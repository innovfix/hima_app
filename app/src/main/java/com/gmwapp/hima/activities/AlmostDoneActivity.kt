package com.gmwapp.hima.activities

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.gmwapp.hima.utils.applySystemBarInsets
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

    // Guard so we route to the next screen exactly once.
    private var navigated = false

    // While she waits on the "under review" screen, poll her status so the app moves
    // to Home the moment Admin verifies her (status → 2) — instead of only checking on
    // onResume, which left her stuck here until she force-closed and reopened the app.
    private val statusPollHandler = Handler(Looper.getMainLooper())
    private val statusPollRunnable = object : Runnable {
        override fun run() {
            BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
                profileViewModel.getUsers(it)
            }
            statusPollHandler.postDelayed(this, STATUS_POLL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlmostDoneBinding.inflate(layoutInflater)
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val supportMail = prefs?.getSettingsData()?.support_mail
        
        // Set initial email if available
        if (!supportMail.isNullOrEmpty()) {
            binding.tvSupportMail.text = supportMail
            binding.tvSupportMail.paintFlags = binding.tvSupportMail.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        }
        
        accountViewModel.getSettings()
        accountViewModel.settingsLiveData.observe(this, Observer {
            if (it!=null && it.success) {
                if (it.data != null) {
                    if (it.data.size > 0) {
                        prefs?.setSettingsData(it.data.get(0))
                        val supportMail = prefs?.getSettingsData()?.support_mail
                        
                        if (!supportMail.isNullOrEmpty()) {
                            binding.tvSupportMail.text = supportMail
                            binding.tvSupportMail.paintFlags =
                                binding.tvSupportMail.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                            
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
                        }
                    }
                }
            }
        })
        setContentView(binding.root)
        applySystemBarInsets(binding.root, R.color.white, darkStatusBarIcons = true)

        // Observe status ONCE (not re-added on every resume). Each poll refreshes the
        // user; when Admin verifies her (status → 2) we route to Home live, and status 0
        // (voice ID not done) routes to voice identification.
        profileViewModel.getUserLiveData.observe(this, Observer {
            val userData: UserData? = it.data
            prefs?.setUserData(userData)
            Log.d("statusCheck", "status=${userData?.status}")
            if (navigated) return@Observer
            val intent: Intent? = when (userData?.status) {
                2 -> Intent(this, MainActivity::class.java).apply {
                    putExtra(DConstants.AVATAR_ID, getIntent().getIntExtra(DConstants.AVATAR_ID, 0))
                    putExtra(DConstants.LANGUAGE, userData.language)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                0 -> Intent(this, VoiceIdentificationActivity::class.java).apply {
                    putExtra(DConstants.LANGUAGE, userData.language)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                else -> null   // status 1 = still under review → keep waiting
            }
            if (intent != null) {
                navigated = true
                stopStatusPolling()
                startActivity(intent)
                finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // OneSignal subscription is handled centrally in BaseApplication and at OTP success;
        // calling logout/optOut here was stranding devices in the opted-out state.
        // Poll immediately + on an interval so an admin verification lands without a restart.
        startStatusPolling()
    }

    override fun onPause() {
        super.onPause()
        stopStatusPolling()
    }

    override fun onDestroy() {
        stopStatusPolling()
        super.onDestroy()
    }

    private fun startStatusPolling() {
        statusPollHandler.removeCallbacks(statusPollRunnable)
        statusPollHandler.post(statusPollRunnable)   // fires an immediate fetch, then every STATUS_POLL_MS
    }

    private fun stopStatusPolling() {
        statusPollHandler.removeCallbacks(statusPollRunnable)
    }

    companion object {
        private const val STATUS_POLL_MS = 4000L
    }
}