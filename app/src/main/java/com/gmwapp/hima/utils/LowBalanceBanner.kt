package com.gmwapp.hima.utils

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.retrofit.responses.CoinsResponseData
import com.gmwapp.hima.viewmodels.WalletViewModel

/**
 * I021 — in-call low-balance warning banner.
 *
 * Surfaces in the male call screens when the countdown timer drops below 60s,
 * letting the user one-tap recharge mid-call. The Agora session stays live
 * via [com.gmwapp.hima.agora.services.CallingService] (foreground service of
 * type MICROPHONE) while the user is in WalletActivity / payment sheet, so
 * audio never cuts during the recharge.
 *
 * Lifecycle:
 *  - construct in `onCreate` once the layout is inflated and the userId is
 *    known
 *  - call [prefetch] in `onUserJoined` so the package catalog is loaded by
 *    the time we hit the 60s threshold
 *  - call [maybeShow] every `CountDownTimer.onTick`
 *  - call [hide] in `onResume` after a successful return from the wallet
 *    (so the next tick re-evaluates against the freshly-extended timer)
 *
 * Idempotent: repeated [maybeShow] calls while shown are no-ops; repeated
 * [prefetch] calls reuse the in-memory list.
 */
class LowBalanceBanner(
    private val activity: AppCompatActivity,
    private val rootView: View,
    private val chipContainer: ViewGroup,
    private val goToWalletTextView: TextView,
    private val walletViewModel: WalletViewModel,
    private val userId: Int,
    /**
     * Called right before we `startActivity(WalletActivity)` so the host
     * activity can flip its `pendingWalletReturn` flag and refresh the
     * remaining-time on `onResume`.
     */
    private val onLaunchedWallet: () -> Unit
) {

    private var packages: List<CoinsResponseData> = emptyList()
    private var shown: Boolean = false
    private var chipsPopulated: Boolean = false

    init {
        // Wire the always-on "Go to wallet" link. Banner root visibility
        // controls whether the user can actually see/tap it.
        goToWalletTextView.setOnClickListener { launchWallet(preSelectedCoinId = null) }
    }

    /**
     * Fetch the catalog (first 3 packages) and prepare the chip row. Safe
     * to call from `onUserJoined`. The observer is registered once per
     * activity lifetime — if the call goes through multiple
     * audio↔video switches we don't double-subscribe because we own the
     * single host activity instance.
     */
    fun prefetch() {
        if (packages.isNotEmpty()) return
        walletViewModel.coinsLiveData.observe(activity) { response ->
            if (response == null || !response.success) return@observe
            val list = response.data?.take(3).orEmpty()
            if (list.isEmpty()) return@observe
            packages = list
            populateChips()
        }
        walletViewModel.getCoins(userId)
    }

    /**
     * Called every CountDownTimer tick. The first sub-60s tick reveals the
     * banner; subsequent sub-60s ticks are no-ops thanks to the [shown]
     * guard.
     */
    fun maybeShow(millisRemaining: Long) {
        if (shown) return
        if (millisRemaining >= THRESHOLD_MS) return
        if (packages.isEmpty()) {
            // Catalog hasn't arrived yet. Show the banner anyway with just
            // the "Go to wallet" link — chips will populate the moment the
            // observer fires (usually within a second or two).
            Log.d(TAG, "Showing banner before catalog arrived; chips will fill in async")
        }
        shown = true
        rootView.alpha = 0f
        rootView.visibility = View.VISIBLE
        rootView.bringToFront()
        rootView.animate().alpha(1f).setDuration(FADE_MS).start()
    }

    fun hide() {
        if (!shown) return
        shown = false
        rootView.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            rootView.visibility = View.GONE
            rootView.alpha = 1f  // reset for the next reveal
        }.start()
    }

    /**
     * Inflate three [R.layout.chip_recharge_package] children into the
     * horizontal chip container and bind each one to the matching package.
     * Re-entry safe: the [chipsPopulated] guard prevents duplicate inflation
     * if `prefetch()`'s observer fires more than once.
     */
    private fun populateChips() {
        if (chipsPopulated) {
            // Just rebind existing children to new data.
            packages.forEachIndexed { i, pkg -> bindChip(i, pkg) }
            return
        }
        chipContainer.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        packages.forEachIndexed { i, pkg ->
            val chip = inflater.inflate(R.layout.chip_recharge_package, chipContainer, false) as CardView
            chipContainer.addView(chip)
            bindChip(i, pkg)
        }
        chipsPopulated = true
    }

    private fun bindChip(index: Int, pkg: CoinsResponseData) {
        val chip = chipContainer.getChildAt(index) as? CardView ?: return
        val priceView = chip.findViewById<TextView>(R.id.tv_chip_price)
        val coinsView = chip.findViewById<TextView>(R.id.tv_chip_coins)
        priceView.text = activity.getString(R.string.low_balance_price, pkg.price)
        coinsView.text = activity.getString(R.string.low_balance_coins, pkg.coins)
        chip.setOnClickListener { launchWallet(preSelectedCoinId = pkg.id) }
    }

    private fun launchWallet(preSelectedCoinId: Int?) {
        onLaunchedWallet()
        val intent = Intent(activity, WalletActivity::class.java)
        if (preSelectedCoinId != null) {
            intent.putExtra(WalletActivity.EXTRA_PRE_SELECTED_COIN_ID, preSelectedCoinId)
        }
        activity.startActivity(intent)
    }

    companion object {
        private const val TAG = "LowBalanceBanner"
        private const val THRESHOLD_MS = 60_000L
        private const val FADE_MS = 220L
    }
}
