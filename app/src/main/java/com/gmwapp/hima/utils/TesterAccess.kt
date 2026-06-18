package com.gmwapp.hima.utils

import android.content.Context
import android.os.SystemClock
import android.widget.Toast

/**
 * 7-tap unlock on the version label activates debug mode for the session.
 * On unlock: starts continuous logcat capture. Long-press version to share.
 * Normal users never reach 7 taps — no background process runs for them.
 */
object TesterAccess {

    private const val TAPS_REQUIRED = 7
    private const val TAP_RESET_MS = 2000L

    @Volatile private var tapCount = 0
    @Volatile private var lastTapTime = 0L
    @Volatile private var unlocked = false

    fun onVersionTap(context: Context) {
        if (unlocked) {
            Toast.makeText(context, "Debug mode already active — hold version to share logs", Toast.LENGTH_SHORT).show()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastTapTime > TAP_RESET_MS) tapCount = 0
        lastTapTime = now
        tapCount++

        when {
            tapCount >= TAPS_REQUIRED -> {
                unlocked = true
                tapCount = 0
                LogCapture.startSessionLog(context)
                Toast.makeText(context, "Debug mode unlocked — logging started. Hold version to share logs.", Toast.LENGTH_LONG).show()
            }
            tapCount >= 4 -> {
                val remaining = TAPS_REQUIRED - tapCount
                Toast.makeText(context, "$remaining more taps to unlock debug mode", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun canUseDebugTools(): Boolean = unlocked
}
