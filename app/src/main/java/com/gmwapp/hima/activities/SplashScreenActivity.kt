package com.gmwapp.hima.activities

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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        initUI()
    }

    private fun initUI() {
        // Check for network connectivity
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

       // ZohoSalesIQ.showLauncher(false)


        // viewModel.appUpdate()

        var intent: Intent? = null
        val prefs = BaseApplication.getInstance()?.getPrefs()
        var userData = prefs?.getUserData()



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
            prefs?.setUserData(it?.data)
            userData = it?.data

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
            startActivity(intent)
            finish()
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
            if (response != null && response.success) {
                val data = response.data
                val link = response.data.link
                val description = response.data.description
                if (data.current_version.toInt() < data.minimum_version.toInt() &&
                    data.update_type == "mandatory") {
                    // 🔒 Force user to update
                    showUpdateDialog(link,description)

                } else {
                    GotoActivity(userData)

                }
            }
        }


    }

    fun GotoActivity(
        userData: UserData?,
    ) {

//            Toast.makeText(this, "1", Toast.LENGTH_SHORT).show()
            if (userData == null) {
//                Toast.makeText(this, "2", Toast.LENGTH_SHORT).show()

//                intent = Intent(this@SplashScreenActivity, NewLoginActivity::class.java)
                val intent = Intent(this@SplashScreenActivity, NewLoginActivity::class.java)
                startActivity(intent)
                finish()

            } else {
                if (userData?.gender == DConstants.MALE) {
//                    Toast.makeText(this, "3", Toast.LENGTH_SHORT).show()
                  //  intent = Intent(this@SplashScreenActivity, MainActivity::class.java)
                    val intent = Intent(this@SplashScreenActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
//                    Toast.makeText(this, "4", Toast.LENGTH_SHORT).show()
                    BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
                        profileViewModel.getUsers(it)
                    }

                }

                intent?.let {
                    Handler().postDelayed({
                        startActivity(it)
                        finish()
                    }, 3000)
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

