package com.gmwapp.hima.utils

import android.app.Activity
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.gmwapp.hima.R

/**
 * Themed feedback when a user taps a disabled audio/video call control
 * (creator has turned off that call type).
 */
object CallUnavailableFeedback {

    fun show(activity: Activity, anchor: View, forAudio: Boolean) {
        val message = activity.getString(
            if (forAudio) R.string.peer_unavailable_audio else R.string.peer_unavailable_video
        )
        val snackbar = Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT)
        snackbar.setBackgroundTint(ContextCompat.getColor(activity, R.color.pink_bold))
        snackbar.setTextColor(ContextCompat.getColor(activity, R.color.white))
        snackbar.animationMode = Snackbar.ANIMATION_MODE_FADE
        snackbar.show()
    }

    fun showBlocked(activity: Activity, anchor: View) {
        val snackbar = Snackbar.make(
            anchor,
            activity.getString(R.string.peer_calls_blocked),
            Snackbar.LENGTH_SHORT
        )
        snackbar.setBackgroundTint(ContextCompat.getColor(activity, R.color.pink_bold))
        snackbar.setTextColor(ContextCompat.getColor(activity, R.color.white))
        snackbar.animationMode = Snackbar.ANIMATION_MODE_FADE
        snackbar.show()
    }
}
