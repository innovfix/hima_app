package com.gmwapp.hima.activities

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.AttachmentViewPagerAdapter
import com.gmwapp.hima.databinding.ActivityTicketDetailBinding
import com.gmwapp.hima.retrofit.responses.TicketDataResponse
import com.gmwapp.hima.utils.unescapeHelpContent
import java.text.SimpleDateFormat
import java.util.Locale
import com.gmwapp.hima.utils.applyLightSystemBars

/**
 * Read-only detail page for a single support ticket. Shows BOTH sides of the
 * conversation in one card: the user's own issue + their attachments, and the
 * support team's reply + their attachments. Opened by tapping a ticket card.
 */
class TicketDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTicketDetailBinding

    companion object {
        const val EXTRA_TICKET = "extra_ticket"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTicketDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sit below the system status bar on every phone (edge-to-edge safe):
        // white status bar with dark icons, and pad the app bar down by exactly
        // the status-bar inset so the title never overlaps the clock/battery.
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true
        applyLightSystemBars()
        val appBarBaseTop = binding.appBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, appBarBaseTop + top, v.paddingRight, v.paddingBottom)
            insets
        }

        val ticket = (intent.getSerializableExtra(EXTRA_TICKET) as? TicketDataResponse)
        if (ticket == null) {
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        render(ticket)
    }

    private fun render(ticket: TicketDataResponse) {
        binding.tvToolbarTitle.text = "Ticket #${ticket.id}"

        // Status badge
        if (ticket.status == 0) {
            binding.tvStatusBadge.text = "Active"
            binding.tvStatusBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#E91E63")
            )
        } else {
            binding.tvStatusBadge.text = "✓ Resolved"
            binding.tvStatusBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#4CAF50")
            )
        }

        // Date
        binding.tvDate.text = formatDate(ticket.created_at)

        // Topic line (category › sub-category), best-effort prettified
        val topic = buildTopic(ticket.category, ticket.sub_category)
        if (topic.isNotBlank()) {
            binding.tvTopic.text = topic
            binding.tvTopic.visibility = View.VISIBLE
        } else {
            binding.tvTopic.visibility = View.GONE
        }

        // The user's own issue text
        binding.tvMessage.text = (ticket.message ?: "").unescapeHelpContent()

        // User's own attachments
        val userAtt = ticket.screenshots ?: listOfNotNull(ticket.screenshot?.takeIf { it.isNotBlank() })
        renderThumbs(binding.llUserThumbs, binding.tvUserAttLabel, userAtt, "📎 You attached")

        // Support reply
        val reply = ticket.reply?.takeIf { it.isNotBlank() }
        if (reply != null) {
            binding.llReplyWrap.visibility = View.VISIBLE
            binding.llNoReply.visibility = View.GONE
            binding.tvReply.text = reply.unescapeHelpContent()
            val adminAtt = ticket.reply_images ?: emptyList()
            renderThumbs(binding.llAdminThumbs, binding.tvAdminAttLabel, adminAtt, "📎 Support attached")
        } else {
            binding.llReplyWrap.visibility = View.GONE
            binding.llNoReply.visibility = View.VISIBLE
        }
    }

    /** Populate a horizontal thumbnail strip; hides both views if empty. */
    private fun renderThumbs(
        container: LinearLayout,
        label: android.widget.TextView,
        urls: List<String>,
        labelPrefix: String
    ) {
        container.removeAllViews()
        if (urls.isEmpty()) {
            container.visibility = View.GONE
            label.visibility = View.GONE
            return
        }
        label.text = "$labelPrefix ${urls.size}"
        label.visibility = View.VISIBLE
        container.visibility = View.VISIBLE

        val d = resources.displayMetrics.density
        val size = (66 * d).toInt()
        val gap = (8 * d).toInt()
        val radius = (10 * d).toInt()

        urls.forEachIndexed { index, url ->
            val iv = ImageView(this)
            val lp = LinearLayout.LayoutParams(size, size)
            if (index > 0) lp.marginStart = gap
            iv.layoutParams = lp
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(this)
                .load(url)
                .transform(RoundedCorners(radius))
                .placeholder(R.drawable.rounded_background)
                .error(R.drawable.rounded_background)
                .into(iv)
            iv.setOnClickListener { openViewer(urls, index) }
            container.addView(iv)
        }
    }

    /** Full-screen swipeable image viewer (reuses the tickets-list dialog). */
    private fun openViewer(urls: List<String>, startAt: Int) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_attachments_viewer)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val viewPager = dialog.findViewById<ViewPager2>(R.id.view_pager_attachments)
        val btnClose = dialog.findViewById<ImageView>(R.id.btn_close)
        val tvImageCount = dialog.findViewById<android.widget.TextView>(R.id.tv_image_count)

        if (viewPager != null && btnClose != null && tvImageCount != null) {
            viewPager.adapter = AttachmentViewPagerAdapter(urls)
            viewPager.setCurrentItem(startAt, false)
            tvImageCount.text = "${startAt + 1} / ${urls.size}"
            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    tvImageCount.text = "${position + 1} / ${urls.size}"
                }
            })
            btnClose.setOnClickListener { dialog.dismiss() }
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            dialog.show()
        }
    }

    private fun formatDate(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val inFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            inFmt.parse(raw)?.let { outFmt.format(it) } ?: raw
        } catch (e: Exception) {
            raw
        }
    }

    /** "coins.deducted_no_connect" / "Coins & Recharge" → "Coins & Recharge › Deducted no connect". */
    private fun buildTopic(category: String?, subCategory: String?): String {
        val cat = prettify(category)
        val sub = prettify(subCategory?.substringAfterLast('.'))
        return when {
            cat.isNotBlank() && sub.isNotBlank() && !sub.equals(cat, true) -> "$cat › $sub"
            cat.isNotBlank() -> cat
            else -> sub
        }
    }

    private fun prettify(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val cleaned = s.replace('_', ' ').replace('.', ' ').trim()
        if (cleaned.isEmpty()) return ""
        return cleaned.split(' ').joinToString(" ") { w ->
            if (w.isEmpty()) w else w.replaceFirstChar { it.uppercase() }
        }
    }
}
