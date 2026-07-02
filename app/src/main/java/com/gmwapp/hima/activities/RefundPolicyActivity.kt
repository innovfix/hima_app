package com.gmwapp.hima.activities

import com.gmwapp.hima.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Native, card-based Refund & Cancellation screen. Content mirrors
 * web.himaapp.in/refund.html.
 */
@AndroidEntryPoint
class RefundPolicyActivity : BasePolicyActivity() {

    override fun config() = PolicyConfig(
        toolbarTitle = getString(R.string.refund_and_cancellation),
        hero = Hero(
            iconRes = R.drawable.ic_refund_modern,
            title = "Refund & Cancellation",
            subtitle = "How refunds and cancellations work on Hi ma — eligibility, timelines and what is non-refundable.",
        ),
        sections = listOf(
            Section(
                R.drawable.ic_refund_modern,
                "1. Eligibility for Refund",
                listOf(
                    Block.Bullet("Refunds are applicable only for payments made towards premium features or subscriptions."),
                    Block.Bullet("Requests for refunds must be made within 7 days of the transaction date."),
                    Block.Bullet("Users must provide valid reasons for requesting a refund."),
                ),
            ),
            Section(
                R.drawable.ic_close_circle,
                "2. Non-Refundable Items",
                listOf(
                    Block.Bullet("One-time service fees and administrative charges."),
                    Block.Bullet("Payments made for promotional or discounted offers."),
                    Block.Bullet("Services that have already been utilized."),
                ),
                accentBg = "#FFEBEE",
                accentTint = "#D32F2F",
            ),
            Section(
                R.drawable.ic_check_circle,
                "3. Refund Process",
                listOf(
                    Block.Bullet("To request a refund, users must contact customer support at Himaapp123@gmail.com."),
                    Block.Bullet("Refunds, if approved, will be credited within 10 business days."),
                    Block.Bullet("Refunds will be credited to the original payment method."),
                ),
                accentBg = "#E8F5E9",
                accentTint = "#2E7D32",
            ),
            Section(
                R.drawable.ic_info,
                "4. Changes to Refund Policy",
                listOf(
                    Block.Para("We reserve the right to modify this refund policy at any time. Any changes will be updated on this page."),
                ),
            ),
        ),
        contact = Contact(
            intro = "For further assistance, please reach out to:",
            email = "himaapp000@gmail.com",
            address = "Innovfix Private Limited,\nIndiqube Ascent, Municipal No. 420, PID68-6-420,\nIV Block, Koramangala, Bangalore South,\nBangalore - 560034, Karnataka, India.",
        ),
    )
}
