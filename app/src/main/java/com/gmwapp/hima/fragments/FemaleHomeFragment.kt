package com.gmwapp.hima.fragments

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager.IMPORTANCE_NONE
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsClient.getPackageName
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.EarningsActivity
import com.gmwapp.hima.activities.GrantPermissionsActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentFemaleHomeBinding
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FemaleCallAttendResponse
import com.gmwapp.hima.agora.services.CallingService
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.gmwapp.hima.viewmodels.WhatsappLinkViewModel
import com.gmwapp.hima.workers.CallUpdateWorker
import com.onesignal.OneSignal
//import com.tencent.mmkv.MMKV
//import com.zegocloud.uikit.ZegoUIKit
//import com.zegocloud.uikit.prebuilt.call.core.CallInvitationServiceImpl
//import com.zegocloud.uikit.prebuilt.call.core.notification.PrebuiltCallNotificationManager
import dagger.hilt.android.AndroidEntryPoint
//import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone


@AndroidEntryPoint
class FemaleHomeFragment : BaseFragment() {
    private val OVERLAY_REQUEST_CODE: Int = 2
    private var mContext: Context? = null
    private val CALL_PERMISSIONS_REQUEST_CODE = 1
    private val NOTIFICATIONS_ENABLED_REQUEST_CODE = 3
    lateinit var binding: FragmentFemaleHomeBinding
    lateinit var language : String
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    private val whatsappLinkViewModel: WhatsappLinkViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()

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
            try {
                askNotificationPermission()
            } catch (e: Exception) {
                val intent = Intent(context, GrantPermissionsActivity::class.java)
                startActivity(intent)
            }
        }
    }
    private var startTime: String = ""
    private var endTime: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentFemaleHomeBinding.inflate(layoutInflater)

        sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        isPermissionDenied = sharedPreferences.getBoolean("isTagSet", false)
        initUI()
        askPermissions()
        return binding.root
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

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        requireActivity(), Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    askNotificationsEnabled();
                } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    val intent = Intent(context, GrantPermissionsActivity::class.java)
                    startActivity(intent)
                } else {
                    try {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } catch (e: Exception) {
                        val intent = Intent(context, GrantPermissionsActivity::class.java)
                        startActivity(intent)
                    }
                }
            } else {
               askNotificationsEnabled();
            }
        } catch (e: Exception) {
            val intent = Intent(context, GrantPermissionsActivity::class.java)
            startActivity(intent)
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

    private fun initUI() {

        accountViewModel.getSettings()
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userData = prefs?.getUserData()


        language = userData?.language.toString()

        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val isTagSet = sharedPreferences.getBoolean("isOneSignalTagSet", false)



        OneSignal.User.addTag("gender", "female")
        language?.let {
            OneSignal.User.addTag("language", it)
            Log.d("OneSignalTag", "Language tag added: $it")
        }

        language?.let {
            OneSignal.User.addTag("gender_language", "female_$it")
            Log.d("OneSignalTag", "female_$it")

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
                            //binding.tvDisclaimer.setText(auto_disable_info)
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

        if (userData != null) {
            binding.sAudio.isChecked = userData.audio_status == 1
            binding.sVideo.isChecked = userData.video_status == 1
        }

        binding.tvCoins.text = "₹" + userData?.balance.toString()

        Log.d("femaleuserdata", "${userData?.name} , ${userData?.language}")

        femaleUsersViewModel.getReports(userData?.id!!)


        femaleUsersViewModel.reportResponseLiveData.observe(viewLifecycleOwner, Observer {
            if (it.success) {

                binding.tvApproxEarnings.text = it.data[0].today_earnings.toString()
                binding.tvTotalCalls.text = it.data[0].today_calls.toString()

            } else {
                //  Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
            }
        })

        femaleUsersViewModel.updateCallStatusResponseLiveData.observe(viewLifecycleOwner, Observer {
            if (it != null && it.success) {
                prefs.setUserData(it.data)
            } else {
                Toast.makeText(context, it?.message, Toast.LENGTH_SHORT).show()
                binding.sAudio.isChecked = prefs.getUserData()?.audio_status == 1
                binding.sVideo.isChecked = prefs.getUserData()?.video_status == 1
            }
        })
        femaleUsersViewModel.updateCallStatusErrorLiveData.observe(viewLifecycleOwner, Observer {
            showErrorMessage(it)
            binding.sAudio.isChecked = prefs.getUserData()?.audio_status == 1
            binding.sVideo.isChecked = prefs.getUserData()?.video_status == 1
        })
        binding.sAudio.setOnCheckedChangeListener({ buttonView, isChecked ->
            userData.id.let {
                femaleUsersViewModel.updateCallStatus(
                    it, DConstants.AUDIO, if (isChecked) 1 else 0
                )
            }
            if (isChecked) {

            }
        })
        binding.sVideo.setOnCheckedChangeListener({ buttonView, isChecked ->
            userData.id.let {
                femaleUsersViewModel.updateCallStatus(
                    it, DConstants.VIDEO, if (isChecked) 1 else 0
                )
            }
        })


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

}