package com.gmwapp.hima.adapters

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Rect
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.databinding.AdapterLanguageBinding
import com.gmwapp.hima.retrofit.responses.Language
import com.gmwapp.hima.utils.setOnSingleClickListener
import java.text.BreakIterator


class LanguageAdapter(
    val activity: Activity,
    private val languages: ArrayList<Language>,
    val onItemSelectionListener: OnItemSelectionListener<Language>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** Position whose selection was just triggered by a tap — plays the reveal once. */
    private var pendingAnimPos: Int = RecyclerView.NO_POSITION

    // Map native scripts for each language
    private val nativeScripts = mapOf(
        "Hindi" to "ह",
        "Telugu" to "త",
        "Malayalam" to "മ",
        "Kannada" to "ಕ",
        "Punjabi" to "ਪ",
        "Tamil" to "த",
        "Marathi" to "म",
        "Bengali" to "ব",
        "Assamese" to "অ",
        "Odia" to "ଓ",
        "Gujarati" to "ગ"
    )


    private val nativeNames = mapOf(
        "Hindi" to "हिंदी",
        "Telugu" to "తెలుగు",
        "Malayalam" to "മലയാളം",
        "Kannada" to "ಕನ್ನಡ",
        "Punjabi" to "ਪੰਜਾਬੀ",
        "Tamil" to "தமிழ்",
        "Marathi" to "मराठी",
        "Bengali" to "বাংলা",
        "Assamese" to "অসমীয়া",
        "Odia" to "ଓଡ଼ିଆ",
        "Gujarati" to "ગુજરાતી"
    )


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val itemHolder = ItemHolder(
            AdapterLanguageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
        return itemHolder
    }

    override fun onBindViewHolder(holderParent: RecyclerView.ViewHolder, position: Int) {
        val holder: ItemHolder = holderParent as ItemHolder
        val language: Language = languages[position]

        // Stop any animation left over from a recycled binding.
        holder.cancelAnim()

        val englishName = language.name
        val nativeName = nativeNames[language.name] ?: language.name
        holder.binding.ivLanguage.text = nativeScripts[language.name] ?: language.name.take(1)
        holder.binding.tvLanguageSubtitle.text = nativeName
        holder.binding.tvHeroNative.text = nativeName

        // Selection state styling
        val density = activity.resources.displayMetrics.density
        if (language.isSelected == true) {
            holder.binding.main.setStrokeColor(ColorStateList.valueOf(activity.getColor(R.color.colorAccent)))
            holder.binding.main.strokeWidth = (2 * density).toInt()
            holder.binding.main.setCardBackgroundColor(activity.getColor(R.color.white))
            holder.binding.ivCheck.visibility = View.VISIBLE
        } else {
            holder.binding.main.setStrokeColor(ColorStateList.valueOf(activity.getColor(R.color.onboarding_card_border)))
            holder.binding.main.strokeWidth = (1 * density).toInt()
            holder.binding.main.setCardBackgroundColor(activity.getColor(R.color.white))
            holder.binding.ivCheck.visibility = View.GONE
        }

        if (position == pendingAnimPos && language.isSelected == true) {
            pendingAnimPos = RecyclerView.NO_POSITION
            playWiperReveal(holder, englishName, nativeName)
        } else {
            restToRest(holder, englishName, nativeName)
        }

        holder.binding.main.setOnSingleClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnSingleClickListener
            onItemSelectionListener.onItemSelected(languages[pos])
            languages.onEach { it.isSelected = false }
            languages[pos].isSelected = true
            pendingAnimPos = pos
            notifyDataSetChanged()
        }
    }

    /** Make sure a non-animating row shows its final, fully-visible state. */
    private fun restToRest(holder: ItemHolder, englishName: String, nativeName: String) {
        holder.binding.tvLanguage.text = englishName
        holder.binding.tvLanguageSubtitle.text = nativeName
        holder.binding.ivLanguage.translationX = 0f
        holder.binding.ivLanguage.translationZ = 0f
        holder.binding.ivLanguage.scaleX = 1f
        holder.binding.ivLanguage.scaleY = 1f
        holder.binding.tvHeroNative.alpha = 0f
        holder.binding.tvHeroNative.clipBounds = null
        holder.binding.vShine.alpha = 0f
        holder.binding.ivCheck.scaleX = 1f
        holder.binding.ivCheck.scaleY = 1f
    }

    /**
     * Wiper reveal (approved mockup): the badge glides left→right; the BIG native
     * script reveals left→right in the centre, in sync with the sweep; the badge
     * returns home as the English name + small native settle into the row;
     * finished with a gloss shine and the check popping in. Total ≈ 1250 ms.
     */
    private fun playWiperReveal(holder: ItemHolder, englishName: String, nativeName: String) {
        val badge = holder.binding.ivLanguage
        val nameTv = holder.binding.tvLanguage
        val subTv = holder.binding.tvLanguageSubtitle
        val hero = holder.binding.tvHeroNative
        val shine = holder.binding.vShine
        val check = holder.binding.ivCheck

        val nameColor = nameTv.currentTextColor
        val subColor = subTv.currentTextColor
        val enRanges = englishName.indices.map { it..it }
        val natRanges = graphemeRanges(nativeName)

        // Entrance start states.
        nameTv.text = revealedText(englishName, enRanges, nameColor, 0f)
        subTv.text = revealedText(nativeName, natRanges, subColor, 0f)
        hero.alpha = 0f
        shine.alpha = 0f
        check.scaleX = 0.3f
        check.scaleY = 0.3f

        // Defer to after layout so view sizes are known. The animation is built and
        // started inside this runnable, so track it on the holder and bail if the
        // holder was recycled or rebound to another row before the frame runs —
        // otherwise we'd animate the wrong row with stale text.
        val targetPos = holder.bindingAdapterPosition
        val runnable = Runnable {
            if (holder.bindingAdapterPosition != targetPos) return@Runnable
            val parent = badge.parent as View
            if (parent.width <= 0) return@Runnable
            val travel = (parent.width - parent.paddingRight - badge.right)
                .toFloat().coerceAtLeast(0f)

            // 1. Badge sweep: hold → right → hold → back, with a gentle scale-up.
            badge.translationZ = 12f
            hero.translationZ = 6f
            val badgeAnim = ObjectAnimator.ofPropertyValuesHolder(
                badge,
                PropertyValuesHolder.ofKeyframe(
                    View.TRANSLATION_X,
                    Keyframe.ofFloat(0f, 0f),
                    Keyframe.ofFloat(0.10f, 0f),
                    Keyframe.ofFloat(0.48f, travel),
                    Keyframe.ofFloat(0.60f, travel),
                    Keyframe.ofFloat(1f, 0f),
                ),
                PropertyValuesHolder.ofKeyframe(
                    View.SCALE_X,
                    Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(0.10f, 1.12f),
                    Keyframe.ofFloat(0.55f, 1.05f), Keyframe.ofFloat(1f, 1f),
                ),
                PropertyValuesHolder.ofKeyframe(
                    View.SCALE_Y,
                    Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(0.10f, 1.12f),
                    Keyframe.ofFloat(0.55f, 1.05f), Keyframe.ofFloat(1f, 1f),
                ),
            ).apply {
                duration = 2200
                interpolator = AccelerateDecelerateInterpolator()
            }

            // 2. Hero native reveals left→right, locked to the badge's rightward travel.
            val heroW = parent.width
            val heroH = hero.height.takeIf { it > 0 } ?: badge.height
            hero.clipBounds = Rect(0, 0, 0, heroH)
            val heroIn = ValueAnimator.ofFloat(0f, 1f).apply {
                startDelay = 310   // ~14% of 2200 — badge has started moving right
                duration = 750     // completes at ~48% — badge reaches the right edge
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val f = it.animatedValue as Float
                    hero.alpha = 1f
                    hero.clipBounds = Rect(0, 0, (heroW * f).toInt(), heroH)
                }
            }
            // 3. Hero collapses as the badge returns.
            val heroOut = ObjectAnimator.ofPropertyValuesHolder(
                hero,
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.78f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.78f),
            ).apply {
                startDelay = 1720
                duration = 480
            }

            // 4. English + small native settle in on the return.
            val enReveal = ValueAnimator.ofFloat(0f, 1f).apply {
                startDelay = 1380
                duration = 760
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    nameTv.text = revealedText(englishName, enRanges, nameColor, it.animatedValue as Float)
                }
            }
            val subReveal = ValueAnimator.ofFloat(0f, 1f).apply {
                startDelay = 1440
                duration = 730
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    subTv.text = revealedText(nativeName, natRanges, subColor, it.animatedValue as Float)
                }
            }

            // 5. Gloss shine rides across the row.
            val shineWidth = (shine.width.takeIf { it > 0 } ?: parent.width).toFloat()
            shine.translationX = -shineWidth
            val shineAnim = ObjectAnimator.ofPropertyValuesHolder(
                shine,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -shineWidth, shineWidth),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 0.9f, 0.9f, 0f),
            ).apply {
                duration = 1850
                interpolator = AccelerateDecelerateInterpolator()
            }

            // 6. Check pops in near the end.
            val checkPop = ObjectAnimator.ofPropertyValuesHolder(
                check,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.3f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.3f, 1f),
            ).apply {
                startDelay = 1580
                duration = 500
                interpolator = OvershootInterpolator()
            }

            val set = AnimatorSet()
            set.playTogether(badgeAnim, heroIn, heroOut, enReveal, subReveal, shineAnim, checkPop)
            set.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    nameTv.text = englishName
                    subTv.text = nativeName
                    hero.alpha = 0f
                    hero.clipBounds = null
                    hero.scaleX = 1f
                    hero.scaleY = 1f
                    hero.translationZ = 0f
                    shine.alpha = 0f
                    badge.translationX = 0f
                    badge.translationZ = 0f
                    badge.scaleX = 1f
                    badge.scaleY = 1f
                    check.scaleX = 1f
                    check.scaleY = 1f
                }
            })
            holder.anim = set
            set.start()
        }
        holder.pendingPost = runnable
        holder.binding.main.post(runnable)
    }

    /** Per-unit alpha reveal (no reflow): units before progress are opaque, the rest transparent. */
    private fun revealedText(
        full: String, units: List<IntRange>, baseColor: Int, progress: Float
    ): CharSequence {
        val sb = SpannableString(full)
        val exact = progress * units.size
        val rgb = baseColor and 0x00FFFFFF
        units.forEachIndexed { i, r ->
            val a = (exact - i).coerceIn(0f, 1f)
            val color = ((a * 255).toInt() shl 24) or rgb
            sb.setSpan(ForegroundColorSpan(color), r.first, r.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    /** Split into grapheme clusters so Indic vowel-signs / conjuncts stay intact. */
    private fun graphemeRanges(s: String): List<IntRange> {
        val it = BreakIterator.getCharacterInstance()
        it.setText(s)
        val out = ArrayList<IntRange>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            out.add(start until end)
            start = end
            end = it.next()
        }
        return out
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? ItemHolder)?.cancelAnim()
    }

    override fun getItemCount(): Int {
        return languages.size
    }

    internal class ItemHolder(val binding: AdapterLanguageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var anim: AnimatorSet? = null
        var pendingPost: Runnable? = null
        fun cancelAnim() {
            pendingPost?.let { binding.main.removeCallbacks(it) }
            pendingPost = null
            anim?.cancel()
            anim = null
        }
    }
}
