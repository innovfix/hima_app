package com.gmwapp.hima.widgets

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.GradientDrawable
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.gmwapp.hima.utils.GiftManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cinematic gift-send animation (ported from the approved "Gift Send Animation"
 * design). One full-screen overlay plays the whole sequence — dim + letterbox,
 * god-rays, aura, shockwave, a hero gift that bursts in big then arcs to the
 * recipient avatar, themed particle rain + sparkle burst, a shimmering gift-name
 * card, impact flash + rings + hearts, and a camera shake.
 *
 * Locked settings: speed 1.15x, 38 particles (12 in lite), gift scale 1.10x,
 * shake on, title = name only. Lite mode (video calls / low-RAM) trims the
 * particle load and skips the god-rays so the live call never stutters.
 *
 * Entry point: [GiftCinema.send]. Both the sender and the receiver call it from
 * their existing animateGift() so the moment plays on every screen in the call.
 */
object GiftCinema {

    private const val SPEED = 1.15f
    private fun d(ms: Long): Long = (ms / SPEED).toLong()
    private const val GIFT_SCALE = 1.10f

    /** Content frames with a cinematic currently playing — rapid taps don't stack. */
    private val busy = java.util.Collections.newSetFromMap(java.util.WeakHashMap<ViewGroup, Boolean>())

    data class Theme(
        val name: String,
        val color: Int,
        val rays: Int,
        val aura: Int,
        val rain: List<String>,
        val heartA: String,
        val heartB: String,
        val emoji: String,
    )

    private val ROSE = Theme(
        "Enchanted Rose", Color.parseColor("#ff5d7d"), Color.parseColor("#ffb3c1"),
        Color.parseColor("#D9FF2D55"), listOf("🌹", "🌸", "💗", "🥀", "✨"), "💗", "🌸", "🌹"
    )
    private val CHOC = Theme(
        "Sweet Indulgence", Color.parseColor("#c98a5a"), Color.parseColor("#e9c9a3"),
        Color.parseColor("#D9965F37"), listOf("🍫", "🍬", "🤎", "✨", "🍩"), "🤎", "🍬", "🍫"
    )
    private val CROWN = Theme(
        "Royal Crown", Color.parseColor("#ffd34d"), Color.parseColor("#fff1a8"),
        Color.parseColor("#E6FFB800"), listOf("👑", "🪙", "✨", "⭐", "💛"), "👑", "🪙", "👑"
    )
    private val TEDDY = Theme(
        "Cuddle Bear", Color.parseColor("#d6a36e"), Color.parseColor("#eccfa6"),
        Color.parseColor("#D9B07D4A"), listOf("🧸", "💖", "🎀", "✨", "💝"), "💖", "🎀", "🧸"
    )

    private fun themeForCost(cost: Int): Theme = when {
        cost >= 120 -> TEDDY
        cost >= 100 -> CROWN
        cost >= 60 -> CHOC
        else -> ROSE
    }

