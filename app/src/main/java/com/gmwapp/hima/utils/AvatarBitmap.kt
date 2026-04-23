package com.gmwapp.hima.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * Draws a WhatsApp-style placeholder avatar: a filled circle (colour hashed off
 * the name so the same sender always gets the same colour) with the name's
 * first character centred in white. Used as a bitmap fallback when no remote
 * avatar URL is available — Android Conversation notifications ignore Resource
 * icons on a [android.app.Person], substituting a generic grey silhouette, so
 * every call site that feeds into MessagingStyle needs an actual [Bitmap].
 */
object AvatarBitmap {
    fun circleWithInitial(
        name: String,
        size: Int = 256
    ): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorForSeed(name)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bg)

        val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
        }
        val bounds = Rect()
        text.getTextBounds(letter, 0, letter.length, bounds)
        val yOffset = (size / 2f) + bounds.height() / 2f - bounds.bottom
        canvas.drawText(letter, size / 2f, yOffset, text)
        return bmp
    }

    private fun colorForSeed(seed: String): Int {
        val hue = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.85f))
    }
}
