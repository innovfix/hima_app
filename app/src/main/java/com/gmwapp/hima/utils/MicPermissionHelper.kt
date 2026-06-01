package com.gmwapp.hima.utils

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * LAI-107: central handling for the "twice-denied" mic permission state.
 *
 * On Android, after the user denies a runtime permission twice the system
 * silently auto-denies every subsequent requestPermissions() call without
 * showing a dialog. Screens that naively keep re-requesting end up in an
 * infinite no-op loop. The fix is to detect the permanently-denied state via
 * [Activity.shouldShowRequestPermissionRationale] and route the user to App
 * Settings instead.
 */
object MicPermissionHelper {

    fun hasMicPermission(activity: Activity): Boolean =
        ContextCompat.checkSelfPermission(
            activity, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Call after a denied callback from a permission launcher. Returns true if
     * the system will no longer show the system permission dialog and the user
     * must enable mic access manually from App Settings.
     */
    fun isPermanentlyDenied(activity: Activity): Boolean =
        !ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.RECORD_AUDIO
        )

    fun showSettingsDeepLinkDialog(
        activity: Activity,
        title: String = "Microphone access needed",
        message: String = "To talk in the app, please enable Microphone access for Hima in System Settings.",
        onCancel: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                runCatching {
                    activity.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", activity.packageName, null)
                        }
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel) { d, _ ->
                d.dismiss()
                onCancel?.invoke()
            }
            .show()
    }
}
