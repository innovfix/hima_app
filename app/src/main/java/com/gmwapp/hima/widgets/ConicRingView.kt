package com.gmwapp.hima.widgets

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class ConicRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 6f
    }

    private val colors = intArrayOf(
        0xFFFF1381.toInt(),
        0xFF9C1DF9.toInt(),
        0xFF10F08A.toInt(),
        0xFFFF1381.toInt()
    )

    private var rotator: ObjectAnimator? = null

    private val density: Float
        get() = resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paint.shader = SweepGradient(w / 2f, h / 2f, colors, null)
    }

    override fun onDraw(canvas: Canvas) {
        val r = (minOf(width, height) / 2f) - paint.strokeWidth / 2f
        if (r <= 0f) return
        canvas.drawCircle(width / 2f, height / 2f, r, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rotator = ObjectAnimator.ofFloat(this, View.ROTATION, 0f, 360f).apply {
            duration = 3000L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    override fun onDetachedFromWindow() {
        rotator?.cancel()
        rotator = null
        super.onDetachedFromWindow()
    }
}
