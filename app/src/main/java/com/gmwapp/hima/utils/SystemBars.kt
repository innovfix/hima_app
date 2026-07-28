package com.gmwapp.hima.utils

import android.app.Activity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gmwapp.hima.R

/**
 * BUG 29 — the only place in the app that decides a system bar colour.
 *
 * Before this, 46 call sites set `window.statusBarColor` / `navigationBarColor` with 16
 * different expressions: white written three ways (ContextCompat.getColor,
 * resources.getColor, Color.parseColor("#ffffff")), four near-identical darks
 * (Color.BLACK, R.color.black, "#0D0D10", "#070E1A"), plus pink, dark_blue,
 * grey_extra_light, colorAccent and transparent. Nothing said which a new screen should
 * pick, so every new screen guessed.
 *
 * Two rules, and every screen is one or the other:
 *
 *  - [applyLightSystemBars]     white bars, dark icons — every ordinary screen.
 *  - [applyImmersiveSystemBars] black bars, light icons — anything full-bleed and dark:
 *                               the four call screens, call-connecting, call-accept,
 *                               random-call, Rating, IPL Room, fullscreen image.
 *
 * Both set the navigation bar as well as the status bar. Several screens used to colour
 * only one of the two, which is how you ended up with a white status bar above a pink
 * navigation bar from the theme default.
 *
 * These are no-ops on Android 15 unless the activity's theme carries
 * `windowOptOutEdgeToEdgeEnforcement` — see AppTheme in styles.xml. Both halves are
 * required for the bars to match across OS versions; neither works alone.
 */

private fun Activity.applySystemBars(colorRes: Int, darkIcons: Boolean) {
    val color = ContextCompat.getColor(this, colorRes)
    window.statusBarColor = color
    window.navigationBarColor = color
    val root = window.decorView
    WindowInsetsControllerCompat(window, root).apply {
        isAppearanceLightStatusBars = darkIcons
        isAppearanceLightNavigationBars = darkIcons
    }
}

/** White bars with dark icons. The default for any screen that is not full-bleed dark. */
fun Activity.applyLightSystemBars() = applySystemBars(R.color.white, darkIcons = true)

/** Black bars with light icons. Calls, rating, IPL room, fullscreen media. */
fun Activity.applyImmersiveSystemBars() = applySystemBars(R.color.black, darkIcons = false)
