package com.gmwapp.hima.utils

import android.graphics.Rect
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

/**
 * BUG 10 — keep the focused input above the soft keyboard on an edge-to-edge form.
 *
 * These screens call enableEdgeToEdge() / setDecorFitsSystemWindows(window, false),
 * which hands inset handling to the app: from that point `adjustResize` no longer
 * shrinks the content view. Their inset listeners only read Type.systemBars(), so the
 * keyboard changed nothing about the layout — the scroll container kept measuring
 * full-screen and the keypad was simply drawn on top of the field. A ScrollView only
 * scrolls a focused child into view when its OWN visible height shrinks, which is why
 * the reported screens also refused to scroll.
 *
 * Reading Type.ime() too fixes both halves: the container really does shrink, and the
 * field is then reachable. Same shape as the RatingActivity BUG #25 fix.
 *
 * max() rather than a sum: with the keyboard up ime.bottom already includes the
 * nav-bar height, so adding them would double-count. Keyboard closed ime.bottom is 0,
 * so the padding collapses to exactly the previous values and the layout is unchanged.
 *
 * Returns the insets rather than CONSUMED, matching what these listeners did before.
 *
 * @param rootView the view that was padded by the listener being replaced (R.id.main)
 */
fun AppCompatActivity.applyImeAwareInsets(rootView: View) {
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
        // Padding shrinks the scroll container, but it does NOT re-scroll a field that
        // already had focus before the keyboard appeared — the field is mid-form on
        // these screens, so ask for it explicitly instead of relying on luck.
        if (ime.bottom > 0) {
            v.post {
                currentFocus?.let { f ->
                    f.requestRectangleOnScreen(Rect(0, 0, f.width, f.height), false)
                }
            }
        }
        insets
    }
}
