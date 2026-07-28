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
import com.gmwapp.hima.mmp.MmpClient
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random
import com.gmwapp.hima.utils.applyHimaRefreshColors


@AndroidEntryPoint
class FemaleHomeFragment : BaseFragment(), Refreshable {

    // ── BUG 15 — native "Earning details" card ────────────────────────────────────
    /**
     * Draws the rates card from the API payload instead of the hardcoded poster PNG.
     *
     * Returns TRUE only when a usable card was actually built. Every other outcome —
     * null payload (today's backend), no sections, sections with no usable rows, or ANY
     * exception while building — returns false so the caller falls back to the legacy
     * image. That is deliberate: this feature must be incapable of making the screen
     * worse than it is today, and it stays completely dormant until the backend starts
     * sending earning_details.
     *
     * The backend supplies pre-formatted strings for both columns, so the app never does
     * rate maths and can never print a number that disagrees with what is actually paid.
     */
    private fun renderEarningDetails(details: com.gmwapp.hima.retrofit.responses.EarningDetails?): Boolean =
        runCatching {
            if (!::binding.isInitialized || !isAdded) return false
            val ctx = context ?: return false
            val sections = details?.sections
                ?.filter { sec -> !sec.rows.isNullOrEmpty() }
                .orEmpty()
            if (sections.isEmpty()) return false

            fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

            val host = binding.llEarningDetails
            host.removeAllViews()
            host.setBackgroundResource(R.drawable.bg_earning_card)
            host.setPadding(dp(14), dp(14), dp(14), dp(14))

            // Heading
            details?.title?.takeIf { it.isNotBlank() }?.let { title ->
                host.addView(TextView(ctx).apply {
                    text = title
                    setTextColor(android.graphics.Color.parseColor("#FFD25A"))
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(10) }
                })
            }

            var built = 0
            sections.forEach { sec ->
                // Section label pill ("Audio call")
                sec.label?.takeIf { it.isNotBlank() }?.let { label ->
                    host.addView(TextView(ctx).apply {
                        text = label
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 11f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setBackgroundResource(R.drawable.bg_earning_pill)
                        setPadding(dp(12), dp(3), dp(12), dp(3))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(6) }
                    })
                }

                // Rows. A row needs at least one populated column to be worth drawing.
                val rows = sec.rows.orEmpty().filter {
                    !it.time.isNullOrBlank() || !it.rate.isNullOrBlank()
                }
                if (rows.isEmpty()) return@forEach

