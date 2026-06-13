package com.gmwapp.hima.activities

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityCommunityGuidelineBinding
import com.gmwapp.hima.utils.applySystemBarInsets
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
        applySystemBarInsets(binding.root, R.color.white, darkStatusBarIcons = true)
        binding.includeProfileToolbar.tvFlowTitle.text = getString(R.string.CommunityGuidelines)
        binding.includeProfileToolbar.cvBack.setOnClickListener { finish() }

        // Enable JavaScript in WebView
        binding.wvPrivacyPolicy.settings.javaScriptEnabled = true
        // Inject CSS once each page finishes so section headers (h1–h6) render in brand pink.
        binding.wvPrivacyPolicy.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(
                    "(function(){var s=document.createElement('style');" +
                    "s.innerHTML='h1,h2,h3,h4,h5,h6{color:#FF2E9A !important;}';" +
                    "document.head.appendChild(s);})();",
                    null
                )
            }
        }

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
