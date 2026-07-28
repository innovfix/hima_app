package com.gmwapp.hima.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityPrivacyPolicyBinding
import com.gmwapp.hima.databinding.ItemPolicySectionBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.applyLightSystemBars

/**
 * Shared scaffold for the native, card-based policy screens
 * (Privacy Policy, Terms & Conditions, Refund & Cancellation, Community
 * Guidelines). Each subclass only supplies its content via [config]; this
 * base class handles the toolbar, gradient hero, colour-coded section cards
 * and the contact card so every policy screen looks and behaves the same.
 */
abstract class BasePolicyActivity : BaseActivity() {

    // ---- Content model shared by every policy screen ----

    sealed class Block {
        data class Para(val text: String) : Block()
        data class SubHead(val text: String) : Block()
        data class Bullet(val text: String) : Block()
    }

    /**
     * A single policy section. [accentBg]/[accentTint] override the rotating
     * palette when a specific colour is wanted (e.g. red for "not allowed").
     */
    data class Section(
        val iconRes: Int,
        val title: String,
        val blocks: List<Block>,
        val accentBg: String? = null,
        val accentTint: String? = null,
    )

    data class Hero(
        val iconRes: Int,
        val title: String,
        val subtitle: String,
        val badge: String? = null,
    )

    data class Contact(
        val intro: String,
        val email: String,
        val address: String,
    )

    data class PolicyConfig(
        val toolbarTitle: String,
        val hero: Hero,
        val sections: List<Section>,
        val contact: Contact?,
    )

    /** Subclasses provide their content here. */
    abstract fun config(): PolicyConfig

    private lateinit var binding: ActivityPrivacyPolicyBinding

    // Pastel (background, icon-tint) pairs rotated per card.
    private val palette = listOf(
        "#E3F2FD" to "#1976D2",
        "#FCE4EC" to "#FF1383",
        "#EDE7F6" to "#6E4EEF",
        "#E8F5E9" to "#2E7D32",
        "#FFF3E0" to "#EF6C00",
        "#E0F2F1" to "#00897B",
    )

    private val brand = Color.parseColor("#FF1383")
    private val bodyColor = Color.parseColor("#5C5C66")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPrivacyPolicyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true
        applyLightSystemBars()
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val cfg = config()
        binding.includeProfileToolbar.tvFlowTitle.text = cfg.toolbarTitle
        binding.includeProfileToolbar.cvBack.setOnSingleClickListener { finish() }
        buildScreen(cfg)
    }

    private fun buildScreen(cfg: PolicyConfig) {
        val root = binding.llSections
        val inflater = LayoutInflater.from(this)

        // Hero
        val hero = inflater.inflate(R.layout.layout_policy_hero, root, false)
        hero.findViewById<android.widget.ImageView>(R.id.iv_hero_icon)
            .setImageResource(cfg.hero.iconRes)
        hero.findViewById<TextView>(R.id.tv_hero_title).text = cfg.hero.title
        hero.findViewById<TextView>(R.id.tv_hero_subtitle).text = cfg.hero.subtitle
        val badge = hero.findViewById<TextView>(R.id.tv_hero_badge)
        if (cfg.hero.badge.isNullOrBlank()) {
            badge.visibility = View.GONE
        } else {
            badge.text = cfg.hero.badge
        }
        root.addView(hero)

        // Single clean container card that holds every section + contact as
        // rows, separated by hairline dividers (matches the "Settings &
        // Support" card on the profile screen).
        val card = inflater.inflate(R.layout.layout_policy_card_container, root, false)
        val body = card.findViewById<LinearLayout>(R.id.ll_card_body)

        val blocks = ArrayList<View>()
        cfg.sections.forEachIndexed { index, section ->
            blocks.add(buildCard(inflater, body, section, index))
        }
        cfg.contact?.let { blocks.add(buildContactCard(inflater, body, it)) }

        blocks.forEachIndexed { i, block ->
            if (i > 0) body.addView(divider())
            body.addView(block)
        }
        root.addView(card)
    }

    private fun divider(): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1),
            )
            setBackgroundColor(Color.parseColor("#F0F0F0"))
        }

    private fun buildCard(
        inflater: LayoutInflater,
        parent: ViewGroup,
        section: Section,
        index: Int,
    ): View {
        val item = ItemPolicySectionBinding.inflate(inflater, parent, false)
        val (paletteBg, paletteTint) = palette[index % palette.size]
        val bg = section.accentBg ?: paletteBg
        val tint = section.accentTint ?: paletteTint

        item.iconBg.backgroundTintList = ColorStateList.valueOf(Color.parseColor(bg))
        item.ivIcon.setImageResource(section.iconRes)
        item.ivIcon.setColorFilter(Color.parseColor(tint))
        item.tvTitle.text = section.title

        val regular = ResourcesCompat.getFont(this, R.font.poppins_regular)
        val medium = ResourcesCompat.getFont(this, R.font.poppins_medium)
        val bulletColor = section.accentTint?.let { Color.parseColor(it) } ?: brand

        section.blocks.forEach { block ->
            when (block) {
                is Block.Para -> item.llBody.addView(paragraph(block.text, regular))
                is Block.SubHead -> item.llBody.addView(subHead(block.text, medium))
                is Block.Bullet -> item.llBody.addView(bulletRow(block.text, regular, bulletColor))
            }
        }
        return item.root
    }

    private fun buildContactCard(
        inflater: LayoutInflater,
        parent: ViewGroup,
        contact: Contact,
    ): View {
        val item = ItemPolicySectionBinding.inflate(inflater, parent, false)
        item.iconBg.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FCE4EC"))
        item.ivIcon.setImageResource(R.drawable.ic_info)
        item.ivIcon.setColorFilter(brand)
        item.tvTitle.text = "Contact Us"

        val regular = ResourcesCompat.getFont(this, R.font.poppins_regular)
        val medium = ResourcesCompat.getFont(this, R.font.poppins_medium)

        item.llBody.addView(paragraph(contact.intro, regular))

        val emailPill = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) }
            text = contact.email
            typeface = medium
            textSize = 13.5f
            setTextColor(brand)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(9), dp(16), dp(9))
            setBackgroundResource(R.drawable.bg_pill_email)
            setOnSingleClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${contact.email}")))
                } catch (_: Exception) {
                }
            }
        }
        item.llBody.addView(emailPill)

        item.llBody.addView(subHead("Address", medium))
        item.llBody.addView(paragraph(contact.address, regular))
        return item.root
    }

    // ---- View builders ----

    private fun paragraph(text: String, font: Typeface?): TextView =
        TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
            this.text = text
            setTextColor(bodyColor)
            textSize = 13.5f
            typeface = font
            setLineSpacing(dp(4).toFloat(), 1f)
        }

    private fun subHead(text: String, font: Typeface?): TextView =
        TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) }
            this.text = text
            setTextColor(ContextCompat.getColor(this@BasePolicyActivity, R.color.black_light))
            textSize = 14f
            typeface = font
        }

    private fun bulletRow(text: String, font: Typeface?, dotColor: Int): View {
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(6) }
            orientation = LinearLayout.HORIZONTAL
        }
        val dot = TextView(this).apply {
            this.text = "•"
            setTextColor(dotColor)
            textSize = 15f
            typeface = font
            layoutParams = LinearLayout.LayoutParams(dp(16), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val body = TextView(this).apply {
            this.text = text
            setTextColor(bodyColor)
            textSize = 13.5f
            typeface = font
            setLineSpacing(dp(4).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(dot)
        row.addView(body)
        return row
    }

    protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