    /**
     * Play the cinematic for [giftUrl] over [activity].
     *
     * @param recipientView the avatar the gift flies to (binding.ivFemaleUser).
     * @param lite          true on video calls / low-RAM devices — fewer particles, no god-rays.
     * @param onStart        invoked just before playback (e.g. hide the video selfie PiP).
     * @param onEnd          invoked after teardown (e.g. restore the selfie PiP).
     */
    fun send(
        activity: Activity,
        giftUrl: String?,
        recipientView: View?,
        lite: Boolean,
        onStart: (() -> Unit)? = null,
        onEnd: (() -> Unit)? = null,
    ) {
        try {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            if (busy.contains(content)) return // throttle / no stacking
            busy.add(content)

            val cost = giftUrl?.let { GiftManager.getGiftIconsWithCoins()[it]?.coins } ?: 0
            val theme = themeForCost(cost)

            val overlay = GiftCinemaView(activity)
            content.addView(
                overlay,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            val finish: () -> Unit = {
                try { content.removeView(overlay) } catch (_: Exception) {}
                busy.remove(content)
                onEnd?.invoke()
            }

            val start: (Bitmap?) -> Unit = { bmp ->
                overlay.post {
                    try {
                        val recip = recipientCenter(overlay, recipientView)
                        onStart?.invoke()
                        overlay.play(bmp, theme, cost, recip, lite, finish)
                    } catch (_: Exception) {
                        finish() // never leave the overlay covering the call
                    }
                }
            }

            if (giftUrl.isNullOrBlank()) {
                start(null)
            } else {
                Glide.with(activity).asBitmap().load(giftUrl)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, t: Transition<in Bitmap>?) = start(resource)
                        override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) = start(null)
                        override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                    })
            }
        } catch (_: Exception) {
            onEnd?.invoke()
        }
    }

    private fun recipientCenter(overlay: View, recipientView: View?): PointF {
        val w = if (overlay.width > 0) overlay.width else overlay.resources.displayMetrics.widthPixels
        val h = if (overlay.height > 0) overlay.height else overlay.resources.displayMetrics.heightPixels
        if (recipientView == null || recipientView.width == 0) {
            return PointF(w * 0.715f, h * 0.215f)
        }
        val o = IntArray(2); overlay.getLocationOnScreen(o)
        val r = IntArray(2); recipientView.getLocationOnScreen(r)
        return PointF(
            (r[0] - o[0] + recipientView.width / 2).toFloat(),
            (r[1] - o[1] + recipientView.height / 2).toFloat()
        )
    }

    // ---------------------------------------------------------------------

    class GiftCinemaView(context: Activity) : FrameLayout(context) {

        private fun dp(v: Float) = v * resources.displayMetrics.density

        fun play(
            hero: Bitmap?, theme: Theme, cost: Int, recip: PointF, lite: Boolean, onComplete: () -> Unit
        ) {
            val w = if (width > 0) width else resources.displayMetrics.widthPixels
            val h = if (height > 0) height else resources.displayMetrics.heightPixels
            val startX = w / 2f
            val startY = h * 0.46f
            val dx = recip.x - startX
            val dy = recip.y - startY
            val mx = dx * 0.5f
            val my = dy * 0.5f - dp(95f)
            val particleCount = if (lite) 12 else 38
            val sparkCount = if (lite) 8 else 14

            // 1) dim + vignette
            val dim = View(context).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x40000000, 0xC7000000.toInt())
                ).apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = maxOf(w, h) * 0.78f
                    setGradientCenter(startX / w, startY / h)
                }
                alpha = 0f
            }
            addView(dim, lp())
            timeline(dim, d(3500), 0.10f, 0.82f)

            // 2) letterbox bars
            val barH = dp(46f).toInt()
            val barTop = View(context).apply { setBackgroundColor(Color.BLACK); scaleY = 0f; pivotY = 0f }
            val barBot = View(context).apply { setBackgroundColor(Color.BLACK); scaleY = 0f; pivotY = barH.toFloat() }
            addView(barTop, FrameLayout.LayoutParams(MATCH, barH, Gravity.TOP))
            addView(barBot, FrameLayout.LayoutParams(MATCH, barH, Gravity.BOTTOM))
            barAnim(barTop); barAnim(barBot)

            // 3) god rays (skipped in lite)
            if (!lite) {
                val rays = RaysView(context, theme.rays).apply { alpha = 0f }
                val raySize = dp(760f).toInt()
                addView(rays, centered(raySize, raySize, startX, startY))
                ValueAnimator.ofFloat(0f, 360f).apply {
                    duration = 7000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                    addUpdateListener { rays.rotation = it.animatedValue as Float }; start()
                }
                rayFade(rays)
            }

            // 4) aura
            val aura = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = dp(150f)
                    colors = intArrayOf(theme.aura, 0x00000000)
                }
                alpha = 0f
            }
            val auraSize = dp(300f).toInt()
            addView(aura, centered(auraSize, auraSize, startX, startY))
            auraPulse(aura)

            // 5) shockwave ring
            val shock = ringView(theme.color, dp(3f))
            val shockSize = dp(160f).toInt()
            addView(shock, centered(shockSize, shockSize, startX, startY))
            scalePop(shock, 0.2f, 2.6f, d(1500), 0L, 0.9f)

            // 6) particle layer (rain + spark + hearts) — single custom view
            val particles = ParticleView(context).apply {
                config(theme, w, h, startX, startY, recip, particleCount, sparkCount,
                    d(2400), d(1100), d(900), d(520), d(1100))
            }
            addView(particles, lp())
            particles.startLoop()

            // 7) hero gift (+ two ghost trails for the comet effect)
            val heroSize = dp(96f * GIFT_SCALE).toInt()
            addHero(hero, theme.emoji, heroSize, startX, startY, mx, my, dx, dy, 0.35f, dp(0f), d(150))
            addHero(hero, theme.emoji, heroSize, startX, startY, mx, my, dx, dy, 0.5f, dp(0f), d(80))
            addHero(hero, theme.emoji, heroSize, startX, startY, mx, my, dx, dy, 1f, dp(0f), 0L)

            // 8) impact flash + rings at recipient
            val flash = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = dp(210f)
                    colors = intArrayOf(0xE6FFFFFF.toInt(), 0x00FFFFFF)
                }
                alpha = 0f
            }
            val flashSize = dp(420f).toInt()
            addView(flash, centered(flashSize, flashSize, recip.x, recip.y))
            flashPop(flash, d(3000))

            val ring1 = ringView(theme.color, dp(3f)); addView(ring1, centered(dp(150f).toInt(), dp(150f).toInt(), recip.x, recip.y))
            scalePop(ring1, 0.3f, 2.3f, d(1000), d(2350), 1f)
            val ring2 = ringView(theme.rays, dp(2f)); addView(ring2, centered(dp(120f).toInt(), dp(120f).toInt(), recip.x, recip.y))
            scalePop(ring2, 0.3f, 2.3f, d(1000), d(2520), 1f)

            // 9) gift-name title card (name only) — shimmering, rises in
            val titleY = startY + dp(96f)
            val title = ShimmerTitle(context, theme.name.uppercase(), theme.color, theme.rays).apply { alpha = 0f }
            addView(title, FrameLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).also {
                it.topMargin = titleY.toInt()
            })
            title.translationY = dp(32f)
            title.animate().alpha(1f).translationY(0f).setStartDelay(d(250))
                .setDuration(d(900)).withEndAction {
                    title.animate().alpha(0f).translationY(-dp(16f)).setStartDelay(d(1100)).setDuration(d(500)).start()
                }.start()

            // camera shake near the impact, then tear down
            postDelayed({ shakeContent() }, d(2380))
            postDelayed({ onComplete() }, d(3700) + 80)
        }

        private fun addHero(
            hero: Bitmap?, emoji: String, size: Int,
            startX: Float, startY: Float, mx: Float, my: Float, dx: Float, dy: Float,
            baseAlpha: Float, @Suppress("UNUSED_PARAMETER") blur: Float, delay: Long
        ) {
            val v: View = if (hero != null) {
                ImageView(context).apply { setImageBitmap(hero); scaleType = ImageView.ScaleType.FIT_CENTER }
            } else {
                TextView(context).apply {
                    text = emoji; textSize = 56f; gravity = Gravity.CENTER
                }
            }
            v.alpha = 0f
            addView(v, centered(size, size, startX, startY))
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = (3000 / SPEED).toLong(); startDelay = delay
            }
            anim.addUpdateListener {
                val t = it.animatedValue as Float
                val (sc, px, py, rot, op) = heroFrame(t, mx, my, dx, dy)
                v.scaleX = sc; v.scaleY = sc
                v.translationX = px; v.translationY = py
                v.rotation = rot
                v.alpha = op * baseAlpha.coerceAtMost(1f)
            }
            anim.start()
        }

        /** Piecewise interpolation of the gift-cinema keyframes. */
        private data class HeroState(val sc: Float, val px: Float, val py: Float, val rot: Float, val op: Float)

        private fun heroFrame(t: Float, mx: Float, my: Float, dx: Float, dy: Float): HeroState {
            // stops: t, scale, posKey(0=origin,1=mid,2=dest), rot, opacity
            val sc: Float; val rot: Float; val op: Float
            val px: Float; val py: Float
            when {
                t < 0.07f -> { val k = t / 0.07f; sc = lerp(0.08f, 1.72f, k); rot = lerp(-22f, 9f, k); op = lerp(0f, 1f, k); px = 0f; py = 0f }
                t < 0.11f -> { val k = (t - 0.07f) / 0.04f; sc = lerp(1.72f, 1.42f, k); rot = lerp(9f, -3f, k); op = 1f; px = 0f; py = 0f }
                t < 0.22f -> { val k = (t - 0.11f) / 0.11f; sc = lerp(1.42f, 1.50f, k); rot = lerp(-3f, 3f, k); op = 1f; px = 0f; py = 0f }
                t < 0.40f -> { val k = (t - 0.22f) / 0.18f; sc = lerp(1.50f, 1.44f, k); rot = lerp(3f, -3f, k); op = 1f; px = 0f; py = 0f }
                t < 0.58f -> { val k = (t - 0.40f) / 0.18f; sc = lerp(1.44f, 0.95f, k); rot = lerp(-3f, 13f, k); op = 1f; px = lerp(0f, mx, k); py = lerp(0f, my, k) }
                t < 0.86f -> { val k = (t - 0.58f) / 0.28f; sc = lerp(0.95f, 0.30f, k); rot = lerp(13f, 20f, k); op = 1f; px = lerp(mx, dx, k); py = lerp(my, dy, k) }
                else -> { val k = (t - 0.86f) / 0.14f; sc = lerp(0.30f, 0.16f, k); rot = lerp(20f, 24f, k); op = lerp(1f, 0f, k); px = dx; py = dy }
            }
            return HeroState(sc, px, py, rot, op)
        }

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

        private fun shakeContent() {
            val target = (parent as? View) ?: this
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = d(520); interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val p = it.animatedValue as Float
                    val amp = dp(6f) * (1f - p)
                    target.translationX = (Math.sin(p * Math.PI * 6).toFloat()) * amp
                    target.translationY = (Math.cos(p * Math.PI * 5).toFloat()) * amp * 0.6f
                }
                addListenerEnd { target.translationX = 0f; target.translationY = 0f }
                start()
            }
        }

        // ---- small view/animator helpers ----
        private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private fun lp() = FrameLayout.LayoutParams(MATCH, MATCH)
        private fun centered(w: Int, h: Int, cx: Float, cy: Float) =
            FrameLayout.LayoutParams(w, h).also {
                it.leftMargin = (cx - w / 2).toInt(); it.topMargin = (cy - h / 2).toInt()
            }

        private fun ringView(color: Int, stroke: Float) = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setStroke(stroke.toInt(), color); setColor(Color.TRANSPARENT)
            }
            alpha = 0f
        }

        private fun timeline(v: View, dur: Long, inAt: Float, outAt: Float) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = dur; interpolator = LinearInterpolator()
                addUpdateListener {
                    val t = it.animatedValue as Float
                    v.alpha = when {
                        t < inAt -> t / inAt
                        t < outAt -> 1f
                        else -> 1f - (t - outAt) / (1f - outAt)
                    }
                }
                start()
            }
        }

        private fun barAnim(v: View) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = d(3500); interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val t = it.animatedValue as Float
                    v.scaleY = when {
                        t < 0.09f -> t / 0.09f
                        t < 0.84f -> 1f
                        else -> 1f - (t - 0.84f) / 0.16f
                    }
                }
                start()
            }
        }

        private fun rayFade(v: View) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = d(3500); interpolator = LinearInterpolator()
                addUpdateListener {
                    val t = it.animatedValue as Float
                    v.alpha = when {
                        t < 0.09f -> (t / 0.09f) * 0.75f
                        t < 0.68f -> lerp(0.75f, 0.55f, (t - 0.09f) / 0.59f)
                        else -> lerp(0.55f, 0f, (t - 0.68f) / 0.32f)
                    }
                }
                start()
            }
        }

        private fun auraPulse(v: View) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = d(3500); interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val t = it.animatedValue as Float
                    val (sc, op) = when {
                        t < 0.10f -> lerp(0.35f, 0.6f, t / 0.10f) to lerp(0f, 0.95f, t / 0.10f)
                        t < 0.44f -> lerp(0.6f, 1.08f, (t - 0.10f) / 0.34f) to lerp(0.95f, 0.7f, (t - 0.10f) / 0.34f)
                        t < 0.66f -> lerp(1.08f, 0.9f, (t - 0.44f) / 0.22f) to lerp(0.7f, 0.4f, (t - 0.44f) / 0.22f)
                        else -> lerp(0.9f, 0.55f, (t - 0.66f) / 0.34f) to lerp(0.4f, 0f, (t - 0.66f) / 0.34f)
                    }
                    v.scaleX = sc; v.scaleY = sc; v.alpha = op
                }
                start()
            }
        }

        private fun scalePop(v: View, from: Float, to: Float, dur: Long, delay: Long, peakAlpha: Float) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = dur; startDelay = delay
                addUpdateListener {
                    val t = it.animatedValue as Float
                    val sc = lerp(from, to, t)
                    v.scaleX = sc; v.scaleY = sc
                    v.alpha = if (t < 0.1f) (t / 0.1f) * peakAlpha else lerp(peakAlpha, 0f, (t - 0.1f) / 0.9f)
                }
                start()
            }
        }

        private fun flashPop(v: View, dur: Long) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = dur
                addUpdateListener {
                    val t = it.animatedValue as Float
                    v.alpha = when {
                        t < 0.67f -> 0f
                        t < 0.73f -> ((t - 0.67f) / 0.06f) * 0.9f
                        t < 0.84f -> lerp(0.9f, 0f, (t - 0.73f) / 0.11f)
                        else -> 0f
                    }
                }
                start()
            }
        }

        private inline fun ValueAnimator.addListenerEnd(crossinline end: () -> Unit) {
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = end()
            })
        }
    }

    // ------------------------------------------------------------------
    /** Rotating god-rays: a sweep gradient masked to a ring. */
    private class RaysView(context: Context, private val rayColor: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        }
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            val sectors = 24
            val cols = IntArray(sectors * 2 + 1)
            val pos = FloatArray(sectors * 2 + 1)
            for (i in 0 until sectors) {
                cols[i * 2] = rayColor; cols[i * 2 + 1] = 0x00000000
                pos[i * 2] = i.toFloat() / sectors
                pos[i * 2 + 1] = (i + 0.30f) / sectors
            }
            cols[sectors * 2] = rayColor; pos[sectors * 2] = 1f
            val saved = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            paint.shader = SweepGradient(cx, cy, cols, pos)
            canvas.drawCircle(cx, cy, minOf(cx, cy), paint)
            maskPaint.shader = RadialGradient(
                cx, cy, minOf(cx, cy),
                intArrayOf(0x00000000, 0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000),
                floatArrayOf(0.07f, 0.22f, 0.40f, 0.60f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, minOf(cx, cy), maskPaint)
            canvas.restoreToCount(saved)
        }
    }

    /** Shimmering uppercase gift-name. */
    private class ShimmerTitle(context: Context, private val label: String, private val c1: Int, private val c2: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = context.resources.displayMetrics.density * 22f
            letterSpacing = 0.14f
            isFakeBoldText = true
            try {
                typeface = androidx.core.content.res.ResourcesCompat.getFont(
                    context, context.resources.getIdentifier("poppins_extrabold", "font", context.packageName)
                )
            } catch (_: Exception) {}
        }
        private var shimmer = -1.8f
        init {
            ValueAnimator.ofFloat(-1.8f, 1.8f).apply {
                duration = 1600; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                addUpdateListener { shimmer = it.animatedValue as Float; invalidate() }; start()
            }
        }
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f + paint.textSize / 3f
            val span = width * 0.6f
            val mid = width / 2f + shimmer * span
            paint.shader = LinearGradient(
                mid - span, 0f, mid + span, 0f,
                intArrayOf(c1, Color.WHITE, c2, c1), floatArrayOf(0.12f, 0.38f, 0.52f, 0.78f), Shader.TileMode.CLAMP
            )
            canvas.drawText(label, cx, cy, paint)
        }
        override fun onMeasure(w: Int, h: Int) {
            setMeasuredDimension(MeasureSpec.getSize(w), (paint.textSize * 1.8f).toInt())
        }
    }

    /** One custom-draw layer for all particles (rain + sparkle burst + hearts). */
    private class ParticleView(context: Context) : View(context), Choreographer.FrameCallback {
        private class P(
            val emoji: String, val sx: Float, val sy: Float, val ex: Float, val ey: Float,
            val size: Float, val rotEnd: Float, val delay: Long, val dur: Long, val kind: Int
        )
        private val list = ArrayList<P>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private var startTime = 0L
        private var maxEnd = 0L
        private val bounds = Rect()

        fun config(
            theme: Theme, w: Int, h: Int, startX: Float, startY: Float, recip: PointF,
            count: Int, sparkN: Int, rainDurBase: Long, rainDurRand: Long,
            sparkDur: Long, @Suppress("UNUSED_PARAMETER") shakeDur: Long, heartDur: Long
        ) {
            val dp = resources.displayMetrics.density
            // rain
            for (i in 0 until count) {
                val x = (Math.random() * w).toFloat()
                val drift = ((Math.random() * 2 - 1) * 70 * dp).toFloat()
                list.add(P(theme.rain[i % theme.rain.size], x, -60f * dp, x + drift, h + 120f * dp,
                    (16 + Math.random() * 22).toFloat() * dp, ((Math.random() * 2 - 1) * 360).toFloat(),
                    (Math.random() * 1500).toLong(), rainDurBase + (Math.random() * rainDurRand).toLong(), 0))
            }
            // sparkle burst
            val pool = listOf("✨", "⭐", "🌟", theme.emoji, "💫")
            for (i in 0 until sparkN) {
                val a = (Math.PI * 2 * i / sparkN + (Math.random() - 0.5) * 0.5)
                val dist = (80 + Math.random() * 110) * dp
                list.add(P(pool[i % pool.size], startX, startY,
                    startX + (cos(a) * dist).toFloat(), startY + (sin(a) * dist).toFloat(),
                    (15 + Math.random() * 16).toFloat() * dp, ((Math.random() * 2 - 1) * 220).toFloat(),
                    (Math.random() * 140).toLong(), sparkDur + (Math.random() * 520).toLong(), 1))
            }
            // hearts at recipient
            list.add(P(theme.heartA, recip.x - 30 * dp, recip.y, recip.x - 30 * dp, recip.y - 90 * dp, 26f * dp, 0f, (2350).toLong(), heartDur, 2))
            list.add(P(theme.heartB, recip.x + 32 * dp, recip.y, recip.x + 32 * dp, recip.y - 95 * dp, 22f * dp, 0f, (2480).toLong(), heartDur, 2))
            list.add(P("✨", recip.x, recip.y - 14 * dp, recip.x, recip.y - 100 * dp, 20f * dp, 0f, (2420).toLong(), heartDur, 2))
            maxEnd = list.maxOf { it.delay + it.dur }
        }

        fun startLoop() { startTime = System.nanoTime() / 1_000_000L; Choreographer.getInstance().postFrameCallback(this) }

        override fun doFrame(frameTimeNanos: Long) {
            invalidate()
            val elapsed = System.nanoTime() / 1_000_000L - startTime
            if (elapsed < maxEnd + 60) Choreographer.getInstance().postFrameCallback(this)
        }

        override fun onDraw(canvas: Canvas) {
            val now = System.nanoTime() / 1_000_000L - startTime
            for (p in list) {
                val t = (now - p.delay).toFloat() / p.dur
                if (t < 0f || t > 1f) continue
                val (x, y, alpha, scale, rot) = when (p.kind) {
                    0 -> { // rain
                        val op = if (t < 0.1f) t / 0.1f else if (t < 0.9f) 1f else 1f - (t - 0.9f) / 0.1f
                        Quint(p.sx + (p.ex - p.sx) * t, p.sy + (p.ey - p.sy) * t, op, 1f, p.rotEnd * t)
                    }
                    1 -> { // spark
                        val op = if (t < 0.16f) t / 0.16f else 1f - (t - 0.16f) / 0.84f
                        Quint(p.sx + (p.ex - p.sx) * t, p.sy + (p.ey - p.sy) * t, op, 0.2f + 0.8f * t, p.rotEnd * t)
                    }
                    else -> { // heart-pop
                        val op = if (t < 0.35f) t / 0.35f else 1f - (t - 0.35f) / 0.65f
                        val sc = if (t < 0.35f) 0f + 1.25f * (t / 0.35f) else 1.25f - 0.4f * ((t - 0.35f) / 0.65f)
                        Quint(p.sx + (p.ex - p.sx) * t, p.sy + (p.ey - p.sy) * t, op, sc, 0f)
                    }
                }
                if (alpha <= 0f) continue
                paint.textSize = p.size * scale
                paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
                paint.getTextBounds(p.emoji, 0, p.emoji.length, bounds)
                canvas.save()
                if (rot != 0f) canvas.rotate(rot, x, y)
                canvas.drawText(p.emoji, x, y + p.size / 3f, paint)
                canvas.restore()
            }
        }

        private data class Quint(val x: Float, val y: Float, val a: Float, val s: Float, val r: Float)

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow(); Choreographer.getInstance().removeFrameCallback(this)
        }
    }
}