                fun cell(text: String, header: Boolean) = TextView(ctx).apply {
                    this.text = text
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = if (header) 11f else 11.5f
                    gravity = Gravity.CENTER
                    if (header) setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setBackgroundResource(
                        if (header) R.drawable.bg_earning_cell_header else R.drawable.bg_earning_cell
                    )
                    setPadding(dp(6), dp(5), dp(6), dp(5))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                fun rowOf(a: String, b: String, header: Boolean) = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(cell(a, header))
                    addView(cell(b, header))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                host.addView(rowOf(
                    getString(R.string.earning_details_col_time),
                    getString(R.string.earning_details_col_rate),
                    header = true
                ))
                rows.forEach { r ->
                    host.addView(rowOf(r.time.orEmpty(), r.rate.orEmpty(), header = false))
                }
                (host.getChildAt(host.childCount - 1) as? View)?.let {
                    (it.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(10)
                }
                built++
            }

            if (built == 0) return false

            // Optional footer note (e.g. the short-call rule).
            details?.note?.takeIf { it.isNotBlank() }?.let { note ->
                host.addView(TextView(ctx).apply {
                    text = note
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 10.5f
                    setBackgroundResource(R.drawable.bg_earning_note)
                    setPadding(dp(9), dp(7), dp(9), dp(7))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            }
            true
        }.getOrElse {
            android.util.Log.w("FemaleHome", "earning_details render failed, falling back to poster image", it)
            false
        }

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

    // C-02: shimmer placeholders for the "Creators Joining Now" row while the
    // discovery feed loads, so a cold home isn't a blank strip. Cleared the
    // moment renderDiscoveryCreators() populates real (or empty-state) content.
    private var discoverySkeletonAnim: android.animation.Animator? = null
    private var discoverySkeletonShowing = false

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

    // B151 — rapid toggling fired one API request per tap. Network reordering
    // then made the server's last-arriving response (not the user's last tap)
    // win, leaving UI and server out of sync. Debounce so only the final
    // intent in a burst hits the network; each fresh tap cancels the prior
    // pending request.
    private var audioToggleDebounceJob: Job? = null
    private var videoToggleDebounceJob: Job? = null
    private val TOGGLE_DEBOUNCE_MS = 400L

    /** Pull-to-refresh: wait for profile (/users) + reports before hiding the indicator */
    private var femaleHomeSwipeProfilePending = false
    private var femaleHomeSwipeReportsPending = false
    private val femaleHomeSwipeRefreshTimeout = Runnable {
        femaleHomeSwipeProfilePending = false
        femaleHomeSwipeReportsPending = false
        if (::binding.isInitialized && isAdded) {
            binding.swipeRefreshFemaleHome.isRefreshing = false
        }
    }

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
        // RECORD_AUDIO + CAMERA are MANDATORY for calls. READ_PHONE_STATE is
        // OPTIONAL — it only lets CallPhoneStateHelper detect an incoming SIM call
        // mid-Hima-call and show the "on hold" banner. We request whichever are
        // missing, but the mandatory gate (and onRequestPermissionsResult) consider
        // ONLY mic+camera, so denying phone-state can never block the call flow.
        val cameraGranted = context?.let {
            ContextCompat.checkSelfPermission(it, "android.permission.CAMERA")
        } == PackageManager.PERMISSION_GRANTED
        val recordGranted = context?.let {
            ContextCompat.checkSelfPermission(it, "android.permission.RECORD_AUDIO")
        } == PackageManager.PERMISSION_GRANTED
        val phoneStateGranted = context?.let {
            ContextCompat.checkSelfPermission(it, "android.permission.READ_PHONE_STATE")
        } == PackageManager.PERMISSION_GRANTED

        // Only request what's missing (avoids relying on a fixed grantResults
        // index, which the OS may compact to the actually-requested set).
        val missing = mutableListOf<String>()
        if (!recordGranted) missing.add("android.permission.RECORD_AUDIO")
        if (!cameraGranted) missing.add("android.permission.CAMERA")
        if (!phoneStateGranted) missing.add("android.permission.READ_PHONE_STATE")

        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), CALL_PERMISSIONS_REQUEST_CODE)
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
                // Re-check actual current grant state instead of trusting fixed
                // grantResults indices: the requested array now varies (only missing
                // perms are requested) and READ_PHONE_STATE is optional, so an index
                // read could be wrong or throw. Only mic+camera gate the call flow.
                val permissionToCamera = context?.let {
                    ContextCompat.checkSelfPermission(it, "android.permission.CAMERA")
                } == PackageManager.PERMISSION_GRANTED
                val permissionToRecord = context?.let {
                    ContextCompat.checkSelfPermission(it, "android.permission.RECORD_AUDIO")
                } == PackageManager.PERMISSION_GRANTED
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

    private fun finishFemaleHomeSwipeProfileIfPending() {
        if (!femaleHomeSwipeProfilePending) return
        femaleHomeSwipeProfilePending = false
        maybeFinishFemaleHomeSwipeRefresh()
    }

    private fun finishFemaleHomeSwipeReportsIfPending() {
        if (!femaleHomeSwipeReportsPending) return
        femaleHomeSwipeReportsPending = false
        maybeFinishFemaleHomeSwipeRefresh()
    }

    private fun maybeFinishFemaleHomeSwipeRefresh() {
        if (!::binding.isInitialized || !isAdded) return
        if (!femaleHomeSwipeProfilePending && !femaleHomeSwipeReportsPending) {
            binding.swipeRefreshFemaleHome.removeCallbacks(femaleHomeSwipeRefreshTimeout)
            binding.swipeRefreshFemaleHome.isRefreshing = false
        }
    }

    /**
     * Re-fetch balance, audio/video availability, today's earnings/calls, rates, discovery, badges.
     * Used by pull-to-refresh; [userId] must be non-null.
     */
    private fun startFemaleHomeSwipeRefresh(userId: Int) {
        femaleHomeSwipeProfilePending = true
        femaleHomeSwipeReportsPending = true
        binding.swipeRefreshFemaleHome.isRefreshing = true
        binding.swipeRefreshFemaleHome.removeCallbacks(femaleHomeSwipeRefreshTimeout)
        binding.swipeRefreshFemaleHome.postDelayed(femaleHomeSwipeRefreshTimeout, 8_000L)
        binding.swipeRefreshFemaleHome.applyHimaRefreshColors()

        femaleUsersViewModel.getReports(userId)
        profileViewModel.getUsers(userId)
        femaleUsersViewModel.getFemaleUsers(userId)
        femaleUsersViewModel.getFemaleDiscovery(userId)
        fetchBadgeList(userId)
        accountViewModel.getSettings()
    }

