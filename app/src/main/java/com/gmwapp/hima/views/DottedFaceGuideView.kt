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

        // Adjusted for sparser dots like image 1
        val dotRadius = unit * 0.008f
        val spacing = unit * 0.038f

        // Head oval - TALLER/MORE ELONGATED (not circular)
        val headPath = Path().apply {
            addOval(
                RectF(
                    w * 0.28f,      // left
                    h * 0.05f,      // top - adjusted to start higher in available space
                    w * 0.72f,      // right
                    h * 0.45f       // bottom - oval shaped
                ),
                Path.Direction.CW
            )
        }
        drawDottedPath(canvas, headPath, dotRadius, spacing)

        // Neck sides (curved, starting from lower on head)
        val leftNeckPath = Path().apply {
            moveTo(w * 0.425f, h * 0.43f)
            cubicTo(
                w * 0.410f, h * 0.48f,
                w * 0.405f, h * 0.53f,
                w * 0.410f, h * 0.58f
            )
        }
        val rightNeckPath = Path().apply {
            moveTo(w * 0.575f, h * 0.43f)
            cubicTo(
                w * 0.590f, h * 0.48f,
                w * 0.595f, h * 0.53f,
                w * 0.590f, h * 0.58f
            )
        }
        drawDottedPath(canvas, leftNeckPath, dotRadius, spacing)
        drawDottedPath(canvas, rightNeckPath, dotRadius, spacing)

        // Collar curve (adjusted for better proportion)
        val collarPath = Path().apply {
            addArc(
                RectF(
                    w * 0.380f,
                    h * 0.555f,
                    w * 0.620f,
                    h * 0.705f
                ),
                0f,
                180f
            )
        }
        drawDottedPath(canvas, collarPath, dotRadius, spacing)

        // Shoulders - extended smooth curves reaching screen edges and going LOWER
        val leftShoulderPath = Path().apply {
            moveTo(0f, h * 0.78f)          // Start from left edge, lower position
            cubicTo(
                w * 0.15f, h * 0.70f,
                w * 0.28f, h * 0.72f,
                w * 0.410f, h * 0.58f
            )
        }
        val rightShoulderPath = Path().apply {
            moveTo(w, h * 0.78f)           // Start from right edge, lower position
            cubicTo(
                w * 0.85f, h * 0.70f,
                w * 0.72f, h * 0.72f,
                w * 0.590f, h * 0.58f
            )
        }
        drawDottedPath(canvas, leftShoulderPath, dotRadius, spacing)
        drawDottedPath(canvas, rightShoulderPath, dotRadius, spacing)
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
