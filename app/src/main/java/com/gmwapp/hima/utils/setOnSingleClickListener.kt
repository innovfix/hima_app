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
