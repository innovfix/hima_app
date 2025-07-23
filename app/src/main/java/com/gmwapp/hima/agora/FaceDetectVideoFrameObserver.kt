package com.gmwapp.hima.agora

import android.content.Context
import android.util.Log
import com.gmwapp.hima.agora.female.FemaleAudioCallingActivity
import com.gmwapp.hima.agora.female.FemaleVideoCallingActivity
import com.gmwapp.hima.agora.male.MaleAudioCallingActivity
import com.gmwapp.hima.agora.male.MaleVideoCallingActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import io.agora.base.VideoFrame
import io.agora.rtc2.video.IVideoFrameObserver

class FaceDetectVideoFrameObserver(private val context: Context) : IVideoFrameObserver {

    var count = 0
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    @Volatile
    private var isProcessing = false

    override fun onCaptureVideoFrame(sourceType: Int, videoFrame: VideoFrame?): Boolean {
        if (isProcessing) {
            return true // skip frame if still processing previous
        }
        val buffer = videoFrame?.buffer ?: return true
        val i420Buffer = buffer.toI420()

        val width = i420Buffer.width
        val height = i420Buffer.height

        val nv21 = ByteArray(width * height * 3 / 2)

        // Copy Y plane
        val yPlane = i420Buffer.dataY
        val yStride = i420Buffer.strideY
        for (row in 0 until height) {
            yPlane.position(row * yStride)
            yPlane.get(nv21, row * width, width)
        }

        // Copy VU plane interleaved
        val uPlane = i420Buffer.dataU
        val vPlane = i420Buffer.dataV
        val uStride = i420Buffer.strideU
        val vStride = i420Buffer.strideV

        val chromaHeight = height / 2
        val chromaWidth = width / 2

        var pos = width * height
        for (row in 0 until chromaHeight) {
            vPlane.position(row * vStride)
            uPlane.position(row * uStride)
            for (col in 0 until chromaWidth) {
                nv21[pos++] = vPlane.get()
                nv21[pos++] = uPlane.get()
            }
        }

        val rotation = videoFrame.rotation

        val image = InputImage.fromByteArray(
            nv21,
            width,
            height,
            rotation,
            InputImage.IMAGE_FORMAT_NV21
        )

        isProcessing = true

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    Log.d("FaceDetection", "Show your face")
                    count++
                    if (count==8){
                        (context as? MaleVideoCallingActivity)?.disableVideo()
                        (context as? MaleAudioCallingActivity)?.disableVideo()

                        (context as? FemaleVideoCallingActivity)?.disableVideo()
                        (context as? FemaleAudioCallingActivity)?.disableVideo()

                    }
                } else {
                    Log.d("FaceDetection", "Face Detected")
                    count= 0
                    (context as? MaleVideoCallingActivity)?.enableVideo()
                    (context as? MaleAudioCallingActivity)?.enableVideo()

                    (context as? FemaleVideoCallingActivity)?.enableVideo()
                    (context as? FemaleAudioCallingActivity)?.enableVideo()

                }
                isProcessing = false
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Face detection failed", e)
                isProcessing = false
            }

        i420Buffer.release()

        return true
    }

    // Implement other required overrides or stub with default return values
    override fun onPreEncodeVideoFrame(sourceType: Int, videoFrame: VideoFrame?) = true
    override fun onMediaPlayerVideoFrame(videoFrame: VideoFrame?, mediaPlayerId: Int) = true
    override fun getVideoFrameProcessMode() = IVideoFrameObserver.PROCESS_MODE_READ_ONLY
    override fun getVideoFormatPreference() = IVideoFrameObserver.VIDEO_PIXEL_I420
    override fun getRotationApplied() = true
    override fun getMirrorApplied() = false
    override fun getObservedFramePosition() = IVideoFrameObserver.POSITION_POST_CAPTURER
    override fun onRenderVideoFrame(channelId: String?, uid: Int, videoFrame: VideoFrame?) = true

}
