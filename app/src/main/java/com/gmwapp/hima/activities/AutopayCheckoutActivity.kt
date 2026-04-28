package com.gmwapp.hima.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.utils.SubscriptionStateCache
import com.gmwapp.hima.viewmodels.AutopayViewModel
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint

/**
 * Triggers /autopay_initiate, opens the Cashfree mandate URL in the
 * user's browser, and polls /subscription_status on resume to detect
 * when the mandate completes.
 *
 * The actual UPI authorisation happens in Cashfree's hosted page;
 * we just bridge the API call and the URL hand-off here.
 *
 * Caller must pass EXTRA_PLAN_TYPE = "trial_new" | "direct_old".
 */
@AndroidEntryPoint
class AutopayCheckoutActivity : AppCompatActivity() {

    private val autopayViewModel: AutopayViewModel by viewModels()

    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var btnClose: MaterialButton

    private var planType: String = PLAN_TRIAL_NEW
    private var redirectAttempted: Boolean = false
    private var checkoutComplete: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_autopay_checkout)

        progress = findViewById(R.id.progress)
        status = findViewById(R.id.tv_status)
        btnClose = findViewById(R.id.btn_close)
        btnClose.setOnClickListener { finish() }

        planType = intent.getStringExtra(EXTRA_PLAN_TYPE) ?: PLAN_TRIAL_NEW

        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
        if (userId == null) {
            failWithMessage("Session expired. Please log in again.")
            return
        }

        // Initiate response → open redirect URL (once).
        autopayViewModel.initiateLiveData.observe(this) { resp ->
            if (resp == null || redirectAttempted) return@observe
            if (!resp.success || resp.data?.redirect_url.isNullOrEmpty()) {
                failWithMessage(resp.message ?: "Could not start autopay.")
                return@observe
            }
            redirectAttempted = true
            status.text = "Opening UPI mandate…"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resp.data!!.redirect_url)))
            } catch (e: Exception) {
                failWithMessage("No app available to open the payment page.")
            }
        }

        // Status re-fetch (called from onResume after browser return) →
        // close on success.
        autopayViewModel.statusLiveData.observe(this) { resp ->
            val data = resp?.data ?: return@observe
            SubscriptionStateCache.update(data)
            if (data.is_active && !checkoutComplete) {
                checkoutComplete = true
                Toast.makeText(this, "Autopay active. Enjoy!", Toast.LENGTH_SHORT).show()
                finish()
            } else if (redirectAttempted && !data.is_active) {
                // User came back without completing — show manual close.
                status.text = "Mandate not yet active. You can close this and try again later."
                progress.visibility = View.GONE
                btnClose.visibility = View.VISIBLE
            }
        }

        autopayViewModel.errorLiveData.observe(this) { msg ->
            failWithMessage(msg ?: "Network error.")
        }

        // Kick off.
        autopayViewModel.autopayInitiate(userId, planType)
    }

    override fun onResume() {
        super.onResume()
        // After returning from the Cashfree page (or any backgrounding),
        // re-check status. The webhook should have already flipped the
        // server-side state by the time the user is back.
        if (redirectAttempted && !checkoutComplete) {
            val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
            status.text = "Checking mandate status…"
            progress.visibility = View.VISIBLE
            btnClose.visibility = View.GONE
            autopayViewModel.subscriptionStatus(userId)
        }
    }

    private fun failWithMessage(msg: String) {
        progress.visibility = View.GONE
        status.text = msg
        btnClose.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_PLAN_TYPE = "extra_plan_type"
        const val PLAN_TRIAL_NEW = "trial_new"
        const val PLAN_DIRECT_OLD = "direct_old"

        @JvmStatic
        fun intentFor(ctx: android.content.Context, planType: String): Intent =
            Intent(ctx, AutopayCheckoutActivity::class.java)
                .putExtra(EXTRA_PLAN_TYPE, planType)
    }
}
