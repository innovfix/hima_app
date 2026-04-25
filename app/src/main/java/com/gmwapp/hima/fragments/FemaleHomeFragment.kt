package com.gmwapp.hima.fragments

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BaseApplication.Companion.getInstance
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.CreatorLevelActivity
import com.gmwapp.hima.activities.EarningsActivity
import com.gmwapp.hima.activities.GrantPermissionsActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentFemaleHomeBinding
import com.gmwapp.hima.retrofit.responses.BadgeData
import com.gmwapp.hima.retrofit.responses.UserData
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.AppEventLogger
import com.bumptech.glide.Glide
import com.gmwapp.hima.callbacks.Refreshable
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.BadgeViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.gmwapp.hima.viewmodels.FirstCallUpdateViewModel
import com.gmwapp.hima.viewmodels.WhatsappLinkViewModel
import com.gmwapp.hima.viewmodels.ZohoMailViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.appsflyer.AppsFlyerLib
import com.onesignal.OneSignal
import com.zoho.commons.LauncherModes
import com.zoho.commons.LauncherProperties
import com.zoho.salesiqembed.ZohoSalesIQ
//import com.tencent.mmkv.MMKV
//import com.zegocloud.uikit.ZegoUIKit
//import com.zegocloud.uikit.prebuilt.call.core.CallInvitationServiceImpl
//import com.zegocloud.uikit.prebuilt.call.core.notification.PrebuiltCallNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
//import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random


@AndroidEntryPoint
class FemaleHomeFragment : BaseFragment(), Refreshable {
    private val OVERLAY_REQUEST_CODE: Int = 2
    private var mContext: Context? = null
    private val CALL_PERMISSIONS_REQUEST_CODE = 1
    private val NOTIFICATIONS_ENABLED_REQUEST_CODE = 3
    lateinit var binding: FragmentFemaleHomeBinding

    private val badgeViewModel: BadgeViewModel by viewModels()

    lateinit var language : String
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    private val zohoMailViewModel: ZohoMailViewModel by viewModels()
    private val whatsappLinkViewModel: WhatsappLinkViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
    private val firstCallUpdateViewModel: FirstCallUpdateViewModel by viewModels()

