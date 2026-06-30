package com.gmwapp.hima.utils

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import java.util.WeakHashMap

/**
 * Animates a coin-balance TextView from the number it currently shows to a new
 * value. Counts UP on app open (starts from 0 when the view is empty), UP after
 * a purchase/daily-claim, and DOWN when the new balance is lower (e.g. after a
 * call deducts coins).
 *
 * Must be called on the UI thread. Duplicate calls with the same target are
 * ignored, so the many balance-refresh callers (onResume, getUsers observer,
 * cache refresh) don't restart the tick every time the value is unchanged.
 */
object CoinAnimUtil {

    private val running = WeakHashMap<TextView, ValueAnimator>()
    private val target = WeakHashMap<TextView, Int>()

    fun animateTo(tv: TextView, newValue: Int, duration: Long = 800L) {
        // Already showing/heading to this value — nothing to do.
        if (target[tv] == newValue) return
        target[tv] = newValue

        // Current on-screen number; empty/non-numeric -> 0 (gives count-up from 0 on first load).
        val from = tv.text?.toString()?.filter { it.isDigit() }?.toIntOrNull() ?: 0

        running.remove(tv)?.cancel()

        if (from == newValue) {
            tv.text = newValue.toString()
            return
        }

        val anim = ValueAnimator.ofInt(from, newValue).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { tv.text = (it.animatedValue as Int).toString() }
        }
        running[tv] = anim
        anim.start()
    }
}
