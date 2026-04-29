package com.gmwapp.hima.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class DottedFaceGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xD9FFFFFF.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val unit = min(w, h)

        val dotRadius = unit * 0.008f
        val spacing = unit * 0.038f

        // Neutral head oval - centered, no gender-coded proportions.
        val headPath = Path().apply {
            addOval(
                RectF(
                    w * 0.30f,
                    h * 0.10f,
                    w * 0.70f,
                    h * 0.62f
                ),
                Path.Direction.CW
            )
        }
        drawDottedPath(canvas, headPath, dotRadius, spacing)

        // Symmetric shoulder arc - broad, no collar/neck styling.
        val shoulderPath = Path().apply {
            moveTo(0f, h * 0.95f)
            cubicTo(
                w * 0.20f, h * 0.78f,
                w * 0.35f, h * 0.72f,
                w * 0.50f, h * 0.72f
            )
            cubicTo(
                w * 0.65f, h * 0.72f,
                w * 0.80f, h * 0.78f,
                w, h * 0.95f
            )
        }
        drawDottedPath(canvas, shoulderPath, dotRadius, spacing)
    }

    private fun drawDottedPath(
        canvas: Canvas,
        path: Path,
        dotRadius: Float,
        spacing: Float
    ) {
        val pm = PathMeasure(path, false)
        val pos = FloatArray(2)

        do {
            val len = pm.length
            var distance = 0f
            while (distance <= len) {
                pm.getPosTan(distance, pos, null)
                canvas.drawCircle(pos[0], pos[1], dotRadius, dotPaint)
                distance += spacing
            }
        } while (pm.nextContour())
    }
}
