package com.gmwapp.hima.activities

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BaseApplication.Companion.getInstance
import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.R
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityAlmostDoneBinding
import com.gmwapp.hima.retrofit.responses.UserData
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.onesignal.OneSignal
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@AndroidEntryPoint
class AlmostDoneActivity : BaseActivity() {
    lateinit var binding: ActivityAlmostDoneBinding
    private val profileViewModel: ProfileViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlmostDoneBinding.inflate(layoutInflater)
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val supportMail = prefs?.getSettingsData()?.support_mail
        binding.tvSupportMail.text =
            supportMail
        accountViewModel.getSettings()
        accountViewModel.settingsLiveData.observe(this, Observer {
            if (it!=null && it.success) {
                if (it.data != null) {
                    if (it.data.size > 0) {
                        prefs?.setSettingsData(it.data.get(0))
                        val supportMail = prefs?.getSettingsData()?.support_mail
                        binding.tvSupportMail.text =
                            supportMail
                        val userData = prefs?.getUserData()
                        val subject = getString(R.string.delete_account_mail_subject, userData?.mobile,  userData?.language)

                        val body = getString(R.string.mail_body, userData?.mobile,android.os.Build.MODEL,userData?.language,
                            BuildConfig.VERSION_CODE
                        )
                        binding.tvSupportMail.setOnSingleClickListener {
                            val intent = Intent(Intent.ACTION_VIEW)

                            val data = Uri.parse(("mailto:$supportMail?subject=$subject").toString() + "&body=$body")
                            intent.setData(data)

                            startActivity(intent)
                        }
                        binding.tvSupportMail.paintFlags =
                            binding.tvSupportMail.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    }
                }
            }
        })
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        subscribeToOneSignal()
        var intent: Intent? = null
        val prefs = BaseApplication.getInstance()?.getPrefs()
        var userData: UserData?
        prefs?.getUserData()?.id?.let { profileViewModel.getUsers(it) }
        profileViewModel.getUserLiveData.observe(this, Observer {
            prefs?.setUserData(it.data);
            userData = it.data;
            Log.d("statusCheck","$")
            if(userData?.status == 2){
                intent = Intent(this, MainActivity::class.java)
                intent?.putExtra(
                    DConstants.AVATAR_ID, getIntent().getIntExtra(DConstants.AVATAR_ID, 0)
                )
                intent?.putExtra(DConstants.LANGUAGE, userData?.language)
                intent?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            } else if(userData?.status == 0){
                intent = Intent(this, VoiceIdentificationActivity::class.java)
                intent?.putExtra(DConstants.LANGUAGE, userData?.language)
                intent?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            if(intent!=null) {
                startActivity(intent)
                finish()
            }
        });
    }

    fun subscribeToOneSignal(){
        CoroutineScope(Dispatchers.Main).launch {
            delay(2000) // wait to ensure OneSignal is initialized fully

            // 1. FULL RESET before login
            OneSignal.logout()
            OneSignal.User.pushSubscription.optOut()

            // 2. Fetch user ID
            val userId = getInstance()?.getPrefs()?.getUserData()?.id.toString()
           var  language = getInstance()?.getPrefs()?.getUserData()?.language.toString()


            if (!userId.isNullOrEmpty() && userId != "null") {
                Log.d("OneSignalFix", "Attempting clean login with userId: $userId")

                // 3. Force fresh login
                OneSignal.login(userId)

                // 4. Re-subscribe and assign external ID
                OneSignal.User.pushSubscription.optIn()

                // 5. Prompt notification permission (Android 13+)
                OneSignal.Notifications.requestPermission(true)

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


    }
}