package com.gmwapp.hima

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.activities.NewLoginActivity
import com.gmwapp.hima.activities.WalletActivity

class DeepLinkActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_deep_link)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val uri = intent?.data
        Log.d("MyDeepLink", "Received URI: $uri")

        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userData = prefs?.getUserData()

        if (userData == null) {
            startActivity(Intent(this, NewLoginActivity::class.java))
            finish()
            return
        }

        when {
            uri?.toString()?.contains("786938q4", ignoreCase = true) == true -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return
            }

            uri?.toString()?.contains("h15zrp5j", ignoreCase = true) == true -> {
                val intent = Intent(this, WalletActivity::class.java)
                intent.putExtra("from_deeplink", true)
                startActivity(intent)
                finish()
                return
            }

            else -> {
                startActivity(Intent(this, MainActivity::class.java)) // fallback
                finish()
                return
            }
        }
    }
}
