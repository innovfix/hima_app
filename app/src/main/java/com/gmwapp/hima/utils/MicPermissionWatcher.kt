package com.gmwapp.hima.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * B127 (lifecycle part) fix.
 *
 * Why: previously a call only re-checked `RECORD_AUDIO` in `onResume()`, so if the user opened
 * Settings and revoked mic permission while the call was active and visible, the call would
 * keep running with no microphone. Android delivers real-time op state changes via
 * `AppOpsManager.startWatchingMode`, which fires on the main thread whenever the OP_RECORD_AUDIO
 * mode for our package flips. We use it to immediately tear the call down.
 *
 * `startWatchingMode` works back to API 19 with `OPSTR_RECORD_AUDIO`, so no SDK gating needed.
 */
class MicPermissionWatcher(
    private val context: Context,
    private val onRevoked: () -> Unit,
) {

    private val appOps: AppOpsManager? =
        context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
    private val main = Handler(Looper.getMainLooper())
    private var listener: AppOpsManager.OnOpChangedListener? = null
    private var fired = false

    fun start() {
        val ops = appOps ?: return
        if (listener != null) return
        val l = AppOpsManager.OnOpChangedListener { op, pkg ->
            if (op != AppOpsManager.OPSTR_RECORD_AUDIO) return@OnOpChangedListener
            if (pkg != context.packageName) return@OnOpChangedListener
            main.post {
                if (fired) return@post
                val granted = ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    fired = true
                    Log.w(TAG, "RECORD_AUDIO revoked mid-call — invoking onRevoked")
                    onRevoked()
                }
            }
        }
        try {
            ops.startWatchingMode(
                AppOpsManager.OPSTR_RECORD_AUDIO,
                context.packageName,
                l,
            )
            listener = l
        } catch (e: Throwable) {
            Log.e(TAG, "startWatchingMode failed", e)
        }
    }

    fun stop() {
        val ops = appOps ?: return
        val l = listener ?: return
        try {
            ops.stopWatchingMode(l)
        } catch (e: Throwable) {
            Log.e(TAG, "stopWatchingMode failed", e)
        }
        listener = null
        main.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val TAG = "MicPermissionWatcher"
    }
}
