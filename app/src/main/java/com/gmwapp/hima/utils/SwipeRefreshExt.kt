package com.gmwapp.hima.utils

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.gmwapp.hima.R

/**
 * BUG 28 — one brand colour for every pull-to-refresh spinner in the app.
 *
 * Eight screens have pull-to-refresh and only four ever set a colour, so Home,
 * Recent, Friends and Favourite fell back to the SwipeRefreshLayout SDK default
 * and rendered near-black. The default would normally inherit the theme's
 * colorPrimary, but AppTheme deliberately does not set it (see styles.xml — it is
 * left unset so buttons, tabs and the bottom nav keep their own explicit colours),
 * so there is nothing for the spinner to inherit.
 *
 * Of the four that DID set a colour, they disagreed: Transactions used
 * R.color.pink (#FF1381), Female Home used R.color.colorAccent (#ff1383) — two hex
 * digits apart, invisible on screen but a second source of truth — and IPL Rooms
 * used its own orange/green pair.
 *
 * Call this instead of setColorSchemeColors/Resources anywhere. Adding the colour at
 * each call site would fix today's report and guarantee the next new screen forgets
 * again; a single helper makes the correct thing the easy thing.
 */
fun SwipeRefreshLayout.applyHimaRefreshColors() {
    setColorSchemeResources(R.color.pink)
}