    private lateinit var sharedPreferences: SharedPreferences
    private var isPermissionDenied: Boolean = false
     var whataspplink : String = ""
    private val dateFormat = SimpleDateFormat("HH:mm:ss").apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set to IST time zone
    }
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            initializeCall()
        } else {
            Toast.makeText(
                requireContext(),
                "Notification permission denied. Enable it in Settings.",
                Toast.LENGTH_LONG
            ).show()

            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        }
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private val audioCallEnablePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val userData = getInstance()?.getPrefs()?.getUserData()
            ?: return@registerForActivityResult
        if (granted) {
            promptPostNotificationsIfNeededForCalls()
            pendingAudioStatus = 1
            femaleUsersViewModel.updateCallStatus(userData.id, DConstants.AUDIO, 1)
            binding.sAudio.setOnCheckedChangeListener(null)
            binding.sAudio.isChecked = true
            setupSwitchListeners(userData)
        } else {
            startActivity(Intent(requireContext(), GrantPermissionsActivity::class.java))
        }
    }

    private var startTime: String = ""
    private var endTime: String = ""

    /** While a toggle API call is in-flight, ignore stale GET /users that would snap switches back */
    private var pendingAudioStatus: Int? = null
    private var pendingVideoStatus: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentFemaleHomeBinding.inflate(layoutInflater)
        setupStatusBarInsets()

        sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        isPermissionDenied = sharedPreferences.getBoolean("isTagSet", false)
        initUI()
        askPermissions()
        return binding.root
    }

    private fun setupStatusBarInsets() {
        val basePaddingTop = binding.clHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.clHeader) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(
                view.paddingLeft,
                basePaddingTop + statusBarInset,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.clHeader)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        this.mContext = context
    }

    fun askPermissions() {
        val permissionNeeded =
            arrayOf("android.permission.RECORD_AUDIO", "android.permission.CAMERA")

        if (context?.let {
                ContextCompat.checkSelfPermission(
                    it, "android.permission.CAMERA"
                )
            } != PackageManager.PERMISSION_GRANTED || context?.let {
                ContextCompat.checkSelfPermission(
                    it, "android.permission.RECORD_AUDIO"
                )
            } != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(permissionNeeded, CALL_PERMISSIONS_REQUEST_CODE)
        } else {
           checkOverlayPermission()
//            askNotificationPermission() // Directly proceed to notification permission

        }
    }

    private fun askNotificationsEnabled(){
//        if(mContext!=null) {
//            val invitationConfig = CallInvitationServiceImpl.getInstance()
//                .callInvitationConfig
//            var channelID = MMKV.defaultMMKV().getString("channelID", null)
//            if (channelID == null) {
//                channelID = if (invitationConfig?.notificationConfig != null) {
//                    invitationConfig.notificationConfig.channelID
//                } else {
//                    PrebuiltCallNotificationManager.incoming_call_channel_id
//                }
//            }
//            if (NotificationManagerCompat.from(mContext!!).areNotificationsEnabled()
//                && NotificationManagerCompat.from(mContext!!)
//                    .getNotificationChannel(channelID.toString())?.importance != IMPORTANCE_NONE
//                && NotificationManagerCompat.from(mContext!!)
//                    .getNotificationChannel(CallingService.callingChannelId)?.importance != IMPORTANCE_NONE
//            ) {
//                initializeCall()
//            }else{
//                try {
//                    val settingsIntent: Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
//                        .putExtra(Settings.EXTRA_APP_PACKAGE, mContext?.packageName)
//                    Toast.makeText(context, getString(R.string.enable_notification), Toast.LENGTH_SHORT).show()
//                    startActivityForResult(settingsIntent, NOTIFICATIONS_ENABLED_REQUEST_CODE)
//                } catch (e: Exception) {
//                    initializeCall()
//                }
//            }
//        }else{
//            initializeCall()
//        }
    }

    /** While audio/video are on, incoming CallStyle needs POST_NOTIFICATIONS on Android 13+. */
    private fun promptPostNotificationsIfNeededForCalls() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!isAdded) return
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            startActivity(Intent(context, GrantPermissionsActivity::class.java))
        } else {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireActivity(), Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                askNotificationsEnabled()
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // Show rationale (but don't loop back)
                val intent = Intent(context, GrantPermissionsActivity::class.java)
                startActivity(intent)
            } else {
                val ud = getInstance()?.getPrefs()?.getUserData()
                val callsOn =
                    (ud?.audio_status ?: 0) == 1 || (ud?.video_status ?: 0) == 1
                if (callsOn) {
                    // Incoming calls need CallStyle notifications; do not throttle while calls are enabled.
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    val notifPrefs =
                        requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val lastAsked = notifPrefs.getLong("notif_permission_last_asked", 0L)
                    if (System.currentTimeMillis() - lastAsked >= 24 * 60 * 60 * 1000L) {
                        notifPrefs.edit()
                            .putLong("notif_permission_last_asked", System.currentTimeMillis())
                            .apply()
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        } else {
            askNotificationsEnabled()
        }
    }


    private fun checkOverlayPermission() {

        if (isPermissionDenied) {
            // If permission was denied before, do not ask again
            askNotificationPermission()
            return
        }


        try {
            val result = mContext?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            if (!Settings.canDrawOverlays(mContext) && !result.isLowRamDevice) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + context?.packageName)
                    )
                    startActivityForResult(intent, OVERLAY_REQUEST_CODE)
                } catch (e: Exception) {
                    askNotificationPermission()
                }
            } else {
                askNotificationPermission()
            }
        } catch (e: Exception) {
            askNotificationPermission()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == OVERLAY_REQUEST_CODE) {
            if (Settings.canDrawOverlays(mContext)) {
                askNotificationPermission()
            } else {
                sharedPreferences.edit().putBoolean("isTagSet", true).apply()
                askNotificationPermission()
            }
        } else if(requestCode == NOTIFICATIONS_ENABLED_REQUEST_CODE){
            askNotificationsEnabled()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CALL_PERMISSIONS_REQUEST_CODE -> if (grantResults.isNotEmpty()) {
                val permissionToCamera = grantResults[0] == PackageManager.PERMISSION_GRANTED
                val permissionToRecord = grantResults[1] == PackageManager.PERMISSION_GRANTED
                if (!(permissionToCamera && permissionToRecord)) {
                    val intent = Intent(context, GrantPermissionsActivity::class.java)
                    startActivity(intent)
                } else {
                   checkOverlayPermission()
//                    askNotificationPermission() // Directly proceed to notification permission

                }
            }
        }
    }

    private fun initializeCall() {
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userData = prefs?.getUserData()
        if (userData != null) {
//            registerBroadcastReceiver()
//            setupZegoUIKit(userData.id, userData.name)
          //  addRoomStateChangedListener()
        }
    }

    private fun refreshIplBanner() {
        if (!::binding.isInitialized) return
        val iplEnabled = (BaseApplication.getInstance()?.getPrefs()?.getUserData()?.ipl_rooms_enabled ?: 0) == 1
        binding.cardIplRooms.visibility = if (iplEnabled) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun initUI() {

        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userData = prefs?.getUserData()
        val userLanguage = userData?.language //

//        if (userData != null) {
//            fetchBadgeList(userData.id)
//        }

//        ZohoSalesIQ.deInit {  }
//        BaseApplication.getInstance()?.initZoho()
//
//        val props = LauncherProperties(LauncherModes.FLOATING)
//        props.setYFromBottom(180)
//
//        props.setDirection(LauncherProperties.Horizontal.RIGHT) // Add this line



//        userLanguage?.let { zohoMailViewModel.fetchZohoMail(it) }


        userLanguage?.let { lang ->
            zohoMailViewModel.fetchZohoMail(lang) { email, department, appKey, accessKey ->
                if (!isAdded || view == null) return@fetchZohoMail
                val ud = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                if (ud == null) {
                    Log.w("FemaleHomeFragment", "userData null in Zoho callback")
                    return@fetchZohoMail
                }
                if (!email.isNullOrEmpty()) {

                    // Initialize Zoho *after* email is ready

                    BaseApplication.getInstance()?.initZoho(appKey, accessKey)

                    val langCode = ud.language.take(3)
                    ZohoSalesIQ.registerVisitor("${ud.id}_${ud.language}")

                    ZohoSalesIQ.Visitor.setName("${ud.name}($langCode)")
                    ZohoSalesIQ.Visitor.setContactNumber("${ud.mobile}")
                    ZohoSalesIQ.Chat.setOperatorEmail(email)


                    if (!department.isNullOrEmpty()) {
                        ZohoSalesIQ.Chat.setDepartment(department)
                    }

                    Log.d("ZohoEmail", "$email, Department: $department")

                    val props = LauncherProperties(LauncherModes.FLOATING)
                    props.setYFromBottom(180)
                    props.setDirection(LauncherProperties.Horizontal.RIGHT)

                    ZohoSalesIQ.setLauncherProperties(props)
                    ZohoSalesIQ.showLauncher(true)
                } else {
                    Log.e("ZohoMailError", "Failed to fetch operator email")
                }
            }
        }



        accountViewModel.getSettings()



        language = userData?.language.toString()

        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val isTagSet = sharedPreferences.getBoolean("isOneSignalTagSet", false)


        CoroutineScope(Dispatchers.Main).launch {
            delay(2000) // wait to ensure OneSignal is initialized fully

            // 1. FULL RESET before login
            OneSignal.logout()
            OneSignal.User.pushSubscription.optOut()

            // 2. Fetch user ID
            val userId = getInstance()?.getPrefs()?.getUserData()?.id.toString()

            if (!userId.isNullOrEmpty() && userId != "null") {
                Log.d("OneSignalFix", "Attempting clean login with userId: $userId")

                // 3. Force fresh login
                OneSignal.login(userId)

                // 4. Re-subscribe and assign external ID
                OneSignal.User.pushSubscription.optIn()

                // 5. Prompt notification permission (Android 13+) — once per day only
                val notifPrefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                val lastAsked = notifPrefs.getLong("notif_permission_last_asked", 0L)
                if (System.currentTimeMillis() - lastAsked >= 24 * 60 * 60 * 1000L) {
                    notifPrefs.edit().putLong("notif_permission_last_asked", System.currentTimeMillis()).apply()
                    OneSignal.Notifications.requestPermission(true)
                }

                OneSignal.User.addTag("gender", "female")
                language?.let {
                    OneSignal.User.addTag("language", it)
                    Log.d("OneSignalTag", "Language tag added: $it")
                }

                language?.let {
                    OneSignal.User.addTag("gender_language", "female_$it")
                    Log.d("OneSignalTag", "female_$it")

                }

                // 6. Debug logs to confirm status
                delay(1000)
                Log.d("OneSignalFix", "externalId: ${OneSignal.User.externalId}")
                Log.d("OneSignalFix", "pushToken: ${OneSignal.User.pushSubscription.token}")
                Log.d("OneSignalFix", "optedIn: ${OneSignal.User.pushSubscription.optedIn}")
            } else {
                Log.e("OneSignalFix", "Invalid user ID: $userId")
            }
        }





        language?.let { whatsappLinkViewModel.fetchLink(it) }

        whatsappLinkViewModel.whatsappResponseLiveData.observe(viewLifecycleOwner) { response ->
            response?.let {
                if (it.success && it.data.isNotEmpty()) {
                    whataspplink = it.data[0].link

                } else {
                    Log.e("VideoError", "Failed to load video")
                }
            }
        }

        accountViewModel.settingsLiveData.observe(viewLifecycleOwner, Observer { response ->
            if (response?.success == true) {
                response.data?.let { settingsList ->
                    Log.d("settinglist","$settingsList")
                    if (settingsList.isNotEmpty()) {
                        val settingsData = settingsList[0]
                        settingsData.auto_disable_info?.let { auto_disable_info ->
                            binding.tvDisclaimer.setText(auto_disable_info)
                        }
                        // Update audio call price
                        settingsData.audio_income?.let { audioIncome ->
                            binding.tvAudioRateValue.text = "1 min = ₹$audioIncome"
                        }
                        // Update video call price
                        settingsData.video_income?.let { videoIncome ->
                            binding.tvVideoRateValue.text = "1 min = ₹$videoIncome"
                        }
                    }
                }
            }
        })






//
//        // Send the tag only if it hasn't been set before
//        if (!isTagSet) {
//            OneSignal.User.addTag("gender", "female")
//            language?.let {
//                OneSignal.User.addTag("language", it)
//                Log.d("OneSignalTag", "Language tag added: $it")
//            }
//
//            // Mark the flag so this doesn't happen again
//            sharedPreferences.edit().putBoolean("isOneSignalTagSet", true).apply()
//        } else {
//            Log.d("OneSignalTag", "Tag already set, skipping... ")
//        }

        binding.whatsapp.setOnClickListener {
            if (whataspplink.isNotEmpty() && whataspplink!=null){
                openWhatsAppGroup(whataspplink)
            }
        }




        binding.clCoins.setOnSingleClickListener({
            val intent = Intent(context, EarningsActivity::class.java)
            startActivity(intent)
        })

        binding.cvCreatorLevel.setOnSingleClickListener {
            startActivity(Intent(context, CreatorLevelActivity::class.java))
        }

        // IPL Room Calls banner click — visibility handled by refreshIplBanner()
        binding.cardIplRooms.setOnClickListener {
            startActivity(Intent(requireContext(), com.gmwapp.hima.activities.IplRoomsActivity::class.java))
        }
        refreshIplBanner()

        if (userData != null) {
            // Disable listeners before initial setup to avoid triggering API calls
            binding.sAudio.setOnCheckedChangeListener(null)
            binding.sVideo.setOnCheckedChangeListener(null)
            
            binding.sAudio.isChecked = userData.audio_status == 1
            binding.sVideo.isChecked = userData.video_status == 1
        }

        binding.tvCoins.text = "₹" + userData?.balance.toString()

        // Star Creator banner
        binding.clStarCreatorBanner.visibility =
            if (userData?.star == 1) View.VISIBLE else View.GONE

        Log.d("femaleuserdata", "${userData?.name} , ${userData?.language}")

        femaleUsersViewModel.getReports(userData?.id!!)
        femaleUsersViewModel.getFemaleUsers(userData.id)
        femaleUsersViewModel.getFemaleDiscovery(userData.id)

        femaleUsersViewModel.femaleUsersResponseLiveData.observe(viewLifecycleOwner, Observer { response ->
            if (response != null && response.success) {
                val firstUser = response.data?.firstOrNull()
                firstUser?.coin_per_min_audio?.let { rate ->
                    binding.tvAudioRateValue.text = "1 min = ₹$rate"
                }
                firstUser?.coin_per_min_video?.let { rate ->
                    binding.tvVideoRateValue.text = "1 min = ₹$rate"
                }
            }
        })

        femaleUsersViewModel.femaleDiscoveryResponseLiveData.observe(viewLifecycleOwner, Observer { response ->
            if (response != null && response.success) {
                val creators = response.data.orEmpty()
                binding.tvOnlineCount.text = "${response.online_count ?: 0} online"
                renderDiscoveryCreators(creators.mapNotNull { creator ->
                    val name = creator.name?.trim().orEmpty()
                    val avatar = creator.avatar?.trim()
                    if (name.isBlank()) null else name to avatar
                })
            } else {
                binding.cvFemaleDiscovery.visibility = View.GONE
            }
        })

        femaleUsersViewModel.femaleDiscoveryErrorLiveData.observe(viewLifecycleOwner, Observer {
            binding.cvFemaleDiscovery.visibility = View.GONE
        })

        femaleUsersViewModel.reportResponseLiveData.observe(viewLifecycleOwner, Observer {
            if (it != null && it.success) {

                Log.d("reportResponseLiveData", "$it")
                Log.d("first_call", "${it.data[0].first_call}")


                binding.tvApproxEarnings.text = it.data[0].today_earnings.toString()
                binding.tvTotalCalls.text = it.data[0].today_calls.toString()

                // Call rates image hidden — creator level card shows rate now
                binding.ivCallRates.visibility = View.GONE
                binding.cvCallRates.visibility = View.GONE

                var firstCall = it.data[0].first_call
                if (firstCall==1){
                   var femaleuserid= BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id

                    val bundle = Bundle().apply {
                        putString("user_id", "$femaleuserid") // optional: useful for debugging
                        putString("first_call_status", "Received")
                    }
                    BaseApplication.firebaseAnalytics.logEvent("first_call", bundle)

                    // Log to backend
                    AppEventLogger.logEvent(
                        context = requireContext(),
                        eventName = "first_call",
                        platform = "firebase",
                        userId = femaleuserid,
                        params = AppEventLogger.bundleToMap(bundle)
                    )

                    if (femaleuserid != null) {
                        firstCallUpdateViewModel.updateFirstCallStatus(femaleuserid, 2)
                    }
                }

            } else {
                //  Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
            }
        })

        profileViewModel.getUserLiveData.observe(viewLifecycleOwner, Observer { response ->
            val data = response?.data ?: return@Observer
            if (!isAdded) return@Observer
            val prefsLocal = BaseApplication.getInstance()?.getPrefs() ?: return@Observer
            prefsLocal.setUserData(data)
            binding.tvCoins.text = "₹" + data.balance.toString()
            binding.clStarCreatorBanner.visibility =
                if (data.star == 1) View.VISIBLE else View.GONE
            refreshIplBanner()

            val shouldSetAudio = pendingAudioStatus == null || pendingAudioStatus == data.audio_status
            val shouldSetVideo = pendingVideoStatus == null || pendingVideoStatus == data.video_status
            if (shouldSetAudio || shouldSetVideo) {
                binding.sAudio.setOnCheckedChangeListener(null)
                binding.sVideo.setOnCheckedChangeListener(null)
                if (shouldSetAudio) {
                    binding.sAudio.isChecked = data.audio_status == 1
                    pendingAudioStatus = null
                }
                if (shouldSetVideo) {
                    binding.sVideo.isChecked = data.video_status == 1
                    pendingVideoStatus = null
                }
                setupSwitchListeners(data)
            }
        })

        femaleUsersViewModel.updateCallStatusResponseLiveData.observe(viewLifecycleOwner, Observer { resp ->
            val data = resp?.data
            if (resp != null && resp.success && data != null) {
                prefs.setUserData(data)
                binding.sAudio.setOnCheckedChangeListener(null)
                binding.sVideo.setOnCheckedChangeListener(null)
                binding.sAudio.isChecked = data.audio_status == 1
                binding.sVideo.isChecked = data.video_status == 1
                setupSwitchListeners(data)
            } else {
                resp?.message?.takeIf { msg -> msg.isNotBlank() }?.let { msg ->
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
                binding.sAudio.setOnCheckedChangeListener(null)
                binding.sVideo.setOnCheckedChangeListener(null)
                binding.sAudio.isChecked = prefs.getUserData()?.audio_status == 1
                binding.sVideo.isChecked = prefs.getUserData()?.video_status == 1
                setupSwitchListeners(prefs.getUserData())
            }
            pendingAudioStatus = null
            pendingVideoStatus = null
        })
        femaleUsersViewModel.updateCallStatusErrorLiveData.observe(viewLifecycleOwner, Observer { msg ->
            if (!msg.isNullOrBlank()) {
                showErrorMessage(msg)
                binding.sAudio.setOnCheckedChangeListener(null)
                binding.sVideo.setOnCheckedChangeListener(null)
                binding.sAudio.isChecked = prefs.getUserData()?.audio_status == 1
                binding.sVideo.isChecked = prefs.getUserData()?.video_status == 1
                setupSwitchListeners(prefs.getUserData())
                femaleUsersViewModel.updateCallStatusErrorLiveData.value = null
            }
            pendingAudioStatus = null
            pendingVideoStatus = null
        })
        
        // Observe female talk duration response
        femaleUsersViewModel.femaleTalkDurationResponseLiveData.observe(viewLifecycleOwner, Observer { response ->
            Log.d("FemaleHomeFragment", "📥 Observer received femaleTalkDurationResponseLiveData: $response")
            Log.d("FemaleHomeFragment", "📥 Response details - isNull: ${response == null}, success: ${response?.success}, data: ${response?.data}")
            if (response != null && response.success) {
                Log.d("FemaleHomeFragment", "✅ Response is successful")
                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                Log.d("FemaleHomeFragment", "👤 UserData check - isNull: ${userData == null}, userId: ${userData?.id}")
                if (userData != null) {
                    val totalMinutes = response.data?.total_talk_duration_minutes ?: 0
                    
                    Log.d("FemaleHomeFragment", "⏱️ Total talk duration for user ${userData.id}: $totalMinutes minutes")
                    Log.d("FemaleHomeFragment", "⏱️ Duration check - totalMinutes: $totalMinutes, isGreaterOrEqual2: ${totalMinutes >= 2}")
                    
                    // Check if total duration >= 2 minutes
                    if (totalMinutes >= 2) {
                        Log.d("FemaleHomeFragment", "✅ Total duration ($totalMinutes min) >= 2 minutes, logging event")
                        // Log the event to Firebase, Meta, AppsFlyer, and backend
                        logTwoMinDurationCompleted(userData, totalMinutes)
                        
                        // Mark as logged locally
                        sharedPreferences.edit()
                            .putBoolean("last_two_min_duration_logged_${userData.id}", true)
                            .apply()
                    } else {
                        Log.d("FemaleHomeFragment", "⏭️ Total duration ($totalMinutes min) is less than 2 minutes")
                    }
                } else {
                    Log.e("FemaleHomeFragment", "❌ UserData is null in observer")
                }
            } else {
                Log.e("FemaleHomeFragment", "❌ Response is null or not successful: $response")
            }
        })
        
        femaleUsersViewModel.femaleTalkDurationErrorLiveData.observe(viewLifecycleOwner, Observer { error ->
            if (error != null) {
                Log.e("FemaleHomeFragment", "❌ Error getting talk duration: $error")
            }
        })
        
        // Set up switch listeners once at the end of initUI
        setupSwitchListeners(userData)
    }

    private fun renderDiscoveryCreators(creators: List<Pair<String, String?>>) {
        if (creators.isEmpty()) {
            binding.cvFemaleDiscovery.visibility = View.GONE
            return
        }

        binding.cvFemaleDiscovery.visibility = View.VISIBLE
        binding.llDiscoveryCreators.removeAllViews()
        val displayCreators = creators.take(12)
        val sortedMinutes = List(displayCreators.size) { Random.nextInt(1, 10) }.sorted()

        displayCreators.forEachIndexed { index, (name, avatar) ->
            val itemView = layoutInflater.inflate(
                R.layout.item_female_discovery_creator,
                binding.llDiscoveryCreators,
                false
            )
            val ivAvatar = itemView.findViewById<ImageView>(R.id.iv_creator_avatar)
            val tvName = itemView.findViewById<TextView>(R.id.tv_creator_name)
            val tvTime = itemView.findViewById<TextView>(R.id.tv_joined_time)

            tvName.text = name
            tvTime.text = "${sortedMinutes[index]} min ago"

            if (avatar.isNullOrBlank()) {
                ivAvatar.setBackgroundResource(R.drawable.circle_bg_grey)
                ivAvatar.setImageResource(R.drawable.ic_user_add)
                ivAvatar.setColorFilter(ContextCompat.getColor(requireContext(), R.color.colorAccent))
                ivAvatar.scaleType = ImageView.ScaleType.CENTER_INSIDE
                ivAvatar.setPadding(14, 14, 14, 14)
            } else {
                ivAvatar.setBackgroundResource(0)
                ivAvatar.clearColorFilter()
                ivAvatar.setPadding(0, 0, 0, 0)
                ivAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
                Glide.with(this)
                    .load(avatar)
                    .into(ivAvatar)
            }

            binding.llDiscoveryCreators.addView(itemView)
        }
    }

    private fun setupSwitchListeners(userData: UserData?) {
        if (userData == null) return

        binding.sAudio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasRecordAudioPermission()) {
                binding.sAudio.setOnCheckedChangeListener(null)
                binding.sAudio.isChecked = false
                setupSwitchListeners(userData)
                audioCallEnablePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return@setOnCheckedChangeListener
            }
            pendingAudioStatus = if (isChecked) 1 else 0
            if (isChecked) promptPostNotificationsIfNeededForCalls()
            femaleUsersViewModel.updateCallStatus(
                userData.id,
                DConstants.AUDIO,
                if (isChecked) 1 else 0
            )
        }

        binding.sVideo.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && (!hasCameraPermission() || !hasRecordAudioPermission())) {
                binding.sVideo.setOnCheckedChangeListener(null)
                binding.sVideo.isChecked = false
                setupSwitchListeners(userData)
                startActivity(Intent(requireContext(), GrantPermissionsActivity::class.java))
                return@setOnCheckedChangeListener
            }
            pendingVideoStatus = if (isChecked) 1 else 0
            if (isChecked) promptPostNotificationsIfNeededForCalls()
            femaleUsersViewModel.updateCallStatus(
                userData.id,
                DConstants.VIDEO,
                if (isChecked) 1 else 0
            )
        }
    }

    fun updateEarnings(){
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
            profileViewModel.getUsers(it)
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userData = prefs?.getUserData()

        // Don't set switches here - let updateEarnings() fetch fresh data and set them
        // This prevents race condition between cached data and API data

        if (userData != null && userData.id != null) {
            // Check and log voice_verified event if status is 2
            checkAndLogVoiceVerified(userData)
            
            // Check and log two_min_duration_completed event
            checkAndLogTwoMinDuration(userData)
            
            femaleUsersViewModel.getReports(userData.id)
            femaleUsersViewModel.getFemaleDiscovery(userData.id)
            updateEarnings()
        } else {
            Log.e("FemaleHomeFragment", "UserData is null, skipping getReports()")
        }

//        femaleUsersViewModel.getReports(userData?.id!!)
//        updateEarnings()
    }

    private fun checkAndLogVoiceVerified(userData: UserData) {
        // Check if user is verified (status == 2)
        if (userData.status == 2) {
            // Check if we've already logged this event for this user (to prevent duplicate API calls)
            val lastLoggedStatus = sharedPreferences.getInt("last_voice_verified_status_${userData.id}", 0)
            
            if (lastLoggedStatus != 2) {
                // Prepare event parameters
                val userId = userData.id

                // 1. Firebase Analytics - voice_verified
                val firebaseBundle = Bundle().apply {
                    putString("user_id", "$userId")
                    putString("gender", userData.gender ?: "")
                    putString("status", "${userData.status}")
                }
                BaseApplication.firebaseAnalytics.logEvent("voice_verified", firebaseBundle)
                
                // 2. Meta/Facebook Analytics - voice_verified
                val metaParams = Bundle().apply {
                    putString("user_id", "$userId")
                    putString("gender", userData.gender ?: "")
                    putString("status", "${userData.status}")
                }
                AppEventsLogger.newLogger(requireContext()).logEvent("voice_verified", metaParams)
                
                // 3. AppsFlyer - voice_verified
                val appsFlyerEvent = HashMap<String, Any>().apply {
                    put("user_id", "$userId")
                    put("gender", userData.gender ?: "")
                    put("status", "${userData.status}")
                }
                AppsFlyerLib.getInstance().logEvent(
                    requireContext(),
                    "voice_verified",
                    appsFlyerEvent
                )
                
                // 4. Log to backend (only Firebase events)
                AppEventLogger.logEvent(
                    context = requireContext(),
                    eventName = "voice_verified",
                    platform = "firebase",
                    userId = userId,
                    params = AppEventLogger.bundleToMap(firebaseBundle)
                )
                
                // Mark as logged for this user (backend will also check, but this prevents unnecessary API calls)
                sharedPreferences.edit()
                    .putInt("last_voice_verified_status_${userData.id}", 2)
                    .apply()
                
                Log.d("FemaleHomeFragment", "✅ voice_verified event logged to Firebase, Meta, AppsFlyer, and backend for user $userId")
            }
        }
    }

    private fun checkAndLogTwoMinDuration(userData: UserData) {
        Log.d("FemaleHomeFragment", "🔍 checkAndLogTwoMinDuration called for user ${userData.id}")
        Log.d("FemaleHomeFragment", "🔍 Function entry - userData.id: ${userData.id}, created_at: ${userData.created_at}")
        
        // Check if account was created after 8 Jan 2026
        val cutoffDate = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 8, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        Log.d("FemaleHomeFragment", "📅 Cutoff date: ${cutoffDate.time}")
        Log.d("FemaleHomeFragment", "📅 User created_at: ${userData.created_at}")
        
        val userCreatedAt = try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val parsedDate = userData.created_at?.let { dateFormat.parse(it) }
            if (parsedDate == null) {
                Log.e("FemaleHomeFragment", "❌ created_at is null, returning early")
                return
            }
            Log.d("FemaleHomeFragment", "✅ Successfully parsed created_at: $parsedDate")
            parsedDate
        } catch (e: Exception) {
            Log.e("FemaleHomeFragment", "❌ Error parsing created_at: ${e.message}", e)
            return
        }
        
        val userCreatedCalendar = Calendar.getInstance().apply {
            time = userCreatedAt
        }
        
        Log.d("FemaleHomeFragment", "📅 Parsed user created date: ${userCreatedCalendar.time}")
        Log.d("FemaleHomeFragment", "📅 Is before cutoff? ${userCreatedCalendar.before(cutoffDate)}")
        
        // Only proceed if account was created after 8 Jan 2026
        if (userCreatedCalendar.before(cutoffDate)) {
            Log.d("FemaleHomeFragment", "⏭️ Account created before cutoff date (${userCreatedCalendar.time}), skipping two_min_duration check")
            return
        }
        
        // Check if we've already logged this event locally
        val lastLoggedTwoMin = sharedPreferences.getBoolean("last_two_min_duration_logged_${userData.id}", false)
        Log.d("FemaleHomeFragment", "🔐 Already logged locally? $lastLoggedTwoMin")
        if (lastLoggedTwoMin) {
            Log.d("FemaleHomeFragment", "⏭️ two_min_duration_completed already logged locally for user ${userData.id}")
            return
        }
        
        // Call API via ViewModel to get total talk duration
        Log.d("FemaleHomeFragment", "✅ Calling getFemaleTalkDuration API for user ${userData.id}")
        Log.d("FemaleHomeFragment", "✅ API call initiated - ViewModel: ${femaleUsersViewModel.javaClass.simpleName}")
        femaleUsersViewModel.getFemaleTalkDuration(userData.id)
        Log.d("FemaleHomeFragment", "✅ API call completed - waiting for response")
    }

    private fun logTwoMinDurationCompleted(userData: UserData, totalMinutes: Int) {
        val userId = userData.id
        
        // 1. Firebase Analytics - two_min_duration_completed
        val firebaseBundle = Bundle().apply {
            putString("user_id", "$userId")
            putInt("total_talk_duration_minutes", totalMinutes)
            putString("gender", userData.gender ?: "")
        }
        BaseApplication.firebaseAnalytics.logEvent("two_min_duration_completed", firebaseBundle)
        
        // 2. Meta/Facebook Analytics - two_min_duration_completed
        val metaParams = Bundle().apply {
            putString("user_id", "$userId")
            putInt("total_talk_duration_minutes", totalMinutes)
            putString("gender", userData.gender ?: "")
        }
        AppEventsLogger.newLogger(requireContext()).logEvent("two_min_duration_completed", metaParams)
        
        // 3. AppsFlyer - two_min_duration_completed
        val appsFlyerEvent = HashMap<String, Any>().apply {
            put("user_id", "$userId")
            put("total_talk_duration_minutes", totalMinutes)
            put("gender", userData.gender ?: "")
        }
        AppsFlyerLib.getInstance().logEvent(
            requireContext(),
            "two_min_duration_completed",
            appsFlyerEvent
        )
        
        // 4. Log to backend (only Firebase events)
        AppEventLogger.logEvent(
            context = requireContext(),
            eventName = "two_min_duration_completed",
            platform = "firebase",
            userId = userId,
            params = AppEventLogger.bundleToMap(firebaseBundle)
        )
        
        Log.d("FemaleHomeFragment", "✅ two_min_duration_completed event logged for user $userId ($totalMinutes minutes)")
    }


    private fun openWhatsAppGroup(groupLink: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(groupLink)
            intent.setPackage("com.whatsapp") // Ensures only WhatsApp handles the intent
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
//            Toast.makeText(this, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getWhatsAppGroupLink(): String {
        val userlanguage = language
        language?.let { whatsappLinkViewModel.fetchLink(it) }

        return when (userlanguage) {
            "Tamil" -> "https://whatsapp.com/channel/0029Vazps3mFsn0p4KSOYF0f"
            "Hindi" -> "https://whatsapp.com/channel/0029Vazay5MHVvTZuoDKOv1h"
            "Punjabi" -> "https://whatsapp.com/channel/0029Vb3h23eLCoX5GRLz0y2B"
            "Telugu" -> "https://whatsapp.com/channel/0029Vb3CXKIFSAt2vcFGUC09"
            "Malayalam" -> "https://whatsapp.com/channel/0029Vb7tuimFnSzCEAPBgc2U"
            "Kannada" -> "https://whatsapp.com/channel/0029VauVGRCFi8xeS3Klvl1m"
            else -> "https://whatsapp.com/channel/0029Vazps3mFsn0p4KSOYF0f"
        }




    }
