package com.gmwapp.hima.agora

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.Observer

import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.agora.male.MaleAudioCallingActivity
import com.gmwapp.hima.agora.male.MaleVideoCallingActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityAgoraRandomCallBinding
import com.gmwapp.hima.viewmodels.AgoraViewModel
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AgoraRandomCallActivity : AppCompatActivity() {

    @javax.inject.Inject
    lateinit var apiManager: com.gmwapp.hima.retrofit.ApiManager

    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    private val agoraViewModel: AgoraViewModel by viewModels()
    private lateinit var binding : ActivityAgoraRandomCallBinding
    var callType: String? = null
    var randomFilter: String? = null
    var receiverId: Int = -1
    var userId: Int? = null
    private var callId = 0
    private var callAttempts = 0
    private val maxAttempts = 4
    private val triedUserIds = mutableSetOf<Int>()
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var progressStatus = 0
    private var isRunning = true  // Keeps the loop running

    private var isCallAccepted = false
    private var currentChannelName: String? = null
    private var declinedChannelName = "CallDeclined"

    private var isWaitingForAcceptance = false
    private var prefetchedAgoraToken: String? = null
    private var prefetchedAgoraAppId: String? = null


    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAgoraRandomCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        // Force white system-icon tint so clock/battery/signal stay visible on the dark gradient.
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        userData?.id?.let { userId = userData?.id}

        callType = intent.getStringExtra(DConstants.CALL_TYPE)
        randomFilter = intent.getStringExtra("RANDOM_FILTER")?.takeIf {
            it == "all" || it == "new" || it == "star"
        } ?: "all"

        // Clear any stale status before starting
        FcmUtils.clearUserBusyStatus()

        getRandomUser()

        initUI()
        observeCallAcceptance()
        observeRandomUser()
        observeUserBusyStatus()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("FemaleCallAcceptActivity", "onBackPressed called via Dispatcher")
                if (userId != null && receiverId != -1 && callType != null) {
                    sendCallNotification(userId!!, receiverId,callType!!,declinedChannelName,"callDeclined")
                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

                    val intent = Intent(this@AgoraRandomCallActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                } else {


                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

                    val intent = Intent(this@AgoraRandomCallActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()

                    Log.e("MaleCallConnectingActivity", "Missing required data: userId=$userId, receiverId=$receiverId, callType=$callType")
                }

            }
        })


    }

    private val radarAnimators = mutableListOf<Animator>()

    fun initUI() {
        progressBar = findViewById(R.id.progressBar)
        startProgressLoop()

        // Eyebrow label
        binding.tvTitle.setText(if (callType == "audio") "AUDIO MATCH" else "VIDEO MATCH")
        // Status line uses the existing tl_wait_title id
        findViewById<TextView>(R.id.tl_wait_title)?.text = "Searching for the right match"

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        Glide.with(this)
            .load(userData?.image)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivCallerProfile)

        startImageSequence()
        startRadarAnimations()

        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.tvCancel.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun startRadarAnimations() {
        val stage = findViewById<View>(R.id.radar_stage) ?: return

        // Pulse rings
        val ringIds = intArrayOf(R.id.ring1, R.id.ring2, R.id.ring3, R.id.ring4)
        ringIds.forEachIndexed { i, id ->
            val v = findViewById<View>(id) ?: return@forEachIndexed
            v.scaleX = 0.6f
            v.scaleY = 0.6f
            v.alpha = 0.9f
            val anim = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(v, View.SCALE_X, 0.6f, 1.4f),
                    ObjectAnimator.ofFloat(v, View.SCALE_Y, 0.6f, 1.4f),
                    ObjectAnimator.ofFloat(v, View.ALPHA, 0.9f, 0f)
                )
                duration = 2400L
                interpolator = DecelerateInterpolator()
                startDelay = (i * 400L)
            }
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isRunning) return
                    v.scaleX = 0.6f
                    v.scaleY = 0.6f
                    v.alpha = 0.9f
                    anim.startDelay = 0L
                    anim.start()
                }
            })
            anim.start()
            radarAnimators.add(anim)
        }

        // Candidates orbit the YOU avatar like planets around the sun:
        // each one at its own radius, starting angle, period and direction.
        stage.doOnLayout { s ->
            val width = s.width.toFloat()
            val height = s.height.toFloat()
            if (width <= 0f || height <= 0f) return@doOnLayout

            // Cap orbit radius so candidates don't escape the stage on small screens.
            val maxRadius = minOf(width, height) / 2f - dp(28f)

            // (viewId, radiusDp, startAngleDeg, periodMs, clockwise)
            val orbits = listOf(
                Orbit(R.id.cand1, 96f, 0f, 12000L, true),
                Orbit(R.id.cand2, 124f, 60f, 14000L, false),
                Orbit(R.id.cand3, 142f, 120f, 10000L, true),
                Orbit(R.id.cand4, 108f, 200f, 13000L, false),
                Orbit(R.id.cand5, 134f, 260f, 11000L, true),
                Orbit(R.id.iv_logo_wrap, 116f, 320f, 9000L, false)
            )

            orbits.forEach { o ->
                val v = findViewById<View>(o.viewId) ?: return@forEach
                val r = minOf(dp(o.radiusDp), maxRadius)
                val direction = if (o.clockwise) 1f else -1f

                val anim = ValueAnimator.ofFloat(0f, 360f).apply {
                    duration = o.periodMs
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener { a ->
                        val sweep = a.animatedValue as Float
                        val rad = Math.toRadians((o.startAngleDeg + direction * sweep).toDouble())
                        v.translationX = (r * kotlin.math.cos(rad)).toFloat()
                        v.translationY = (r * kotlin.math.sin(rad)).toFloat()
                    }
                }
                anim.start()
                radarAnimators.add(anim)
            }
        }
    }

    private data class Orbit(
        val viewId: Int,
        val radiusDp: Float,
        val startAngleDeg: Float,
        val periodMs: Long,
        val clockwise: Boolean
    )

    private fun stopRadarAnimations() {
        radarAnimators.forEach { it.cancel() }
        radarAnimators.clear()
    }

    private fun startProgressLoop() {
        Thread {
            while (isRunning) {
                progressStatus = 0  // Reset progress

                while (progressStatus < 100 && isRunning) {
                    progressStatus += 1  // Increase progress
                    handler.post { progressBar.progress = progressStatus }

                    try {
                        Thread.sleep(200)  // Smooth animation delay
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }

                // Reset to 0 and repeat the loop
                if (isRunning) {
                    handler.post { progressBar.progress = 0 }
                }
            }
        }.start()

    }

    private fun startImageSequence() {
        // List of image resources
        val images = listOf(
            R.drawable.avatar1,
            R.drawable.avatar2,
            R.drawable.avatar3,
            R.drawable.avatar4,
            R.drawable.avatar5,
            R.drawable.avatar6,

            )

        // Handler to post delayed tasks
        val handler = Handler(Looper.getMainLooper())

        // Function to update image sequence
        val updateImageSequence = object : Runnable {
            var currentImageIndex = 0

            override fun run() {
                if (isFinishing || isDestroyed) {
                    return // Exit if the activity is finishing or destroyed
                }

                // Apply circle crop using Glide
                val requestOptions = RequestOptions().circleCrop()

                // Load the image using Glide with circle crop
                Glide.with(this@AgoraRandomCallActivity)
                    .load(images[currentImageIndex])  // Load the current image resource
                    .apply(requestOptions)  // Apply the circle crop transformation
                    .into(binding.ivLogo)  // Set image into the ImageView

                // Move to the next image
                currentImageIndex = (currentImageIndex + 1) % images.size  // Loop back to the first image after the last one

                // Post the next update with a delay of 1 second
                handler.postDelayed(this, 1000)  // 1 second delay
            }
        }

        // Start the image sequence
        handler.post(updateImageSequence)
    }



    private fun getRandomUser() {
        Log.d("callAttemptDebug","$callAttempts")
        if (isWaitingForAcceptance || callAttempts >= maxAttempts) {
            Log.d("isWaitingForAcceptance", "$isWaitingForAcceptance")
            return

        }
        isWaitingForAcceptance = true


        userId?.let { userId ->
            callType?.let { callType ->
                Log.d("getRandomUser", "Attempt: $callAttempts")
                femaleUsersViewModel.getRandomUser(userId, callType, randomFilter)
            }
        }
    }


    private fun observeRandomUser() {
        femaleUsersViewModel.randomUsersResponseLiveData.removeObservers(this)

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        var myname = userData?.name
        var myAvatar = userData?.image
        femaleUsersViewModel.randomUsersResponseLiveData.observe(this, Observer { response ->
            Log.d("RandomUsersResponse", "$response")

            if (response != null && response.success) {
                response.data?.let { data ->
                    if (data.call_id != null && data.call_user_id != null) {
                        callId = data.call_id
                        receiverId = data.call_user_id
                        Log.d("RandomUsersID", "$callId $receiverId")

                        BaseApplication.getInstance()?.saveSenderId(receiverId)

                        Log.d("receiverIds","$receiverId")

                        if (triedUserIds.contains(receiverId)) {
                            Log.d("triedUserIds", "Already tried user $receiverId, waiting before retrying...")
                            isWaitingForAcceptance = false
                            Handler(mainLooper).postDelayed({ retryCall() }, 3000L) // Delay retry by 3 seconds
                            return@Observer
                        }

                        triedUserIds.add(receiverId)

                        Log.d("triedUserList","$triedUserIds")

                        currentChannelName = generateUniqueChannelName(userId!!)
                        prefetchedAgoraToken = null
                        prefetchedAgoraAppId = null
                        prefetchAgoraToken(currentChannelName!!)

                        if (currentChannelName!=null){
                            sendCallNotification(userId!!, receiverId!!, callType!!,currentChannelName!!, "incoming call $callId $myAvatar $myname")
                        }
                        observeNotificationResponse()
                        waitForCallAcceptance()
                    } else {
                        Log.e("RandomCall", "Invalid call data: call_id or call_user_id is null")
                        isWaitingForAcceptance = false

                        retryCall()
                    }
                } ?: run {
                    Log.e("RandomCall", "Response data is null")
                    isWaitingForAcceptance = false

                    retryCall()
                }
            }else{

//                Toast.makeText(this, "${response.message}", Toast.LENGTH_LONG).show()
//                navigateToMainActivity()

                response?.message?.let { message ->
                    if (message.startsWith("Insufficient coins")) {
                        val intent = Intent(this@AgoraRandomCallActivity, WalletActivity::class.java)
                        Toast.makeText(this@AgoraRandomCallActivity, message, Toast.LENGTH_LONG).show()
                        startActivity(intent)
                        finish()
                    } else {
                        Log.d("RandomUsersResponse", "$message")

                        Toast.makeText(this@AgoraRandomCallActivity, message, Toast.LENGTH_LONG).show()
                        navigateToMainActivity()
                    }
                }
            }
        })
    }

    private fun waitForCallAcceptance() {
        val waitTime = when (callAttempts) {
            0 -> 7000L  // First attempt: 7 seconds
            1 -> 14000L // Second attempt: 17 seconds
            2 -> 21000L // Third attempt: 27 seconds
            else -> 28000L // Fourth attempt: 37 seconds
        }

        Log.d("RandomCall", "Waiting for $waitTime ms before checking call status")

        android.os.Handler(mainLooper).postDelayed({
            isWaitingForAcceptance = false

            checkCallStatus()
        }, waitTime)
    }



    private fun checkCallStatus() {
        // If call is accepted, do nothing
        isWaitingForAcceptance = false // Allow next user

        var currentActivity = BaseApplication.getInstance()?.getCurrentActivity()

        if (!isCallAccepted && currentActivity is AgoraRandomCallActivity) {
            declineCall()
            retryCall()
        }



    }

    private fun declineCall() {
        if (userId != null && receiverId != null && callType != null) {
            sendCallNotification(userId!!, receiverId!!, callType!!, declinedChannelName,"callDeclined")
        }
    }

    private fun retryCall() {
        callAttempts++
        if (callAttempts < maxAttempts) {
            Log.d("RandomCall", "Retrying... Attempt $callAttempts")
            Handler(mainLooper).postDelayed({ getRandomUser() }, 3000L) // Add 3 seconds delay before retrying
        } else {
            Log.d("RandomCall", "Max retries reached, stopping calls.")

            // Track this failed cycle on the backend (audio/video, language inferred server-side from user)
            val uid = userId
            val ct = callType
            if (uid != null && uid > 0 && !ct.isNullOrEmpty()) {
                apiManager.trackRandomCallFailure(uid, ct)
            }

            val intent = Intent(this@AgoraRandomCallActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }



    fun sendCallNotification(senderId:Int, receiverId:Int, callType:String,myChannel:String, message:String) {
        fcmNotificationViewModel.sendNotification(
            senderId = senderId,
            receiverId = receiverId,
            callType = callType,
            channelName = myChannel,
            message = message
        )
        observeNotificationResponse()
        Log.d("ChannelNameDebug", "Sending $message to user $receiverId with channel $myChannel")
    }

    fun observeNotificationResponse() {
        fcmNotificationViewModel.notificationResponseLiveData.removeObservers(this) // add this

        fcmNotificationViewModel.notificationResponseLiveData.observe(this) { response ->
            response?.let {
                if (it.success) {
                    Log.d("FCM_Success", "${it.data_sent?.receiverId}")
                } else {
                    Log.e("FCMNotification", "Failed to send notification")
                }
            }
        }
    }

    fun observeCallAcceptance() {

       FcmUtils.callStatus.removeObservers(this) // 👈 REMOVE existing observers first

        FcmUtils.callStatus.observe(this, Observer { callData ->
            if (callData != null) {  // Check if it's not null before destructuring
                val (status, channelName) = callData
                Log.d("CallStatusDebug", "Received status=$status, channel=$channelName, expected=$currentChannelName, isAccepted=$isCallAccepted")

                if (status == "accepted" && !isCallAccepted && channelName == currentChannelName) {
                    isCallAccepted = true
                    isWaitingForAcceptance = false // Clear waiting
                    FcmUtils.clearCallStatus()  // Clear before moving to AudioCallingActivity

                    var currentActivity = BaseApplication.getInstance()?.getCurrentActivity()
                    if (currentActivity !is MainActivity){
                        var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                        if (previousSenderId==receiverId){

                        Log.d("callTypeData","$callType")
                        if (callType=="audio") {
                            val intent = Intent(this, MaleAudioCallingActivity::class.java).apply {
                                putExtra("CHANNEL_NAME", channelName)
                                putExtra("RECEIVER_ID", receiverId)
                                putExtra("CALL_ID", callId)
                                prefetchedAgoraToken?.let { putExtra("AGORA_TOKEN", it) }
                                prefetchedAgoraAppId?.let { putExtra("AGORA_APP_ID", it) }
                                Log.d("RECEIVER_ID","$receiverId")
                            }
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(intent)
                            finish()
                        }else{
                            FcmUtils.clearCallStatus()
                            val intent = Intent(this, MaleVideoCallingActivity::class.java).apply {
                                putExtra("CHANNEL_NAME", channelName)
                                putExtra("RECEIVER_ID", receiverId)
                                putExtra("CALL_ID", callId)
                                prefetchedAgoraToken?.let { putExtra("AGORA_TOKEN", it) }
                                prefetchedAgoraAppId?.let { putExtra("AGORA_APP_ID", it) }
                            }
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(intent)
                            finish()
                        }
                    }}
                } else if (status == "rejected") {
                    FcmUtils.clearCallStatus()
                    isWaitingForAcceptance = false

                    retryCall()
                }
            }
        })
    }

    private fun observeUserBusyStatus() {
        FcmUtils.userBusyStatus.observe(this, Observer { busyData ->
            if (busyData != null) {
                Log.d("observeUserBusyStatus", "Random user busy, retrying with another user...")
                
                // Clear the status
                FcmUtils.clearUserBusyStatus()
                
                // Clear waiting state
                isWaitingForAcceptance = false
                
                // Treat it like a rejection - retry with another user
                retryCall()
            }
        })
    }

    private fun prefetchAgoraToken(channelForToken: String) {
        Log.d("AgoraTiming", "RandomCall prefetchAgoraToken started at ${System.currentTimeMillis()}")
        agoraViewModel.agoraTokenLiveData.removeObservers(this)
        agoraViewModel.agoraTokenLiveData.observe(this) { response ->
            if (response != null && response.success == true && !response.token.isNullOrEmpty()) {
                prefetchedAgoraToken = response.token
                prefetchedAgoraAppId = response.app_id
                Log.d("AgoraTiming", "RandomCall prefetchAgoraToken received at ${System.currentTimeMillis()}")
            }
        }
        agoraViewModel.getAgoraToken(channelForToken, 0, "publisher", 3600)
    }

    private fun navigateToMainActivity() {
        FcmUtils.clearCallStatus()  // Clear any pending call status

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }



    fun generateUniqueChannelName(senderId: Int): String {
        return "${senderId}_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    override fun onDestroy() {
        isRunning = false
        stopRadarAnimations()
        super.onDestroy()
    }

}