package com.gmwapp.hima.utils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * Gold-foil text with a slow shimmer sweep — used for the earnings amount on the
 * B1 honour screen. Pure display; rebuilds its gradient when the text/size changes
 * (so a count-up animation keeps the foil), and translates the gradient matrix to
 * animate the highlight.
 */
class GoldShimmerTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatTextView(context, attrs, defStyle) {

    private val gold = intArrayOf(
        0xFFB8862F.toInt(), 0xFFE8C874.toInt(), 0xFFFFF4CF.toInt(),
        0xFFE8C874.toInt(), 0xFFB8862F.toInt()
    )
    private val positions = floatArrayOf(0f, 0.38f, 0.5f, 0.62f, 1f)

    private var shader: LinearGradient? = null
    private val matrix = Matrix()
    private var translate = 0f
    private var span = 0f
    private var animator: ValueAnimator? = null

    private fun rebuild(w: Int) {
        if (w <= 0) return
        span = w * 2.2f
        shader = LinearGradient(
            -span, 0f, 0f, 0f, gold, positions, Shader.TileMode.CLAMP
        )
        paint.shader = shader
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild(w)
    }

    // Count-up changes the text width; keep the foil in sync.
    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, after: Int) {
        super.onTextChanged(text, start, before, after)
        if (width > 0) rebuild(width)
    }

    fun startShimmer() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                translate = (it.animatedValue as Float) * span
                shader?.let { s ->
                    matrix.setTranslate(translate, 0f)
                    s.setLocalMatrix(matrix)
                    invalidate()
                }
            }
            start()
        }
    }

    fun stopShimmer() {
        animator?.cancel()
        animator = null
    }

    override fun onDetachedFromWindow() {
        stopShimmer()
        super.onDetachedFromWindow()
    }
}
