package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.gmwapp.hima.databinding.ActivityCreatorLevelBinding
import com.gmwapp.hima.models.CreatorLevelData
import com.gmwapp.hima.models.CreatorLevelDummy
import com.gmwapp.hima.utils.setOnSingleClickListener

class CreatorLevelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatorLevelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatorLevelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        binding.includeProfileToolbar.tvFlowTitle.text = "Creator Level"
        binding.includeProfileToolbar.cvBack.setOnSingleClickListener { finish() }

        // TODO: replace with real API call → viewModel.fetchCreatorLevel()
        populateLevel(CreatorLevelDummy.get())
        setupFaqs()

        // BL-19: Something wrong? → support flow
        binding.llSomethingWrong.setOnSingleClickListener {
            startActivity(Intent(this, HelpAndSupportActivity::class.java))
        }
    }

    private fun populateLevel(data: CreatorLevelData) {
        // ── Hero card ────────────────────────────────────────────────
        binding.tvHeroLevelName.text = data.levelName
        binding.tvHeroMessage.text   = data.customMessage
        binding.tvHeroRateChip.text  = "You earn ₹%.2f per 💗".format(data.ratePerStar)
        renderHeroStars(data.level)

        // ── Progress card ─────────────────────────────────────────────
        binding.tvNextLevelBadge.text = if (data.isMaxLevel) {
            "Max Level 🏆"
        } else {
            "Next: ${data.nextLevelName} ${CreatorLevelDummy.starsLabel(data.nextLevel)}"
        }

        // Last-30-day stats (revenue, calls, active days)
        binding.tvStatRevenue.text     = "₹%,.0f".format(data.revenue30d)
        binding.tvStatCalls.text       = data.calls30d.toString()
        binding.tvStatActiveDays.text  = data.activeDays30d.toString()
    }

    /** Lights up the correct number of stars in the hero card. */
    private fun renderHeroStars(level: Int) {
        val stars = listOf(
            binding.ivHeroStar1,
            binding.ivHeroStar2,
            binding.ivHeroStar3,
            binding.ivHeroStar4,
            binding.ivHeroStar5,
        )
        val activeColor = android.graphics.Color.parseColor("#FFB800")
        val inactiveColor = android.graphics.Color.parseColor("#D1D5DB")
        stars.forEachIndexed { index, iv ->
            iv.imageTintList = android.content.res.ColorStateList.valueOf(
                if (index < level) activeColor else inactiveColor
            )
        }
    }

    /** Wires all FAQ accordion items. */
    private fun setupFaqs() {
        listOf(
            Triple(binding.faq1Header, binding.faq1Chevron, binding.faq1Answer),
            Triple(binding.faq3Header, binding.faq3Chevron, binding.faq3Answer),
            Triple(binding.faq5Header, binding.faq5Chevron, binding.faq5Answer),
            Triple(binding.faq6Header, binding.faq6Chevron, binding.faq6Answer),
        ).forEach { (header, chevron, answer) ->
            header.setOnClickListener {
                val expanding = answer.visibility == android.view.View.GONE
                answer.visibility = if (expanding) android.view.View.VISIBLE else android.view.View.GONE
                chevron.animate()
                    .rotation(if (expanding) 270f else 90f)
                    .setDuration(200)
                    .start()
            }
        }
    }
}
