package com.gmwapp.hima.utils

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

/**
 * Compact, bottom-aligned toasts to avoid oversized system OEM toasts and reduce overlap with cards.
 */
fun Context.showAppToast(message: CharSequence?, duration: Int = Toast.LENGTH_SHORT) {
    val text = message?.toString()?.trim().orEmpty()
    if (text.isEmpty()) return

    Toast.makeText(this, text, duration).show()
}

fun Context.showAppToast(@StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT) {
    showAppToast(getString(resId), duration)
}

fun Fragment.showAppToast(message: CharSequence?, duration: Int = Toast.LENGTH_SHORT) {
    requireContext().showAppToast(message, duration)
}

fun Fragment.showAppToast(@StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT) {
    requireContext().showAppToast(resId, duration)
}
