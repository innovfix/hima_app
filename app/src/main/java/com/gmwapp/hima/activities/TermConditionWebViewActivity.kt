package com.gmwapp.hima.activities

import android.os.Bundle
import android.util.Log
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
        binding.wvPrivacyPolicy.getSettings().setJavaScriptEnabled(true);

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