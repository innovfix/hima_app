package com.gmwapp.hima.utils

import android.os.SystemClock
import android.view.View

// 500 ms is right for activity-launch / API-call buttons (prevents double-fire
// double-charging). Too long for pure-UI toggles like the Home FAB — users
// naturally tap rapidly to expand-then-collapse and the second tap was getting
// swallowed (B066: "Random button doesn't trigger sometimes"). Callers that
// just toggle UI state can use the (debounceMs, onSingleClick) overload below.
private const val DEFAULT_DEBOUNCE_TIME = 500L

fun View.setOnSingleClickListener(onSingleClick: (View) -> Unit) =
    setOnSingleClickListener(DEFAULT_DEBOUNCE_TIME, onSingleClick)

fun View.setOnSingleClickListener(debounceMs: Long, onSingleClick: (View) -> Unit) {
    var lastClickTime = 0L
    this.setOnClickListener { view ->
        if (SystemClock.elapsedRealtime() - lastClickTime >= debounceMs) {
            lastClickTime = SystemClock.elapsedRealtime()
            onSingleClick(view)
        }
    }
}

/**
 * Fires [onHold] once the view is pressed and held continuously for [holdMs].
 * Lifting the finger before the time is up cancels it. Used for the hidden
 * "Share logs" gesture on the version label after debug mode is unlocked.
 */
@android.annotation.SuppressLint("ClickableViewAccessibility")
fun View.setOnHold(holdMs: Long = 5000L, onHold: () -> Unit) {
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    val fire = Runnable { onHold() }
    isClickable = true
    isLongClickable = true
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                v.parent?.requestDisallowInterceptTouchEvent(true)
                handler.removeCallbacks(fire)
                handler.postDelayed(fire, holdMs)
                true
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                v.parent?.requestDisallowInterceptTouchEvent(false)
                handler.removeCallbacks(fire)
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) v.performClick()
                true
            }
            else -> true
        }
    }
}
