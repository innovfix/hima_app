package com.gmwapp.hima.activities

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.databinding.ActivityCommunityGuidelineBinding
import com.gmwapp.hima.viewmodels.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CommunityGuidelineActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCommunityGuidelineBinding
    private val accountViewModel: AccountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityGuidelineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable JavaScript in WebView
        binding.wvPrivacyPolicy.settings.javaScriptEnabled = true

        // Load from prefs if already saved
        val prefs = BaseApplication.getInstance()?.getPrefs()
        prefs?.getSettingsData()?.guideline?.let {
            binding.wvPrivacyPolicy.loadUrl(it)
        }

        // Fetch settings from ViewModel
        accountViewModel.getSettings()
        accountViewModel.settingsLiveData.observe(this, Observer {
            if (it != null && it.success) {
                if (!it.data.isNullOrEmpty()) {
                    prefs?.setSettingsData(it.data[0])
                    val url = prefs?.getSettingsData()?.guideline
                    Log.d("CommunityGuideline", "Loaded URL: $url")
                    url?.let { binding.wvPrivacyPolicy.loadUrl(it) }
                }
            }
        })
    }
}
