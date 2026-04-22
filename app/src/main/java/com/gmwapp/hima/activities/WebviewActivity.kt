package com.gmwapp.hima.activities

import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.R
import com.gmwapp.hima.callbacks.OnButtonClickListener
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityAlmostDoneBinding
import com.gmwapp.hima.databinding.ActivityVoiceIdentificationBinding
import com.gmwapp.hima.databinding.ActivityWebviewBinding
import com.gmwapp.hima.dialogs.BottomSheetVoiceIdentification
import com.gmwapp.hima.utils.applySystemBarInsets
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File


@AndroidEntryPoint
class WebviewActivity : BaseActivity() {
    lateinit var binding: ActivityWebviewBinding
    private val accountViewModel: AccountViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, R.color.white)
        binding.wvPrivacyPolicy.getSettings().setJavaScriptEnabled(true);

        val prefs = BaseApplication.getInstance()?.getPrefs()
        prefs?.getSettingsData()?.privacy_policy?.let {
            binding.wvPrivacyPolicy.loadDataWithBaseURL(
                null,  // Base URL, null if no base is needed
                it,  // The HTML content
                "text/html",  // MIME type for HTML
                "UTF-8",  // Encoding
                null  // History URL, set to null
            )
        }

        accountViewModel.getSettings()
        accountViewModel.settingsLiveData.observe(this, Observer {
            if (it!=null && it.success) {
                if (it.data != null) {
                    if (it.data.size > 0) {
                        prefs?.setSettingsData(it.data.get(0))
                        val prefs = BaseApplication.getInstance()?.getPrefs()
                        Log.d("PrivacyPolicy", "Your Privacy : ${it}")
                        prefs?.getSettingsData()?.privacy_policy?.let {
                            binding.wvPrivacyPolicy.loadDataWithBaseURL(
                                null,  // Base URL, null if no base is needed
                                it,  // The HTML content
                                "text/html",  // MIME type for HTML
                                "UTF-8",  // Encoding
                                null  // History URL, set to null
                            )
                        }
                    }
                }
            }
        })
    }
}