package com.gmwapp.hima.dialogs

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.random.Random
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.databinding.BottomSheetWelcomeBonusBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BottomSheetWelcomeBonus : BottomSheetDialogFragment() {

    interface OnAddCoinsListener {
        fun onAddCoins(coins: String, id: Int)
    }

    interface OnDismissListener {
        fun onBottomSheetDismissed()
    }

    private var _binding: BottomSheetWelcomeBonusBinding? = null
    private val binding get() = _binding!!
    private var addCoinsListener: OnAddCoinsListener? = null
    private var dismissListener: OnDismissListener? = null

    // Variables to receive from arguments
    private var coins: Int = 0
    private var originalPrice: Int = 0
    private var discountedPrice: Int = 0
    private var coinId: Int = 0
    private var totalCount: Int = 0

    companion object {
        fun newInstance(
            coins: Int,
            originalPrice: Int,
            discountedPrice: Int,
            coinId: Int,
            totalCount: Int
        ): BottomSheetWelcomeBonus {
            val fragment = BottomSheetWelcomeBonus()
            val args = Bundle().apply {
                putInt("coins", coins)
                putInt("originalPrice", originalPrice)
                putInt("discountedPrice", discountedPrice)
                putInt("coinId", coinId)
                putInt("totalCount", totalCount)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnAddCoinsListener) {
            addCoinsListener = context
        } else {
            throw RuntimeException("$context must implement OnAddCoinsListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            coins = it.getInt("coins")
            originalPrice = it.getInt("originalPrice")
            discountedPrice = it.getInt("discountedPrice")
            coinId = it.getInt("coinId")
            totalCount = it.getInt("totalCount")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetWelcomeBonusBinding.inflate(inflater, container, false)

        // Set strikethrough on original price
        binding.tvBonusOriginal.paintFlags =
            binding.tvBonusOriginal.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

        // Set UI values
        binding.tvBonusText.text = "$coins Coins"
        binding.tvBonusOriginal.text = "₹$originalPrice"
        binding.tvBonusDiscount.text = "₹$discountedPrice"
        binding.tvCtaPrice.text = "₹$discountedPrice"
        binding.tvUsedBy.text = "Used by $totalCount people in the last 30 mins"

        // Savings badge (hide if there is no real discount)
        if (originalPrice > 0 && discountedPrice < originalPrice) {
            val savePercent = Math.round((originalPrice - discountedPrice) * 100.0 / originalPrice)
            binding.tvDiscountBadge.text = "SAVE $savePercent%"
            binding.tvDiscountBadge.visibility = View.VISIBLE
        } else {
            binding.tvDiscountBadge.visibility = View.GONE
        }

        val twoPercentage = discountedPrice.toDouble() * 0.02
        val roundedAmount = Math.round(twoPercentage)
        val totalAmount = (discountedPrice + roundedAmount).toString()

        binding.tvViewMorePlans.setOnSingleClickListener {
            startActivity(Intent(context, WalletActivity::class.java))
        }

        binding.btnAddCoins.setOnSingleClickListener {
            addCoinsListener?.onAddCoins(totalAmount, coinId)
        }

        // Subtle coin rainfall behind the content (purely decorative)
        binding.coinRain.post { startCoinRain(binding.coinRain) }
        // Festival confetti burst when the sheet opens
        binding.confettiLayer.post { playConfettiBlast(binding.confettiLayer) }

        return binding.root
    }

    /** Festive one-shot confetti burst that rains down when the sheet opens. */
    private fun playConfettiBlast(layer: FrameLayout) {
        val w = layer.width
        val h = layer.height
        if (w <= 0 || h <= 0) return
        val density = resources.displayMetrics.density
        val colors = intArrayOf(
            0xFFFF1383.toInt(), // pink
            0xFFFFC107.toInt(), // gold
            0xFF6E4EEF.toInt(), // purple
            0xFF16A34A.toInt(), // green
            0xFF29B6F6.toInt(), // blue
            0xFFFF6D00.toInt(), // orange
        )
        val pieceCount = 40
        for (i in 0 until pieceCount) {
            val pw = ((5 + Random.nextInt(5)) * density).toInt()
            val ph = ((8 + Random.nextInt(8)) * density).toInt()
            val piece = View(layer.context).apply {
                layoutParams = FrameLayout.LayoutParams(pw, ph)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(colors[i % colors.size])
                    cornerRadius = 2f * density
                }
                // Burst from just under the grabber, spread across the width.
                x = (w / 2f) + (Random.nextFloat() - 0.5f) * (w * 0.5f)
                y = (16 * density) + (Random.nextFloat() - 0.5f) * (20 * density)
                rotation = Random.nextInt(360).toFloat()
            }
            layer.addView(piece)

            val dx = (Random.nextFloat() - 0.5f) * w * 1.1f
            val dy = h * (0.55f + Random.nextFloat() * 0.5f)
            piece.animate()
                .translationXBy(dx)
                .translationYBy(dy)
                .rotationBy((Random.nextInt(720) - 360).toFloat())
                .alpha(0f)
                .setStartDelay(Random.nextInt(120).toLong())
                .setDuration((1400 + Random.nextInt(900)).toLong())
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { layer.removeView(piece) }
                .start()
        }
    }

    private val coinAnimators = mutableListOf<ObjectAnimator>()

    /**
     * Gently drifts faint coins down behind the sheet content. It sits in its
     * own back layer (coin_rain) so it never overlaps or shifts the cards —
     * the opaque cards simply cover the coins, leaving only the light gaps
     * showing a soft fall for a premium feel.
     */
    private fun startCoinRain(container: FrameLayout) {
        val w = container.width
        val h = container.height
        if (w <= 0 || h <= 0) return
        val density = resources.displayMetrics.density
        val coinCount = 14
        val topY = -(28 * density) // just above the sheet, off-screen

        for (i in 0 until coinCount) {
            val sizePx = ((16 + Random.nextInt(12)) * density).toInt() // 16–28dp
            val coin = ImageView(container.context).apply {
                setImageResource(R.drawable.coin_d)
                layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
                alpha = 0.45f + Random.nextFloat() * 0.22f // visible: 0.45–0.67
                x = Random.nextInt((w - sizePx).coerceAtLeast(1)).toFloat()
                translationY = topY // park off-screen until the animator positions it
            }
            container.addView(coin)

            val endY = h.toFloat() + sizePx
            val fallDur = (9000 + Random.nextInt(5000)).toLong() // slow: 9–14s
            val fall = ObjectAnimator.ofFloat(coin, "translationY", topY, endY).apply {
                duration = fallDur
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
            }
            val spinDur = (7000 + Random.nextInt(4000)).toLong()
            val spin = ObjectAnimator.ofFloat(
                coin, "rotation", 0f, if (i % 2 == 0) 360f else -360f,
            ).apply {
                duration = spinDur
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
            }
            fall.start()
            spin.start()
            // Seed each coin at a random point in its fall so the rain is
            // already spread across the whole sheet on the first frame — no
            // stacking at the top, no start-delay pauses, seamless looping
            // (both ends of the fall are off-screen, so the restart is unseen).
            fall.currentPlayTime = (Random.nextFloat() * fallDur).toLong()
            spin.currentPlayTime = (Random.nextFloat() * spinDur).toLong()
            coinAnimators.add(fall)
            coinAnimators.add(spin)
        }
    }

    fun setOnDismissListener(listener: OnDismissListener) {
        this.dismissListener = listener
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        android.util.Log.d("BottomSheetWelcomeBonus", "onDismiss called - dismissListener is ${if (dismissListener != null) "SET" else "NULL"}")
        // Notify listener when bottom sheet is dismissed
        dismissListener?.onBottomSheetDismissed()
    }

    override fun onDestroyView() {
        coinAnimators.forEach { it.cancel() }
        coinAnimators.clear()
        super.onDestroyView()
        _binding = null
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        // Always open fully expanded so short screens / large font scale still show the
        // whole sheet, including "View more plans" at the bottom. The NestedScrollView in
        // the layout lets the content scroll when it's taller than the available height,
        // so nothing is ever clipped and unreachable (the device-inconsistency bug).
        dialog.setOnShowListener { d ->
            val sheet = (d as BottomSheetDialog)
                .findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                // Let the sheet size to its content and open there — do NOT force
                // STATE_EXPANDED. Forcing expanded made it open at the right height
                // and then auto-grow to fill the whole screen ("opens good, then
                // expands and looks big"). wrap_content on the sheet view + fitToContents
                // keeps it content-sized; the NestedScrollView still scrolls on short
                // screens, so "View more plans" stays reachable.
                behavior.isFitToContents = true
                behavior.skipCollapsed = true
                it.layoutParams = it.layoutParams.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }
            // The sheet draws to the screen bottom (behind the nav bar), so the last row
            // ("View more plans") is covered by the navigation bar and needs a manual
            // scroll to reach — BUG #7 / #24.
            //
            // Two earlier attempts both read the inset from b.llWelcomeContent and both
            // returned 0. Material inflates every sheet into design_bottom_sheet_dialog,
            // whose top two containers (#container and #coordinator) BOTH declare
            // android:fitsSystemWindows="true" — i.e. they consume the system-window
            // insets and hand their children the leftovers. Our content sits four levels
            // below that, so getInsets(navigationBars()).bottom is always 0 there and
            // max(20dp, 0) stayed 20dp on every device. Switching a one-shot read to an
            // inset listener changed WHEN we read, not WHERE, so it changed nothing.
            //
            // Read above that chain instead: the host Activity's decorView sees the real,
            // unconsumed insets and is already laid out by the time a sheet opens, so the
            // value is correct and available immediately — no dispatch dependency at all.
            //
            // max(baseGap, navInset) — NOT baseGap + navInset — so 3-button nav clears the
            // bar without stacking an extra 20dp on top of it, and gesture nav (navInset≈0)
            // keeps the 20dp resting gap. No wasted space either way.
            _binding?.let { b ->
                val baseGap = (20 * resources.displayMetrics.density).toInt()
                val navInset = androidx.core.view.ViewCompat
                    .getRootWindowInsets(requireActivity().window.decorView)
                    ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                    ?.bottom ?: 0
                b.llWelcomeContent.setPadding(
                    b.llWelcomeContent.paddingLeft,
                    b.llWelcomeContent.paddingTop,
                    b.llWelcomeContent.paddingRight,
                    maxOf(baseGap, navInset)
                )
            }
        }
        return dialog
    }
}
