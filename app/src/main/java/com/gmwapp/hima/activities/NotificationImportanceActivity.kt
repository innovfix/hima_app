package com.gmwapp.hima.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityNotificationImportanceBinding
import com.gmwapp.hima.utils.applySystemBarInsets
import com.gmwapp.hima.utils.setOnSingleClickListener

/**
 * Shown after the user denies [Manifest.permission.POST_NOTIFICATIONS] from MainActivity.
 * Explains value of notifications and offers a smart path: retry OS dialog or app notification settings.
 */
class NotificationImportanceActivity : BaseActivity() {

    private lateinit var binding: ActivityNotificationImportanceBinding

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // When the user taps Allow, permission is applied before this callback; [onResume] shows toast and finishes.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationImportanceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, R.color.white)
        initUi()
    }

    private fun initUi() {
        binding.ivBack.setOnSingleClickListener { finish() }
        binding.btnMaybeLater.setOnSingleClickListener { finish() }
        binding.btnEnable.setOnSingleClickListener { onEnableClicked() }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        if (hasNotificationPermission()) {
            Toast.makeText(this, R.string.ni_enabled_toast, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun onEnableClicked() {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> openAppNotificationSettings()
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> finish()

            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ->
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            else -> openAppNotificationSettings()
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
    }

    private fun openAppNotificationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "ACTION_APP_NOTIFICATION_SETTINGS failed: ${e.message}")
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            } catch (e2: Exception) {
                Log.e(TAG, "ACTION_APPLICATION_DETAILS_SETTINGS failed: ${e2.message}")
            }
        }
    }

    companion object {
        private const val TAG = "NotificationImportance"
    }
}
