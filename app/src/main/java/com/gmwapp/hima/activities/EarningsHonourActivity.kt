package com.gmwapp.hima.activities

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gmwapp.hima.R
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallEarningsResponse
import com.gmwapp.hima.utils.GoldShimmerTextView
import com.gmwapp.hima.viewmodels.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Response
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * B1 — post-call earnings honour screen (creator/female only).
 *
 * Shows the REAL, server-credited earnings for the just-ended call, then forwards
 * to RatingActivity. All amounts come from the server (get_call_earnings); the app
 * never computes money from its own timer. If the call hasn't settled yet, it shows
 * a "finalizing" state and never a fabricated number. Auto-advances after ~7s; the
 * Continue button (or auto-advance) both do the same safe thing: go to the review.
 */
@AndroidEntryPoint
class EarningsHonourActivity : AppCompatActivity() {

    private val profileViewModel: ProfileViewModel by viewModels()

    private val handler = Handler(Looper.getMainLooper())
    private val proceeded = AtomicBoolean(false)
    private val rendered = AtomicBoolean(false)

    private lateinit var root: FrameLayout
    private lateinit var embers: FrameLayout
    private lateinit var ivCrown: ImageView
    private lateinit var tvEyebrow: TextView
    private lateinit var tvAmount: GoldShimmerTextView
    private lateinit var tvSentiment: TextView
    private lateinit var tvThanks: TextView
    private lateinit var llChips: LinearLayout
    private lateinit var tvCallLine: TextView
    private lateinit var tvBonusLine: TextView
    private lateinit var llWallet: LinearLayout
    private lateinit var tvWalletOld: TextView
    private lateinit var tvWalletNew: TextView
    private lateinit var tvVerified: TextView
    private lateinit var btnContinue: TextView

    private val autoAdvance = Runnable { proceed() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // NEVER-FAIL GUARD: if anything in setup throws, skip straight to the review so
        // the creator is never trapped on a broken screen. proceed() uses no views.
        try {
            buildScreen()
        } catch (t: Throwable) {
            android.util.Log.e("EarningsHonour", "setup failed, skipping to rating", t)
            proceed()
        }
    }

    private fun buildScreen() {
        setContentView(R.layout.activity_earnings_honour)

        root = findViewById(R.id.earningsRoot)
        embers = findViewById(R.id.embersContainer)
        ivCrown = findViewById(R.id.ivCrown)
        tvEyebrow = findViewById(R.id.tvEyebrow)
        tvAmount = findViewById(R.id.tvAmount)
        tvSentiment = findViewById(R.id.tvSentiment)
        tvThanks = findViewById(R.id.tvThanks)
        llChips = findViewById(R.id.llChips)
        tvCallLine = findViewById(R.id.tvCallLine)
        tvBonusLine = findViewById(R.id.tvBonusLine)
        llWallet = findViewById(R.id.llWallet)
        tvWalletOld = findViewById(R.id.tvWalletOld)
        tvWalletNew = findViewById(R.id.tvWalletNew)
        tvVerified = findViewById(R.id.tvVerified)
        btnContinue = findViewById(R.id.btnContinue)

        // Warm personal line: a rotating sentiment (one per call) + "Thank you, {name}".
        val firstName = (BaseApplication.getInstance()?.getPrefs()?.getUserData()?.name ?: "")
            .trim().split(" ").firstOrNull().orEmpty()
        tvThanks.text = if (firstName.isNotEmpty())
            getString(R.string.earnings_thanks, firstName) else ""
        val sentiments = resources.getStringArray(R.array.earnings_sentiments)
        // Start in the honest "finalizing" state until the server confirms the number.
        tvAmount.visibility = View.INVISIBLE
        tvSentiment.text = getString(R.string.earnings_finalizing)

        btnContinue.setOnClickListener { proceed() }

        revealChrome()
        spawnEmbers()
        fetchEarnings(sentiments)

        // Safety net: never trap her on this screen.
        handler.postDelayed(autoAdvance, AUTO_ADVANCE_MS)
    }

    /** Reveal the parts that don't depend on server data. */
    private fun revealChrome() {
        ivCrown.alpha = 0f; ivCrown.scaleX = 0.4f; ivCrown.scaleY = 0.4f
        ivCrown.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(0).setDuration(420)
            .setInterpolator(DecelerateInterpolator()).start()
        fadeUp(tvEyebrow, 180)
        fadeUp(tvSentiment, 220)
        if (tvThanks.text.isNotEmpty()) fadeUp(tvThanks, 320)
        // Button appears last (~3.2s) so the moment lands before "move on" shows.
        btnContinue.alpha = 0f
        btnContinue.visibility = View.VISIBLE
        btnContinue.animate().alpha(1f).setStartDelay(BUTTON_DELAY_MS).setDuration(360).start()
    }

