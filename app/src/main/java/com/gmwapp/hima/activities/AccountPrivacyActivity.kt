package com.gmwapp.hima.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityAccountPrivacyBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class AccountPrivacyActivity : BaseActivity() {
    lateinit var binding: ActivityAccountPrivacyBinding
    private val accountViewModel: AccountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountPrivacyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUI()
    }

    private fun initUI() {
        val prefs = BaseApplication.getInstance()?.getPrefs()
        accountViewModel.getSettings()

        binding.cvDeleteAccount.setOnSingleClickListener {
            val intent = Intent(this, DeleteAccountActivity::class.java)
            startActivity(intent)
        }
        binding.cvBack.setOnSingleClickListener {
            finish()
        }
        binding.cvPrivacyPolicy.setOnSingleClickListener {
            try {
                val intent = Intent(this, WebviewActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    this@AccountPrivacyActivity, e.message, Toast.LENGTH_LONG
                ).show()
                e.message?.let { it1 -> Log.e("AccountPrivacyActivity", it1) }
            }
        }
        accountViewModel.settingsLiveData.observe(this, Observer {
            if (it!=null && it.success) {
                if (it.data != null) {
                    if (it.data.size > 0) {
                        prefs?.setSettingsData(it.data.get(0))
                    }
                }
            }
        })
    }
}