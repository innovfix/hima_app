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


class LanguageAdapter(
    val activity: Activity,
    private val languages: ArrayList<Language>,
    val onItemSelectionListener: OnItemSelectionListener<Language>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** Position whose selection was just triggered by a tap — plays the reveal once. */
    private var pendingAnimPos: Int = RecyclerView.NO_POSITION

    /** Total wiper-reveal duration. All phase delays/durations are fractions of this. */
    private val revealDurationMs = 1400L

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

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        // We drive our own selection reveal, so kill the default change cross-fade —
        // otherwise notifyItemChanged() would flicker the row under our animation.
        (recyclerView.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
            ?.supportsChangeAnimations = false
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
            // Rebind ONLY the two rows that change (old selection + new) instead of the
            // whole list — a full notifyDataSetChanged() rebinds every visible row at the
            // exact moment the reveal starts, which is the tap-time hitch.
            val prev = languages.indexOfFirst { it.isSelected == true }
            languages.onEach { it.isSelected = false }
            languages[pos].isSelected = true
            pendingAnimPos = pos
            if (prev != RecyclerView.NO_POSITION && prev != pos) notifyItemChanged(prev)
            notifyItemChanged(pos)
        }
    }

    /** Make sure a non-animating row shows its final, fully-visible state. */
    private fun restToRest(holder: ItemHolder, englishName: String, nativeName: String) {
        holder.binding.tvLanguage.text = englishName
        holder.binding.tvLanguageSubtitle.text = nativeName
        holder.binding.llText.alpha = 1f
        holder.binding.llText.translationX = 0f
        holder.binding.ivLanguage.translationX = 0f
        holder.binding.ivLanguage.translationZ = 0f
        holder.binding.ivLanguage.scaleX = 1f
        holder.binding.ivLanguage.scaleY = 1f
        holder.binding.tvHeroNative.alpha = 0f
        holder.binding.tvHeroNative.scaleX = 1f
        holder.binding.tvHeroNative.scaleY = 1f
        holder.binding.tvHeroNative.clipBounds = null
        holder.binding.vShine.alpha = 0f
        holder.binding.ivCheck.scaleX = 1f
        holder.binding.ivCheck.scaleY = 1f
    }

    /**
     * Wiper reveal (approved mockup, smooth build). Order: the badge LEADS (glides
     * right); the BIG native script COMES AFTER (~16%), wipes left→right, holds, then
     * GOES (collapses); only as it leaves do the row's English + native texts settle
     * in; a gloss shine rides across and the check pops. Total = [revealDurationMs].
     *
     * Smoothness: everything runs on transform / alpha / clipBounds — the row text is
     * set ONCE and revealed by animating the whole ll_text block (no per-frame
     * TextView relayout), and the pure-transform views get a hardware layer for the run.
     */
    private fun playWiperReveal(holder: ItemHolder, englishName: String, nativeName: String) {
        val badge = holder.binding.ivLanguage
        val text = holder.binding.llText
        val hero = holder.binding.tvHeroNative
        val shine = holder.binding.vShine
        val check = holder.binding.ivCheck
        val density = activity.resources.displayMetrics.density
        val d = revealDurationMs

        // Text is set ONCE (no per-frame relayout) and hidden while the hero is on screen.
        holder.binding.tvLanguage.text = englishName
        holder.binding.tvLanguageSubtitle.text = nativeName
        text.alpha = 0f
        text.translationX = 6f * density
        hero.text = nativeName
        hero.alpha = 0f
        hero.scaleX = 1f
        hero.scaleY = 1f
        shine.alpha = 0f
        check.scaleX = 0.3f
        check.scaleY = 0.3f

        // Defer to after layout so view sizes are known. Bail if the holder was recycled
        // or rebound before the frame runs — otherwise we'd animate the wrong row.
        val targetPos = holder.bindingAdapterPosition
        val runnable = Runnable {
            if (holder.bindingAdapterPosition != targetPos) return@Runnable
            val parent = badge.parent as View
            if (parent.width <= 0) return@Runnable
            val travel = (parent.width - parent.paddingRight - badge.right)
                .toFloat().coerceAtLeast(0f)
            val heroW = parent.width
            val heroH = hero.height.takeIf { it > 0 } ?: badge.height

            // GPU layers for the pure transform/alpha views during the run.
            badge.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            shine.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            text.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            check.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            badge.translationZ = 12f

            // 1. Badge LEADS: hold → sweep right → hold → return, with a gentle scale.
            val badgeAnim = ObjectAnimator.ofPropertyValuesHolder(
                badge,
                PropertyValuesHolder.ofKeyframe(
                    View.TRANSLATION_X,
                    Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(0.10f, 0f),
                    Keyframe.ofFloat(0.48f, travel), Keyframe.ofFloat(0.60f, travel),
                    Keyframe.ofFloat(1f, 0f),
                ),
                PropertyValuesHolder.ofKeyframe(
                    View.SCALE_X,
                    Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(0.10f, 1.12f),
                    Keyframe.ofFloat(0.48f, 1.06f), Keyframe.ofFloat(1f, 1f),
                ),
                PropertyValuesHolder.ofKeyframe(
                    View.SCALE_Y,
                    Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(0.10f, 1.12f),
                    Keyframe.ofFloat(0.48f, 1.06f), Keyframe.ofFloat(1f, 1f),
                ),
            ).apply {
                duration = d
                interpolator = AccelerateDecelerateInterpolator()
            }

            // 2. Hero COMES AFTER the badge starts (~16%), wipes left→right, holds.
            hero.clipBounds = Rect(0, 0, 0, heroH)
            val heroIn = ValueAnimator.ofFloat(0f, 1f).apply {
                startDelay = (d * 0.16f).toLong()   // wait for the badge to lead
                duration = (d * 0.38f).toLong()      // fully revealed by ~54%
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val f = it.animatedValue as Float
                    hero.alpha = 1f
                    hero.clipBounds = Rect(0, 0, (heroW * f).toInt(), heroH)
                }
            }
            // 3. Hero GOES: collapses/fades as the badge returns (72% → end).
            val heroOut = ObjectAnimator.ofPropertyValuesHolder(
                hero,
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.8f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.8f),
            ).apply {
                startDelay = (d * 0.72f).toLong()
                duration = (d * 0.28f).toLong()
            }

            // 4. Row text SETTLES in only as the hero leaves (72% →), no relayout.
            val textSettle = ObjectAnimator.ofPropertyValuesHolder(
                text,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 6f * density, 0f),
            ).apply {
                startDelay = (d * 0.72f).toLong()
                duration = (d * 0.23f).toLong()
                interpolator = DecelerateInterpolator()
            }

            // 5. Gloss shine rides across the row.
            val shineWidth = (shine.width.takeIf { it > 0 } ?: parent.width).toFloat()
            shine.translationX = -shineWidth
            val shineAnim = ObjectAnimator.ofPropertyValuesHolder(
                shine,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -shineWidth, shineWidth),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 0.9f, 0.9f, 0f),
            ).apply {
                duration = d
                interpolator = AccelerateDecelerateInterpolator()
            }

            // 6. Check pops in near the end.
            val checkPop = ObjectAnimator.ofPropertyValuesHolder(
                check,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.3f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.3f, 1f),
            ).apply {
                startDelay = (d * 0.72f).toLong()
                duration = (d * 0.30f).toLong()
                interpolator = OvershootInterpolator()
            }

            val set = AnimatorSet()
            set.playTogether(badgeAnim, heroIn, heroOut, textSettle, shineAnim, checkPop)
            set.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    text.alpha = 1f
                    text.translationX = 0f
                    hero.alpha = 0f
                    hero.clipBounds = null
                    hero.scaleX = 1f
                    hero.scaleY = 1f
                    shine.alpha = 0f
                    badge.translationX = 0f
                    badge.translationZ = 0f
                    badge.scaleX = 1f
                    badge.scaleY = 1f
                    check.scaleX = 1f
                    check.scaleY = 1f
                    badge.setLayerType(View.LAYER_TYPE_NONE, null)
                    shine.setLayerType(View.LAYER_TYPE_NONE, null)
                    text.setLayerType(View.LAYER_TYPE_NONE, null)
                    check.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            holder.anim = set
            set.start()
        }
        holder.pendingPost = runnable
        holder.binding.main.post(runnable)
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