    private fun fadeUp(v: View, delay: Long) {
        v.alpha = 0f
        v.translationY = 12f
        v.visibility = View.VISIBLE
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(340)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    /**
     * Settlement (WorkManager) and this screen fire at the same moment, so the first
     * fetch usually sees "not settled yet". Poll across the hold window until the server
     * confirms the credit, then render once. Every terminal state (not-settled, fail,
     * no-network, exhausted attempts) simply leaves the honest "finalizing" text and lets
     * the 7s auto-advance carry her onward — it can never block or crash the flow.
     */
    private fun fetchEarnings(sentiments: Array<String>) {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        val callId = intent.getIntExtra(DConstants.CALL_ID, 0)
        if (userId <= 0 || callId <= 0) return  // stay in finalizing state; auto-advance handles exit
        attemptFetch(userId, callId, sentiments, 0)
    }

    private fun attemptFetch(userId: Int, callId: Int, sentiments: Array<String>, attempt: Int) {
        if (rendered.get() || proceeded.get() || isFinishing) return

        profileViewModel.getCallEarnings(userId, callId, object : NetworkCallback<CallEarningsResponse> {
            override fun onResponse(call: Call<CallEarningsResponse>, response: Response<CallEarningsResponse>) {
                try {
                    val d = response.body()?.data
                    if (response.isSuccessful && d != null && d.settled && d.total != null) {
                        showEarnings(d.call_income ?: 0.0, d.bonus ?: 0.0, d.total,
                            d.balance_before, d.balance_after, sentiments)
                    } else {
                        retry(userId, callId, sentiments, attempt)  // not settled yet — poll again
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("EarningsHonour", "render earnings failed", t)
                    // stay in finalizing state; auto-advance still carries her onward
                }
            }

            override fun onFailure(call: Call<CallEarningsResponse>, t: Throwable) {
                retry(userId, callId, sentiments, attempt)
            }

            override fun onNoNetwork() { /* offline — keep finalizing, no point polling */ }
        })
    }

    private fun retry(userId: Int, callId: Int, sentiments: Array<String>, attempt: Int) {
        if (attempt + 1 >= MAX_FETCH_ATTEMPTS || rendered.get() || proceeded.get() || isFinishing) return
        handler.postDelayed({ attemptFetch(userId, callId, sentiments, attempt + 1) }, POLL_INTERVAL_MS)
    }

    private fun showEarnings(
        income: Double, bonus: Double, total: Double,
        balBefore: Double?, balAfter: Double?, sentiments: Array<String>
    ) {
        if (isFinishing || proceeded.get()) return
        if (!rendered.compareAndSet(false, true)) return   // render exactly once
        // Swap the finalizing placeholder for a warm line.
        tvSentiment.text = sentiments[Random.nextInt(sentiments.size)]

        // Count-up the gold amount.
        tvAmount.visibility = View.VISIBLE
        tvAmount.alpha = 0f
        tvAmount.animate().alpha(1f).setDuration(260).start()
        tvAmount.startShimmer()
        ValueAnimator.ofFloat(0f, total.toFloat()).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            addUpdateListener { tvAmount.text = rupee((it.animatedValue as Float).toDouble()) }
            start()
        }

        // Breakdown — bonus chip only when there is a bonus.
        tvCallLine.text = "${getString(R.string.earnings_call_label)} ${rupee(income)}"
        if (bonus > 0.0) {
            tvBonusLine.text = "${getString(R.string.earnings_bonus_label)} +${rupee(bonus)}"
            tvBonusLine.visibility = View.VISIBLE
        } else {
            tvBonusLine.visibility = View.GONE
            (tvBonusLine.parent as? LinearLayout)?.getChildAt(1)?.visibility = View.GONE // hide divider
        }
        fadeUp(llChips, 320)

        // Wallet jump (only if the server gave us both balances).
        if (balBefore != null && balAfter != null) {
            tvWalletOld.text = rupee(balBefore)
            tvWalletOld.paintFlags = tvWalletOld.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            tvWalletNew.text = rupee(balAfter)
            fadeUp(llWallet, 480)
        }
        fadeUp(tvVerified, 620)
    }

    /** ₹60 for whole amounts, ₹60.50 otherwise, with thousands grouping. */
    private fun rupee(amt: Double): String {
        return if (amt == Math.floor(amt))
            "₹" + String.format("%,d", amt.toLong())
        else "₹" + String.format("%,.2f", amt)
    }

    private fun spawnEmbers() {
        val h = resources.displayMetrics.heightPixels.toFloat()
        val w = resources.displayMetrics.widthPixels
        val size = (4 * resources.displayMetrics.density).toInt()
        repeat(10) { i ->
            val e = ImageView(this)
            e.setImageResource(R.drawable.earnings_ember)
            val lp = FrameLayout.LayoutParams(size, size)
            lp.leftMargin = Random.nextInt(w - size)
            lp.topMargin = h.toInt()
            e.layoutParams = lp
            e.alpha = 0f
            embers.addView(e)
            e.animate().alpha(0.9f).translationY(-h * 0.75f)
                .setStartDelay((i * 750L))
                .setDuration(6500)
                .withEndAction { loopEmber(e, h) }
                .start()
        }
    }

    private fun loopEmber(e: View, h: Float) {
        if (isFinishing) return
        e.translationY = 0f
        e.alpha = 0f
        e.animate().alpha(0.9f).translationY(-h * 0.75f)
            .setStartDelay(Random.nextLong(0, 3000))
            .setDuration(6500)
            .withEndAction { loopEmber(e, h) }
            .start()
    }

    @Suppress("DEPRECATION")
    private fun proceed() {
        if (!proceeded.compareAndSet(false, true)) return
        handler.removeCallbacksAndMessages(null)   // cancel auto-advance + any pending polls
        val next = Intent(this, RatingActivity::class.java)
        intent.extras?.let { next.putExtras(it) }   // forward all original call extras
        startActivity(next)
        overridePendingTransition(0, 0)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::tvAmount.isInitialized) tvAmount.stopShimmer()
        super.onDestroy()
    }

    // Block accidental back-dismiss into limbo — route it through the same safe path.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() { proceed() }

    companion object {
        private const val AUTO_ADVANCE_MS = 7000L
        private const val BUTTON_DELAY_MS = 3000L
        // Poll for the settled credit across the hold window (settlement races this screen).
        private const val MAX_FETCH_ATTEMPTS = 5
        private const val POLL_INTERVAL_MS = 1300L
    }
}
