package com.gmwapp.hima.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object ImageCompressor {

    fun compress(
        context: Context,
        uri: Uri,
        maxDimPx: Int = 1280,
        quality: Int = 75
    ): File {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: throw IllegalStateException("Unable to decode selected image")

        val rotatedBitmap = applyExifRotation(context, uri, decodedBitmap)
        val finalBitmap = scaleDown(rotatedBitmap, maxDimPx)

        val outFile = File(context.cacheDir, "chat_img_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { output ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        }

        if (finalBitmap !== rotatedBitmap && !rotatedBitmap.isRecycled) {
            rotatedBitmap.recycle()
        }
        if (rotatedBitmap !== decodedBitmap && !decodedBitmap.isRecycled) {
            decodedBitmap.recycle()
        } else if (rotatedBitmap === decodedBitmap && finalBitmap !== decodedBitmap && !decodedBitmap.isRecycled) {
            decodedBitmap.recycle()
        }

        return outFile
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimPx: Int): Int {
        var sampleSize = 1
        var workingWidth = width
        var workingHeight = height
        while (workingWidth > maxDimPx * 2 || workingHeight > maxDimPx * 2) {
            sampleSize *= 2
            workingWidth /= 2
            workingHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun applyExifRotation(context: Context, uri: Uri, source: Bitmap): Bitmap {
        val exifRotation = context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val rotationDegrees = when (exifRotation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        if (rotationDegrees == 0f) return source

        val matrix = Matrix().apply {
            postRotate(rotationDegrees)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap, maxDimPx: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val largest = maxOf(width, height)
        if (largest <= maxDimPx) return bitmap

        val scale = maxDimPx.toFloat() / largest.toFloat()
        val targetWidth = (width * scale).toInt()
        val targetHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
