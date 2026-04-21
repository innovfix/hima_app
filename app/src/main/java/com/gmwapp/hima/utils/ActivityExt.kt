package com.gmwapp.hima.utils

import android.view.View
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gmwapp.hima.R

/**
 * Apply system bar insets as padding to [rootView] and paint the status bar.
 *
 * Needed because AppTheme sets windowIsTranslucent=true, which makes Android
 * ignore the XML statusBarColor attribute AND break fitsSystemWindows cascading
 * through FrameLayout/ViewFlipper roots.
 *
 * Call from onCreate AFTER setContentView.
 *
 * @param rootView       top-level view to pad (usually binding.root)
 * @param statusBarColor color resource for the status bar (match the header)
 * @param applyBottom    pad bottom for nav/gesture bar (default true)
 * @param applyIme       also pad for the soft keyboard (use on chat/input screens)
 */
fun AppCompatActivity.applySystemBarInsets(
    rootView: View,
    @ColorRes statusBarColor: Int = R.color.colorAccent,
    applyBottom: Boolean = true,
    applyIme: Boolean = false,
    darkStatusBarIcons: Boolean = false,
) {
    window.statusBarColor = ContextCompat.getColor(this, statusBarColor)
    WindowInsetsControllerCompat(window, rootView)
        .isAppearanceLightStatusBars = darkStatusBarIcons
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
        val typeMask = if (applyIme) {
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        } else {
            WindowInsetsCompat.Type.systemBars()
        }
        val bars = insets.getInsets(typeMask)
        v.setPadding(bars.left, bars.top, bars.right, if (applyBottom) bars.bottom else 0)
        WindowInsetsCompat.CONSUMED
    }
}
