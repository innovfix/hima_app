package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.ChatListActivity
import com.gmwapp.hima.agora.female.FemaleCallAcceptActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivitySplashScreenBinding
import com.gmwapp.hima.retrofit.responses.UserData
import com.gmwapp.hima.viewmodels.IndividualAppUpdateViewModel
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.zoho.salesiqembed.ZohoSalesIQ
//import com.zego.ve.Log
import dagger.hilt.android.AndroidEntryPoint
import org.greenrobot.eventbus.EventBus

@AndroidEntryPoint
class SplashScreenActivity : BaseActivity() {
    lateinit var binding: ActivitySplashScreenBinding
    val profileViewModel: ProfileViewModel by viewModels()
    val individualAppUpdateViewModel: IndividualAppUpdateViewModel by viewModels()
    val viewModel: LoginViewModel by viewModels()
    var currentVersion = ""
    
    // Track splash screen start time for minimum display duration
    private var splashStartTime: Long = 0
    private val SPLASH_DISPLAY_DURATION = 3000L // 3 seconds


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        // Record start time
        splashStartTime = System.currentTimeMillis()

        startSplashAnimations()
        initUI()
    }

    private fun startSplashAnimations() {
        // Ensure logo is visible using Glide for better image loading
        try {
            Glide.with(this)
                .load(R.drawable.logo)
                .into(binding.imageViewLogo)
            binding.imageViewLogo.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("SplashScreen", "Error loading logo: ${e.message}")
            // Fallback to direct resource loading
            try {
                binding.imageViewLogo.setImageResource(R.drawable.logo)
            } catch (ex: Exception) {
                Log.e("SplashScreen", "Fallback also failed: ${ex.message}")
            }
        }

        // Animate logo container with scale and fade
        val logoScaleX = ObjectAnimator.ofFloat(binding.logoContainer, "scaleX", 0f, 1f)
        val logoScaleY = ObjectAnimator.ofFloat(binding.logoContainer, "scaleY", 0f, 1f)
        val logoAlpha = ObjectAnimator.ofFloat(binding.logoContainer, "alpha", 0f, 1f)
        
        val logoAnimSet = AnimatorSet().apply {
            playTogether(logoScaleX, logoScaleY, logoAlpha)
            duration = 800
            interpolator = DecelerateInterpolator()
        }

        // Animate pulse ring
        val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.splash_pulse_animation)
        binding.pulseRing.startAnimation(pulseAnimation)

        // Animate app name text
        val appNameAlpha = ObjectAnimator.ofFloat(binding.appNameText, "alpha", 0f, 1f)
        val appNameTranslateY = ObjectAnimator.ofFloat(binding.appNameText, "translationY", 30f, 0f)
        
        val appNameAnimSet = AnimatorSet().apply {
            playTogether(appNameAlpha, appNameTranslateY)
            duration = 1000
            startDelay = 300
            interpolator = DecelerateInterpolator()
        }

        // Animate tagline text
        val taglineAlpha = ObjectAnimator.ofFloat(binding.taglineText, "alpha", 0f, 1f)
        val taglineTranslateY = ObjectAnimator.ofFloat(binding.taglineText, "translationY", 30f, 0f)
        
        val taglineAnimSet = AnimatorSet().apply {
            playTogether(taglineAlpha, taglineTranslateY)
            duration = 1000
            startDelay = 500
            interpolator = DecelerateInterpolator()
        }

        // Animate loading container
        val loadingAlpha = ObjectAnimator.ofFloat(binding.loadingContainer, "alpha", 0f, 1f)
        val loadingTranslateY = ObjectAnimator.ofFloat(binding.loadingContainer, "translationY", 50f, 0f)
        
        val loadingAnimSet = AnimatorSet().apply {
            playTogether(loadingAlpha, loadingTranslateY)
            duration = 1000
            startDelay = 600
            interpolator = DecelerateInterpolator()
        }

        // Animate background circles
        animateBackgroundCircles()

        // Animate loading dots
        animateLoadingDots()

        // Start all animations
        logoAnimSet.start()
        appNameAnimSet.start()
        taglineAnimSet.start()
        loadingAnimSet.start()
    }

    private fun animateBackgroundCircles() {
        // Circle 1 - Slow rotation and scale
        val circle1Rotate = ObjectAnimator.ofFloat(binding.circle1, "rotation", 0f, 360f).apply {
            duration = 20000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        val circle1ScaleX = ObjectAnimator.ofFloat(binding.circle1, "scaleX", 1f, 1.2f, 1f).apply {
            duration = 4000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        
        val circle1ScaleY = ObjectAnimator.ofFloat(binding.circle1, "scaleY", 1f, 1.2f, 1f).apply {
            duration = 4000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }

        // Circle 2 - Slow rotation opposite direction
        val circle2Rotate = ObjectAnimator.ofFloat(binding.circle2, "rotation", 360f, 0f).apply {
            duration = 15000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        val circle2ScaleX = ObjectAnimator.ofFloat(binding.circle2, "scaleX", 1f, 1.3f, 1f).apply {
            duration = 5000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        
        val circle2ScaleY = ObjectAnimator.ofFloat(binding.circle2, "scaleY", 1f, 1.3f, 1f).apply {
            duration = 5000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }

        circle1Rotate.start()
        circle1ScaleX.start()
        circle1ScaleY.start()
        circle2Rotate.start()
        circle2ScaleX.start()
        circle2ScaleY.start()
    }

    private fun animateLoadingDots() {
        // Create bouncing animation for each dot with different delays
        val dots = listOf(binding.loadingDot1, binding.loadingDot2, binding.loadingDot3)
        
        dots.forEachIndexed { index, dot ->
            val bounceAnimation = ObjectAnimator.ofFloat(dot, "translationY", 0f, -14f, 0f).apply {
                duration = 600
                repeatCount = ObjectAnimator.INFINITE
                startDelay = (index * 150).toLong()
                interpolator = AccelerateDecelerateInterpolator()
            }
            bounceAnimation.start()
        }
    }

    private fun initUI() {
        // Check for network connectivity
        if (!isNetworkAvailable()) {
            showAppToast("No internet connection", Toast.LENGTH_SHORT)
            return
        }

        // Capture install referrer on first launch
        BaseApplication.getInstance()?.getInstallReferrer()

        ZohoSalesIQ.showLauncher(false)


        // viewModel.appUpdate()

        var intent: Intent? = null
        val prefs = BaseApplication.getInstance()?.getPrefs()
        var userData = prefs?.getUserData()


        if (userData == null) {
            viewModel.firstInstall()
        }

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            currentVersion = pInfo.versionCode.toString()
            Log.d("CurrentVersion","$currentVersion")
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
      //  userData?.let { individualAppUpdateViewModel.checkUserAppVersion(it.id,currentVersion) }
        val userId = userData?.id
        if (userId != null) {
            individualAppUpdateViewModel.checkUserAppVersion(userId, currentVersion)
        }else{
            GotoActivity(userData)
        }


        Log.d("AppUpdateData","user id ${userData?.id}, $currentVersion")

        val isIncomingCall = BaseApplication.getInstance()?.isIncomingCall() ?: false
        val senderId = BaseApplication.getInstance()?.getSenderIdForSplashActivity() ?: -1
        val callType = BaseApplication.getInstance()?.getCallTypeForSplashActivity()
        val channelName = BaseApplication.getInstance()?.getChannelName() ?: "default_channel"
        val callId = BaseApplication.getInstance()?.getCallIdForSplashActivity()

        if (isIncomingCall) {
            Log.d("SplashActivity", "Incoming call detected! Redirecting to Call Accept Screen.")

            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, FemaleCallAcceptActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("CALL_TYPE", callType)
                    putExtra("SENDER_ID", senderId)
                    putExtra("CHANNEL_NAME", channelName)
                    putExtra("CALL_ID", callId)
                    Log.d("CALL_TYPE_Data", "$callType")

                }
                startActivity(intent)
                finish()
            }, 2000)  // Delay ONLY if there's an incoming call
        } else {
            Log.d("SplashActivity", "No incoming call. Redirecting to MainActivity.")
        }







        profileViewModel.getUserLiveData.observe(this, Observer {
            it?.data?.let { fresh -> prefs?.setUserData(fresh) }
            userData = it?.data ?: prefs?.getUserData()

            intent = when {
                userData?.status == 2 -> {
                    Intent(this, MainActivity::class.java).apply {
                        putExtra(
                            DConstants.AVATAR_ID,
                            getIntent().getIntExtra(DConstants.AVATAR_ID, 0)
                        )
                        putExtra(DConstants.LANGUAGE, userData?.language)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                }

                userData?.status == 1 -> {
                    Intent(this, AlmostDoneActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                }

                else -> {
                    Intent(this, VoiceIdentificationActivity::class.java).apply {
                        putExtra(DConstants.LANGUAGE, userData?.language)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                }
            }
            navigateWithMinimumDelay(intent)
        })



//        viewModel.appUpdateResponseLiveData.observe(this, Observer {
//            if (it != null && it.success) {
//
//                val latestVersion = it.data[0].app_version.toString()
//                val minimum_required_version = it.data[0].minimum_required_version.toString()
//
//                val link = it.data[0].link
//                val description = it.data[0].description
//
//                GotoActivity(userData, latestVersion,minimum_required_version, link, description)
//            }
//        })

        individualAppUpdateViewModel.individualUpdateLiveData.observe(this) { response ->
            val savedUser = prefs?.getUserData()
            if (response != null && response.success) {
                val data = response.data
                val link = data.link
                val description = data.description
                if (data.current_version.toInt() < data.minimum_version.toInt() &&
                    data.update_type == "mandatory") {
                    showUpdateDialog(link, description)
                } else {
                    GotoActivity(savedUser)
                }
            } else {
                Log.w("SplashScreen", "App version check missing or failed; continuing with cached session")
                GotoActivity(savedUser)
            }
        }

        individualAppUpdateViewModel.individualUpdateErrorLiveData.observe(this) { err ->
            Log.w("SplashScreen", "App version check error: $err — continuing with cached session")
            GotoActivity(prefs?.getUserData())
        }

        viewModel.firstInstallResponseLiveData.observe(this) { response ->
            if (response != null) {
                Log.d("FirstInstall", "First install tracked: ${response.message}")
                Log.d("FirstInstall", "First install ip_address: ${response.ip_address}")
                response.ip_address?.takeIf { it.isNotBlank() }?.let { ipAddress ->
                    BaseApplication.getInstance()?.getPrefs()?.setString("saved_address", ipAddress)
                    Log.d("FirstInstall", "Saved address stored from first_install ip_address: $ipAddress")
                }
            }
        }

        viewModel.firstInstallErrorLiveData.observe(this) { error ->
            if (error != null) {
                Log.e("FirstInstall", "Error tracking first install: $error")
            }
        }

    }

    /**
     * Ensures splash screen is displayed for at least 3 seconds before navigating
     */
    private fun navigateWithMinimumDelay(intent: Intent?) {
        intent?.let {
            val elapsedTime = System.currentTimeMillis() - splashStartTime
            val remainingTime = SPLASH_DISPLAY_DURATION - elapsedTime
            
            if (remainingTime > 0) {
                // Wait for remaining time to reach minimum display duration
                Handler(Looper.getMainLooper()).postDelayed({
                    if (BaseApplication.getInstance()?.isChatListActivityVisible() == false) {
                        // currently on ChatListActivity
                        startActivity(it)
                        Log.d("FromSplash","Hello Splash")
                        finish()
                    }
                }, remainingTime)
            } else {
                // Minimum time already elapsed, navigate immediately
                startActivity(it)
                finish()
            }
        }
    }

    fun GotoActivity(
        userData: UserData?,
    ) {
//            showAppToast("1", Toast.LENGTH_SHORT)
            if (userData == null) {
//                showAppToast("2", Toast.LENGTH_SHORT)

//                intent = Intent(this@SplashScreenActivity, NewLoginActivity::class.java)
                val intent = Intent(this@SplashScreenActivity, NewLoginActivity::class.java)
                navigateWithMinimumDelay(intent)

            } else {
                if (userData?.gender == DConstants.MALE) {
//                    showAppToast("3", Toast.LENGTH_SHORT)
                  //  intent = Intent(this@SplashScreenActivity, MainActivity::class.java)
                    val intent = Intent(this@SplashScreenActivity, MainActivity::class.java)
                    navigateWithMinimumDelay(intent)
                } else {
//                    showAppToast("4", Toast.LENGTH_SHORT)
                    BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
                        profileViewModel.getUsers(it)
                    }

                }

        }

    }

    // Function to check network availability
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun showUpdateDialog(link: String, description: String) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_dialog_update, null)
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.setCancelable(false);

        val btnUpdate = view.findViewById<View>(R.id.btnUpdate)
        val dialogMessage = view.findViewById<TextView>(R.id.dialog_message)
        dialogMessage.text = description
        btnUpdate.setOnClickListener(View.OnClickListener {
            val url = link;
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            startActivity(i)
        })


        // Customize your bottom dialog here
        // For example, you can set text, buttons, etc.

        bottomSheetDialog.show()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().unregister(this) // Prevent unwanted registration
    }






}