    private fun refreshIplBanner() {
        if (!::binding.isInitialized) return
        val iplEnabled = com.gmwapp.hima.utils.FeatureFlags.IPL_ENABLED &&
            (BaseApplication.getInstance()?.getPrefs()?.getUserData()?.ipl_rooms_enabled ?: 0) == 1
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


        // Subscription is handled centrally in BaseApplication (and at OTP success).
        // All that remains here is setting user-scoped tags and prompting for
        // notification permission — without the logout/optOut churn that used to
        // strand devices in the opted-out state.
        if (userData?.id != null && userData.id > 0) {
            OneSignal.User.addTag("gender", "female")
            language?.let {
                OneSignal.User.addTag("language", it)
                OneSignal.User.addTag("gender_language", "female_$it")
                Log.d("OneSignalTag", "tags set language=$it gender_language=female_$it")
            }

            val notifPrefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val lastAsked = notifPrefs.getLong("notif_permission_last_asked", 0L)
            if (System.currentTimeMillis() - lastAsked >= 24 * 60 * 60 * 1000L) {
                notifPrefs.edit().putLong("notif_permission_last_asked", System.currentTimeMillis()).apply()
                viewLifecycleOwner.lifecycleScope.launch {
                    OneSignal.Notifications.requestPermission(true)
                }
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
                        // Persist the freshly-fetched settings so cached reads on the
                        // call screens (icebreaker toggles, call_bonus) are never stale.
                        BaseApplication.getInstance()?.getPrefs()?.setSettingsData(settingsData)
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

        // 2026-05-22 v21 — belt&suspenders: also hide at runtime + kill the
        // navigation. Creator Level feature held for later release.
        binding.cvCreatorLevel.visibility = View.GONE
        binding.cvCreatorLevel.setOnSingleClickListener { /* disabled */ }

        // Target female-home redesign: only the 4 target cards (Availability, tip,
        // Today's Activity, Creators Joining) are shown. Hide the extras in code too,
        // since some are force-shown by observers/XML otherwise.
        // cv_call_rates (blue Earnings Details chart) stays — handled by the
        // call_rates observer below (shown when the admin poster exists).
        binding.cvPriceSlab.visibility = View.GONE
        binding.cvYourBadge.visibility = View.GONE
        binding.cvYourAverage.visibility = View.GONE

        // IPL Room Calls banner click — visibility handled by refreshIplBanner()
        binding.cardIplRooms.setOnClickListener {
            startActivity(Intent(requireContext(), com.gmwapp.hima.activities.IplRoomsActivity::class.java))
        }
        refreshIplBanner()

        binding.swipeRefreshFemaleHome.setOnRefreshListener {
            val id = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
            if (id == null) {
                binding.swipeRefreshFemaleHome.isRefreshing = false
            } else {
                startFemaleHomeSwipeRefresh(id)
            }
        }

        if (userData != null) {
            // Disable listeners before initial setup to avoid triggering API calls
            binding.sAudio.setOnCheckedChangeListener(null)
            binding.sVideo.setOnCheckedChangeListener(null)
            
            binding.sAudio.isChecked = userData.audio_status == 1
            binding.sVideo.isChecked = userData.video_status == 1
        }

        binding.tvCoins.text = "₹" + userData?.balance.toString()

        // Star Creator banner — app-side kill-switch overrides the server `star` flag.
        binding.clStarCreatorBanner.visibility =
            if (com.gmwapp.hima.utils.FeatureFlags.STAR_CREATOR_ENABLED && userData?.star == 1) View.VISIBLE else View.GONE

        Log.d("femaleuserdata", "${userData?.name} , ${userData?.language}")

        femaleUsersViewModel.getReports(userData?.id!!)
        femaleUsersViewModel.getFemaleUsers(userData.id)
        showDiscoverySkeleton()
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
                // TC_020: keep the card visible with an empty state instead of hiding it.
                renderDiscoveryCreators(emptyList())
            }
        })

        femaleUsersViewModel.femaleDiscoveryErrorLiveData.observe(viewLifecycleOwner, Observer {
            // TC_020: on a transient discovery error, keep the card visible. Preserve any
            // already-shown creators; only fall back to the empty state if nothing loaded.
            binding.cvFemaleDiscovery.visibility = View.VISIBLE
            if (binding.llDiscoveryCreators.childCount == 0) {
                binding.llDiscoveryCreators.visibility = View.GONE
                binding.tvDiscoveryEmpty.visibility = View.VISIBLE
            }
        })

