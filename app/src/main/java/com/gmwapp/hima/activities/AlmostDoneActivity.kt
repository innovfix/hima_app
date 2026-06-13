package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BaseApplication.Companion.getInstance
import com.gmwapp.hima.R
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityAlmostDoneBinding
import com.gmwapp.hima.retrofit.responses.UserData
import com.gmwapp.hima.utils.applySystemBarInsets
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlmostDoneBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, R.color.white, darkStatusBarIcons = true)
    }

    override fun onResume() {
        super.onResume()
        // OneSignal subscription is handled centrally in BaseApplication and at OTP success;
        // calling logout/optOut here was stranding devices in the opted-out state.
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

}