//
//    private fun addRoomStateChangedListener() {
//
//        ZegoUIKit.addRoomStateChangedListener { room, reason, _, _ ->
//            Log.d("roomStateCheck","reason : $reason, room : $room")
//
//            when (reason) {
//                ZegoRoomStateChangedReason.LOGINED -> {
//                    if (CallInvitationServiceImpl.getInstance().callInvitationData.type == 1) {
//                        activateWakeLock()
//                    }
//                    mContext?.startService(Intent(mContext, CallingService::class.java))
//                    mContext?.let {
//                        NotificationManagerCompat.from(it)
//                            .cancel(PrebuiltCallNotificationManager.incoming_call_notification_id)
//                    }
//                    CallInvitationServiceImpl.getInstance().dismissCallNotification()
//                    lastActiveTime = System.currentTimeMillis()
//                    roomID = room
//                    Log.d("roomidCheck","Login $room")
//
//                    startTime = dateFormat.format(Date()) // Set call start time in IST
//                    femaleUsersViewModel.femaleCallAttend(receivedId,
//                        callId,
//                        startTime,
//                        object : NetworkCallback<FemaleCallAttendResponse> {
//                            override fun onResponse(
//                                call: Call<FemaleCallAttendResponse>,
//                                response: Response<FemaleCallAttendResponse>
//                            ) {
//                                balanceTime = response.body()?.data?.remaining_time
//                            }
//
//                            override fun onFailure(
//                                call: Call<FemaleCallAttendResponse>, t: Throwable
//                            ) {
//                            }
//
//                            override fun onNoNetwork() {
//                            }
//                        })
//
//                }
//
//                ZegoRoomStateChangedReason.LOGOUT -> {
//                    releaseWakeLock()
//                    lifecycleScope.launch {
//                        mContext?.stopService(Intent(mContext, CallingService::class.java))
//
//                        lastActiveTime = 0
//                        delay(500)
//                        Log.d("roomidCheck","Logout $roomID")
//
//                        if (roomID != null) {
//                            roomID = null
//                            endTime = dateFormat.format(Date()) // Set call end time in IST
//
//                            val constraints =
//                                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
//                                    .build()
//                            Log.d("RoomStateChangedCheck", "Login room: $roomID")
//                            Log.d("RoomStateChangedCheck", "Start time: $startTime")
//                            Log.d("RoomStateChangedCheck", "EndTime: $endTime")
//                            Log.d("RoomStateChangedCheck", "callId: $callId")
//                            Log.d("RoomStateChangedCheck", "USER_ID: $receivedId")
//                            val data: Data = Data.Builder().putInt(DConstants.USER_ID, receivedId)
//                                .putInt(DConstants.CALL_ID, callId)
//                                .putString(DConstants.STARTED_TIME, startTime).putBoolean(
//                                    DConstants.IS_INDIVIDUAL,
//                                    BaseApplication.getInstance()
//                                        ?.isReceiverDetailsAvailable() == true
//                                ).putString(DConstants.ENDED_TIME, endTime).build()
//                            val oneTimeWorkRequest = OneTimeWorkRequest.Builder(
//                                CallUpdateWorker::class.java
//                            ).setInputData(data).setConstraints(constraints).build()
//                            mContext?.let {
//                                WorkManager.getInstance(it).enqueue(oneTimeWorkRequest)
//                            }
//                            val prefs = BaseApplication.getInstance()?.getPrefs()
//                            val userData = prefs?.getUserData()
//                            if (userData != null) {
//                                setupZegoUIKit(userData.id, userData.name)
//                            }
//                        }
//                    }
//                }
//
//                else -> {
//                }
//            }
//        }
//    }

    fun fetchBadgeList(id: Int) {
        badgeViewModel.getBadgesInformation(id)
        badgeViewModel.badgeLiveData.observe(viewLifecycleOwner) { response ->
            if (response != null && response.success) {
                response.badges?.let { badgeList ->
                    addRowsToTable(badgeList)
                    binding.tvTip.text = response.tips
                    binding.tvMyavgTime.text = "${response.average_duration} mins"
                    binding.tvMybadge.text = "${response.matched_badge?.badge}"
                    binding.tvAudioRate.text = "Audio: ₹${response.matched_badge?.audio}/mins"
                    binding.tvVideoRate.text = "Video: ₹${response.matched_badge?.video}/mins"

                }
            }
        }
    }

    /**
     * Called when the user re-taps the Home tab in bottom nav.
     * Re-fetches female reports / users / discovery so the screen shows current data.
     */
    override fun refresh() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        femaleUsersViewModel.getReports(userId)
        femaleUsersViewModel.getFemaleUsers(userId)
        femaleUsersViewModel.getFemaleDiscovery(userId)
        fetchBadgeList(userId)
    }


    private fun addRowsToTable(badgeList: List<BadgeData>) {
        // Remove old rows except header
        binding.tblAvg.removeViews(1, binding.tblAvg.childCount - 1)

        for (badge in badgeList) {
            val row = TableRow(requireContext()).apply {
                gravity = Gravity.CENTER
            }

            val badgeTv = createCell(badge.badge)
            val avgTimeTv = createCell(badge.average_time)
            val audioTv = createCell("₹${badge.audio}")
            val videoTv = createCell("₹${badge.video}")

            row.addView(badgeTv)
            row.addView(avgTimeTv)
            row.addView(audioTv)
            row.addView(videoTv)

            binding.tblAvg.addView(row)
        }
    }

    private fun createCell(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(requireContext() , R.color.grey_medium))
            setTypeface(typeface, Typeface.BOLD)
            textSize = 15f
            setPadding(0, 8, 8, 8)
        }
    }
}