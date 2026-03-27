package com.gmwapp.hima.utils

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.gmwapp.hima.R

/**
 * Compact, bottom-aligned toasts to avoid oversized system OEM toasts and reduce overlap with cards.
 */
fun Context.showAppToast(message: CharSequence?, duration: Int = Toast.LENGTH_SHORT) {
    val text = message?.toString()?.trim().orEmpty()
    if (text.isEmpty()) return

    val inflater = LayoutInflater.from(this)
    val view = inflater.inflate(R.layout.view_toast_compact, null)
    view.findViewById<TextView>(R.id.tv_toast_message).text = text

    val toast = Toast(this)
    toast.duration = duration
    @Suppress("DEPRECATION")
    toast.view = view

    val yOffset = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        96f,
        resources.displayMetrics
    ).toInt()
    toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, yOffset)
    toast.show()
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