        femaleUsersViewModel.reportResponseLiveData.observe(viewLifecycleOwner, Observer {
            try {
                if (it != null && it.success) {

                    Log.d("reportResponseLiveData", "$it")
                    Log.d("first_call", "${it.data[0].first_call}")


                    binding.tvApproxEarnings.text = it.data[0].today_earnings.toString()
                    binding.tvTotalCalls.text = it.data[0].today_calls.toString()

                    // v1110 EARNINGS_CARD_FIX (2026-06-11): the big card in the layout
                    // (cl_earnings_row / tv_approx_earnings_old) was a dead placeholder
                    // showing "₹0" forever because no Kotlin code ever updated it. Mirror
                    // today's earnings into it with the ₹ prefix that the layout's
                    // initial text used. Same value source as tv_approx_earnings above.
                    binding.tvApproxEarningsOld.text = "₹${it.data[0].today_earnings}"

                    // Split-earnings: show today's GIFT income in its own row, separate
                    // from Call earnings above. Old backend omits the field → shows ₹0.
                    binding.tvGiftEarnings.text = "₹${it.data[0].today_gift_earnings ?: "0"}"

                    // TC_020: the calls twin of the v1110 fix above was missed.
                    // tv_total_calls_old — the "Total Calls" value inside the same
                    // visible "Today's Activity" card — is never written by any other
                    // Kotlin code, so it stayed on its hardcoded layout default "0"
                    // regardless of real activity (the "Today's Activity missing"
                    // symptom). Mirror today's calls into it; same value source as
                    // tv_total_calls above.
                    binding.tvTotalCallsOld.text = it.data[0].today_calls.toString()

                    // BUG 15 — prefer the structured earning_details payload and draw the
                    // card natively; fall back to the legacy poster image otherwise.
                    //
                    // renderEarningDetails() returns false for null / empty / malformed
                    // input AND on any exception, so with today's backend (which does not
                    // send the field) this is a no-op and the image path below runs
                    // exactly as it always has. That is what keeps live users unaffected:
                    // the new branch cannot engage until the backend opts in.
                    if (renderEarningDetails(it.data[0].earning_details)) {
                        binding.ivCallRates.visibility = View.GONE
                        binding.llEarningDetails.visibility = View.VISIBLE
                        binding.cvCallRates.visibility = View.VISIBLE
                    } else {
                        binding.llEarningDetails.visibility = View.GONE
                        // Restored 2026-05-22: load admin-uploaded call rates poster.
                        // (Autopay merge had hidden this; user confirmed the admin poster
                        // is the source of truth for the female-home Earnings Details card.)
                        it.data[0].call_rates?.let { imageUrl ->
                            if (imageUrl.isNotEmpty()) {
                                binding.ivCallRates.visibility = View.VISIBLE
                                binding.cvCallRates.visibility = View.VISIBLE
                                Glide.with(requireContext())
                                    .load(imageUrl)
                                    .into(binding.ivCallRates)
                            } else {
                                binding.ivCallRates.visibility = View.GONE
                                binding.cvCallRates.visibility = View.GONE
                            }
                        } ?: run {
                            binding.ivCallRates.visibility = View.GONE
                            binding.cvCallRates.visibility = View.GONE
                        }
                    }

                    var firstCall = it.data[0].first_call
                    if (firstCall==1){
                       var femaleuserid= BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id

                        val bundle = Bundle().apply {
                            putString("user_id", "$femaleuserid") // optional: useful for debugging
                            putString("first_call_status", "Received")
                        }
                        BaseApplication.firebaseAnalytics.logEvent("first_call", bundle)

                        // 2026-05-24 v1074 — mirror first_call to Meta per marketing.
                        try {
                            com.facebook.appevents.AppEventsLogger.newLogger(requireContext())
                                .logEvent("first_call", bundle)
                        } catch (t: Throwable) {
                            android.util.Log.w("HimaAnalytics", "Meta first_call failed: ${t.message}")
                        }

                        // Log to backend
                        AppEventLogger.logEvent(
                            context = requireContext(),
                            eventName = "first_call",
                            platform = "firebase",
                            userId = femaleuserid,
                            params = AppEventLogger.bundleToMap(bundle)
                        )

                        // Adjust (mirrors alongside Firebase + Meta + backend).
                        com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
                            "first_call",
                            params = mapOf("user_id" to "$femaleuserid", "first_call_status" to "Received")
                        )

                        if (femaleuserid != null) {
                            firstCallUpdateViewModel.updateFirstCallStatus(femaleuserid, 2)
                        }
                    }

                } else {
                    //  Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                }
            } finally {
                finishFemaleHomeSwipeReportsIfPending()
            }
        })

        femaleUsersViewModel.reportsErrorLiveData.observe(viewLifecycleOwner) {
            finishFemaleHomeSwipeReportsIfPending()
        }

