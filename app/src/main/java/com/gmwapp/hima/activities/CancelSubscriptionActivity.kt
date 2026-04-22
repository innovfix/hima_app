package com.gmwapp.hima.activities

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gmwapp.hima.databinding.ActivityCancelSubscriptionBinding
import com.gmwapp.hima.dialogs.BottomSheetCancelConfirm

class CancelSubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCancelSubscriptionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        super.onCreate(savedInstanceState)
        binding = ActivityCancelSubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnCancel.setOnClickListener {
            BottomSheetCancelConfirm().show(supportFragmentManager, "cancel_confirm")
        }
    }
}
