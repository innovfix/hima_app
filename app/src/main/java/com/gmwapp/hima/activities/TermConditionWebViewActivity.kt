package com.gmwapp.hima.activities

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityRefundWebViewBinding
import com.gmwapp.hima.databinding.ActivityTermConditionWebViewBinding
import com.gmwapp.hima.databinding.ActivityWebviewBinding
import com.gmwapp.hima.utils.applySystemBarInsets
import com.gmwapp.hima.viewmodels.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TermConditionWebViewActivity : AppCompatActivity() {
    lateinit var binding: ActivityTermConditionWebViewBinding
    private val accountViewModel: AccountViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTermConditionWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, R.color.white, darkStatusBarIcons = true)
        binding.includeProfileToolbar.tvFlowTitle.text = getString(R.string.terms_and_condition)
        binding.includeProfileToolbar.cvBack.setOnClickListener { finish() }
        binding.wvPrivacyPolicy.getSettings().setJavaScriptEnabled(true);
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

        val prefs = BaseApplication.getInstance()?.getPrefs()
        prefs?.getSettingsData()?.terms_conditions?.let {
          binding.wvPrivacyPolicy.loadUrl(it)
        }

        accountViewModel.getSettings()
        accountViewModel.settingsLiveData.observe(this, Observer {
            if (it!=null && it.success) {
                if (it.data != null) {
                    if (it.data.size > 0) {
                        prefs?.setSettingsData(it.data.get(0))
                        val prefs = BaseApplication.getInstance()?.getPrefs()
                        Log.d("PrivacyPolicy", "Your Privacy : ${it}")
                        prefs?.getSettingsData()?.terms_conditions?.let {
                            binding.wvPrivacyPolicy.loadUrl(it)

                        }
                    }
                }
            }
        })
    }
}