        profileViewModel.getUserLiveData.observe(viewLifecycleOwner, Observer { response ->
            finishFemaleHomeSwipeProfileIfPending()
            val data = response?.data ?: return@Observer
            if (!isAdded) return@Observer
            val prefsLocal = BaseApplication.getInstance()?.getPrefs() ?: return@Observer
            // B075 — preserve the user's toggle / DND intent. The updateCallStatus
            // observer below is the legitimate writer for audio_status / video_status.
            prefsLocal.setUserDataPreservingLocalIntent(data)
            // Bug #10 — re-apply the account-blocked banner now that fresh server data
            // has landed. onResume() evaluated it against the stale cached
            // UserData.blocked (this getUsers() refresh runs async, after that), so
            // without this the creator's banner only appeared on the NEXT launch.
            applyDismissableBlockBanner(
                binding.blockBannerFemale.root,
                binding.blockBannerFemale.btnCloseBanner
            )
            binding.tvCoins.text = "₹" + data.balance.toString()
            binding.clStarCreatorBanner.visibility =
                if (com.gmwapp.hima.utils.FeatureFlags.STAR_CREATOR_ENABLED && data.star == 1) View.VISIBLE else View.GONE
            refreshIplBanner()

            // v1110 NO_AUTO_ONLINE fix (2026-06-11):
            // Removed the B074 re-push that silently called updateCallStatus(AUDIO/VIDEO, 1)
            // whenever the UI switch was ON and the server reported 0. That auto-call was
            // the root cause of the v1109 "23 ghost-onlines per creator per day" pattern
            // (vs ~9 on v54 — see APP_SPEC_NO_AUTO_ONLINE_v1110_2026-06-10.md).
            //
            // From v1110 onward, the user-tap handler in setupSwitchListeners() is the
            // ONLY writer of audio_status / video_status. Server state is authoritative
            // for display; if the server has auto-OFF'd the creator, the UI will show
            // OFF and the creator must consciously tap the toggle to come back online.
            val effectiveAudio = data.audio_status
            val effectiveVideo = data.video_status
            val shouldSetAudio = pendingAudioStatus == null || pendingAudioStatus == effectiveAudio
            val shouldSetVideo = pendingVideoStatus == null || pendingVideoStatus == effectiveVideo
            if (shouldSetAudio || shouldSetVideo) {
                binding.sAudio.setOnCheckedChangeListener(null)
                binding.sVideo.setOnCheckedChangeListener(null)
                if (shouldSetAudio) {
                    binding.sAudio.isChecked = effectiveAudio == 1
                    pendingAudioStatus = null
                }
                if (shouldSetVideo) {
                    binding.sVideo.isChecked = effectiveVideo == 1
                    pendingVideoStatus = null
                }
                setupSwitchListeners(data)

                // TOGGLE_FLICKER_FIX — the bottom-nav rebuilds Home via replace(), so a
                // fresh FemaleHomeFragment paints the switch synchronously from cached
                // UserData (initUI). The B075 merge freezes cached audio_status /
                // video_status at their first non-null value (prev ?: fresh), so the cache
                // can stay 0 while the server (and this observer) show 1 — the next Home
                // entry then paints OFF, then this getUsers() reply flips it ON ~½s later:
                // a visible OFF→ON blink. When no toggle is in-flight (the shouldSet path),
                // the value we just painted IS the truth, so persist it. This only realigns
                // the cache with what's already on screen — no updateCallStatus, so no
                // ghost-online — and the pending guard still lets a just-tapped intent win.
                prefsLocal.getUserData()?.let { cached ->
                    prefsLocal.setUserData(
                        cached.copy(
                            audio_status = if (shouldSetAudio) effectiveAudio else cached.audio_status,
                            video_status = if (shouldSetVideo) effectiveVideo else cached.video_status
                        )
                    )
                }
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
        
        // 2026-07-27 — the old cumulative two_min_duration_completed event was
        // retired. Its replacement (two_min_new_female) now fires from
        // FemaleVideoCallingActivity at call-end (first 2-min video call, once
        // per creator), so the talk-duration API + these observers are no longer
        // used here.
        
        // Set up switch listeners once at the end of initUI
        setupSwitchListeners(userData)
    }

    /** C-02: fill the discovery row with shimmer placeholders on a cold load so
     *  it isn't a blank strip until the feed returns. Never overwrites real
     *  content — only shows when the row is empty (or already skeletoned). */
    private fun showDiscoverySkeleton() {
        if (!::binding.isInitialized) return
        val ll = binding.llDiscoveryCreators
        if (ll.childCount > 0 && !discoverySkeletonShowing) return  // real items present
        binding.cvFemaleDiscovery.visibility = View.VISIBLE
        binding.tvDiscoveryEmpty.visibility = View.GONE
        ll.visibility = View.VISIBLE
        ll.removeAllViews()
        repeat(4) {
            ll.addView(layoutInflater.inflate(R.layout.item_female_discovery_skeleton, ll, false))
        }
        discoverySkeletonShowing = true
        discoverySkeletonAnim?.cancel()
        discoverySkeletonAnim = android.animation.ObjectAnimator.ofFloat(ll, "alpha", 1f, 0.4f).apply {
            duration = 650L
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopDiscoverySkeleton() {
        discoverySkeletonAnim?.cancel()
        discoverySkeletonAnim = null
        discoverySkeletonShowing = false
        if (::binding.isInitialized) binding.llDiscoveryCreators.alpha = 1f
    }

    private fun renderDiscoveryCreators(creators: List<Pair<String, String?>>) {
        // C-02: real (or empty-state) content is about to replace the placeholders.
        stopDiscoverySkeleton()
        // TC_020: the online-presence card must stay visible at all times. Previously an
        // empty (or failed) discovery fetch hid the whole card (View.GONE) — that was the
        // "Online tab missing entirely" symptom. Now we keep the card and show an empty state.
        binding.cvFemaleDiscovery.visibility = View.VISIBLE
        if (creators.isEmpty()) {
            binding.llDiscoveryCreators.removeAllViews()
            binding.llDiscoveryCreators.visibility = View.GONE
            binding.tvDiscoveryEmpty.visibility = View.VISIBLE
            binding.tvOnlineCount.text = "0 online"
            return
        }
        binding.tvDiscoveryEmpty.visibility = View.GONE
        binding.llDiscoveryCreators.visibility = View.VISIBLE
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

            tvName.text = com.gmwapp.hima.utils.DisplayName.clean(name)
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
        // B074: never early-return. Earlier this method bailed when [userData]
        // was null (the error branches of updateCallStatus and an empty
        // getUserLiveData response after pull-to-refresh both pass null),
        // leaving sAudio / sVideo permanently un-listened until app restart.
        // We always attach; the lambdas resolve a live user at click-time
        // from prefs so a null param here can't permanently disable toggles.
        binding.sAudio.setOnCheckedChangeListener { _, isChecked ->
            val user = userData ?: getInstance()?.getPrefs()?.getUserData()
            ?: return@setOnCheckedChangeListener
            if (isChecked && !hasRecordAudioPermission()) {
                binding.sAudio.setOnCheckedChangeListener(null)
                binding.sAudio.isChecked = false
                setupSwitchListeners(user)
                audioCallEnablePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return@setOnCheckedChangeListener
            }
            val proceed = {
                // Marketing — Button Enabled (creator turns audio availability ON).
                if (isChecked) com.gmwapp.hima.utils.HimaAnalytics.logButtonEnabled(requireContext(), user.id, "audio")
                pendingAudioStatus = if (isChecked) 1 else 0
                if (isChecked) promptPostNotificationsIfNeededForCalls()
                if (isChecked) disableDndIfActiveForAvailability()
                // B151 — coalesce rapid taps. Each new tap cancels the pending
                // launch; only the final state in a tap burst hits the server.
                audioToggleDebounceJob?.cancel()
                audioToggleDebounceJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(TOGGLE_DEBOUNCE_MS)
                    femaleUsersViewModel.updateCallStatus(
                        user.id,
                        DConstants.AUDIO,
                        if (isChecked) 1 else 0
                    )
                }
            }
            // B11 — turning availability ON also turns DND OFF. Confirm first; on
            // cancel snap the toggle back off and keep DND on.
            if (isChecked && BaseApplication.isDndActiveStatic(user)) {
                com.gmwapp.hima.utils.DndDisableConfirm.show(
                    this,
                    onConfirmed = { proceed() },
                    onCancelled = {
                        binding.sAudio.setOnCheckedChangeListener(null)
                        binding.sAudio.isChecked = false
                        setupSwitchListeners(user)
                    }
                )
                return@setOnCheckedChangeListener
            }
            proceed()
        }

        binding.sVideo.setOnCheckedChangeListener { _, isChecked ->
            val user = userData ?: getInstance()?.getPrefs()?.getUserData()
            ?: return@setOnCheckedChangeListener
            if (isChecked && (!hasCameraPermission() || !hasRecordAudioPermission())) {
                binding.sVideo.setOnCheckedChangeListener(null)
                binding.sVideo.isChecked = false
                setupSwitchListeners(user)
                startActivity(Intent(requireContext(), GrantPermissionsActivity::class.java))
                return@setOnCheckedChangeListener
            }
            val proceed = {
                // Marketing — Button Enabled (creator turns video availability ON).
                if (isChecked) com.gmwapp.hima.utils.HimaAnalytics.logButtonEnabled(requireContext(), user.id, "video")
                pendingVideoStatus = if (isChecked) 1 else 0
                if (isChecked) promptPostNotificationsIfNeededForCalls()
                if (isChecked) disableDndIfActiveForAvailability()
                // B151 — debounced for the same reason as the audio toggle above.
                videoToggleDebounceJob?.cancel()
                videoToggleDebounceJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(TOGGLE_DEBOUNCE_MS)
                    femaleUsersViewModel.updateCallStatus(
                        user.id,
                        DConstants.VIDEO,
                        if (isChecked) 1 else 0
                    )
                }
            }
            // B11 — turning availability ON also turns DND OFF. Confirm first; on
            // cancel snap the toggle back off and keep DND on.
            if (isChecked && BaseApplication.isDndActiveStatic(user)) {
                com.gmwapp.hima.utils.DndDisableConfirm.show(
                    this,
                    onConfirmed = { proceed() },
                    onCancelled = {
                        binding.sVideo.setOnCheckedChangeListener(null)
                        binding.sVideo.isChecked = false
                        setupSwitchListeners(user)
                    }
                )
                return@setOnCheckedChangeListener
            }
            proceed()
        }
    }

    /**
     * When the creator turns an availability toggle (audio/video) back ON while
     * Do Not Disturb is still active, DND would keep silently black-holing every
     * incoming call — she'd look online but males still couldn't reach her.
     * Availability and DND are mutually exclusive, so auto-clear DND and tell her.
     * No-op when DND isn't active (the common case), so a normal toggle stays silent.
     *
     * DND is purely CLIENT-SIDE suppression ([BaseApplication.isDndActiveStatic]
     * reads the local prefs copy), so clearing the local flag is what actually
     * unblocks calls — do that optimistically, then best-effort sync the server
     * (toggle_dnd is 404 on prod; the local clear stands regardless).
     */
    private fun disableDndIfActiveForAvailability() {
        val user = getInstance()?.getPrefs()?.getUserData() ?: return
        if (!BaseApplication.isDndActiveStatic(user)) return
        getInstance()?.getPrefs()?.setUserData(user.copy(dnd_enabled = 0, dnd_until = null))
        Toast.makeText(
            requireContext(),
            getString(R.string.dnd_auto_off_for_availability),
            Toast.LENGTH_LONG
        ).show()
        profileViewModel.toggleDnd(
            user.id, 0, 0,
            object : com.gmwapp.hima.retrofit.callbacks.NetworkCallback<com.gmwapp.hima.retrofit.responses.ToggleDndResponse> {
                override fun onResponse(
                    call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.ToggleDndResponse>,
                    response: retrofit2.Response<com.gmwapp.hima.retrofit.responses.ToggleDndResponse>
                ) {
                    Log.d("FemaleHomeFragment", "DND auto-off resp http=${response.code()} success=${response.body()?.success}")
                }

                override fun onFailure(
                    call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.ToggleDndResponse>,
                    t: Throwable
                ) {
                    Log.w("FemaleHomeFragment", "DND auto-off server sync failed (local clear stands): ${t.message}")
                }

                override fun onNoNetwork() {
                    Log.w("FemaleHomeFragment", "DND auto-off server sync skipped, no network (local clear stands)")
                }
            }
        )
    }

    fun updateEarnings(){
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
            profileViewModel.getUsers(it)
        }
    }

    override fun onDestroyView() {
        // C-02: stop the discovery shimmer so its animator can't outlive the view.
        discoverySkeletonAnim?.cancel()
        discoverySkeletonAnim = null
        super.onDestroyView()
    }

    override fun onPause() {
        super.onPause()
        // TC-HMA-004: the audio/video availability toggle sends its updateCallStatus after a 400 ms
        // debounce that runs in the VIEW lifecycle scope. Leaving Home within 400 ms of a tap (very
        // easy — tap the toggle, then a bottom-nav tab) cancels that coroutine before it fires, so
        // the ON/OFF never reaches the server and the creator returns to a switch snapped back to OFF.
        // Flush any still-pending toggle here: onPause runs before the view scope is torn down, and
        // updateCallStatus enqueues a fire-and-forget Retrofit call, so it completes even as the
        // fragment goes away.
        val u = getInstance()?.getPrefs()?.getUserData()
        if (u != null) {
            if (audioToggleDebounceJob?.isActive == true) {
                audioToggleDebounceJob?.cancel()
                pendingAudioStatus?.let { femaleUsersViewModel.updateCallStatus(u.id, DConstants.AUDIO, it) }
            }
            if (videoToggleDebounceJob?.isActive == true) {
                videoToggleDebounceJob?.cancel()
                pendingVideoStatus?.let { femaleUsersViewModel.updateCallStatus(u.id, DConstants.VIDEO, it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Bug #10 — app-open account-blocked banner (creator Home, dismissable)
        applyDismissableBlockBanner(
            binding.blockBannerFemale.root,
            binding.blockBannerFemale.btnCloseBanner
        )
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userData = prefs?.getUserData()

        // TC-INC-001: recover a dropped incoming-call ring. If a call push was
        // lost while the app was backgrounded (Doze / OneSignal fallback / stale
        // token), this surfaces the banner the creator missed by pulling
        // authoritative server state on foreground. No-op unless a call is
        // ringing for her right now; self-guards against double-ringing.
        com.gmwapp.hima.utils.IncomingCallRecovery.check()

        // Don't set switches here - let updateEarnings() fetch fresh data and set them
        // This prevents race condition between cached data and API data

        if (userData != null && userData.id != null) {
            // Check and log voice_verified event if status is 2
            checkAndLogVoiceVerified(userData)

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
                // TC_028 (B17): persist the per-user idempotency guard SYNCHRONOUSLY
                // and BEFORE emitting. apply() only schedules an async disk write, so
                // if the process was killed right after verification (common — the
                // creator is bounced between screens at that moment) the flag never
                // reached disk and the next launch re-fired the event, doubling the
                // voice_verified funnel count. commit() + set-first closes that window
                // and also blocks any rapid second onResume.
                sharedPreferences.edit()
                    .putInt("last_voice_verified_status_${userData.id}", 2)
                    .commit()

                // Prepare event parameters
                val userId = userData.id

                // TC_028 (B17): the idempotency guard is already committed above, so
                // emit to each platform INDEPENDENTLY — one SDK throwing (Meta/AppsFlyer
                // not yet initialised, a detached context, etc.) must not skip the
                // remaining emits. The guard intentionally stays set either way: a rare
                // lost event is preferable to a double-counted funnel.

                // 1. Firebase Analytics - voice_verified
                val firebaseBundle = Bundle().apply {
                    putString("user_id", "$userId")
                    putString("gender", userData.gender ?: "")
                    putString("status", "${userData.status}")
                }
                runCatching {
                    BaseApplication.firebaseAnalytics.logEvent("voice_verified", firebaseBundle)
                }.onFailure { Log.w("FemaleHomeFragment", "voice_verified Firebase emit failed: ${it.message}") }

                // 2. Meta/Facebook Analytics - voice_verified
                val metaParams = Bundle().apply {
                    putString("user_id", "$userId")
                    putString("gender", userData.gender ?: "")
                    putString("status", "${userData.status}")
                }
                runCatching {
                    AppEventsLogger.newLogger(requireContext()).logEvent("voice_verified", metaParams)
                }.onFailure { Log.w("FemaleHomeFragment", "voice_verified Meta emit failed: ${it.message}") }

                // 3. MMP - voice_verified
                runCatching {
                    MmpClient.trackEvent(
                        eventName = "voice_verified",
                        params = mapOf(
                            "user_id" to "$userId",
                            "gender" to (userData.gender ?: ""),
                            "status" to "${userData.status}"
                        ),
                        customerUserId = "$userId"
                    )
                }.onFailure { Log.w("FemaleHomeFragment", "voice_verified MMP emit failed: ${it.message}") }

                // 4. Log to backend (only Firebase events)
                runCatching {
                    AppEventLogger.logEvent(
                        context = requireContext(),
                        eventName = "voice_verified",
                        platform = "firebase",
                        userId = userId,
                        params = AppEventLogger.bundleToMap(firebaseBundle)
                    )
                }.onFailure { Log.w("FemaleHomeFragment", "voice_verified backend emit failed: ${it.message}") }

                // 5. Adjust - voice_verified (mirrors alongside the emits above)
                runCatching {
                    com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
                        "voice_verified",
                        params = mapOf(
                            "user_id" to "$userId",
                            "gender" to (userData.gender ?: ""),
                            "status" to "${userData.status}"
                        )
                    )
                }.onFailure { Log.w("FemaleHomeFragment", "voice_verified Adjust emit failed: ${it.message}") }

                // Idempotency guard already persisted above (commit) BEFORE emit.

                Log.d("FemaleHomeFragment", "✅ voice_verified event logged to Firebase, Meta, AppsFlyer, and backend for user $userId")
            }
        }
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
            "Tamil" -> "https://chat.whatsapp.com/JVQB42dDPQsBV9MwSUB2kv?s=cl&p=i&ilr=4"
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
        // Guard: see HomeFragment.refresh — MainActivity may fire this after a
        // configuration change (e.g. split-screen) before our view is rebound.
        if (view == null || !::binding.isInitialized) return
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        femaleUsersViewModel.getReports(userId)
        femaleUsersViewModel.getFemaleUsers(userId)
        femaleUsersViewModel.getFemaleDiscovery(userId)
        fetchBadgeList(userId)
        updateEarnings()
        accountViewModel.getSettings()
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