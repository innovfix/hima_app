package com.gmwapp.hima.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityStarCreatorVideoRecordBinding
import com.gmwapp.hima.utils.applySystemBarInsets
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class StarCreatorVideoRecordActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_SPEECH_TEXT = "extra_speech_text"
        private val REQUIRED_PERMS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    private lateinit var binding: ActivityStarCreatorVideoRecordBinding
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var isRecording = false
    private var elapsedSecs = 0
    private var recordTimer: CountDownTimer? = null
    private var recordedUri: Uri? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            if (perms.all { it.value }) startCamera()
            else { Toast.makeText(this, R.string.star_creator_perm_camera, Toast.LENGTH_SHORT).show(); finish() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStarCreatorVideoRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, R.color.black)

        // Show speech text so user can read while recording
        val speechText = intent.getStringExtra(EXTRA_SPEECH_TEXT).orEmpty()
        binding.tvSpeechOverlay.text = speechText
        binding.llSpeechOverlay.isVisible = speechText.isNotEmpty()

        if (REQUIRED_PERMS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startCamera()
        } else {
            permissionLauncher.launch(REQUIRED_PERMS)
        }

        binding.ivClose.setOnClickListener { finish() }
        binding.ivFlipCamera.setOnClickListener { flipCamera() }
        binding.ivRecordBtn.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }
        binding.tvRetake.setOnClickListener { retake() }
        binding.tvUseVideo.setOnClickListener { useVideo() }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
            } catch (e: Exception) {
                Log.e("StarCreatorVideo", "Camera bind: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val vc = videoCapture ?: return
        val outputFile = File(
            cacheDir,
            "creator_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.mp4"
        )
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        recording = vc.output
            .prepareRecording(this, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> runOnUiThread { onRecordingStarted() }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        if (!event.hasError()) {
                            recordedUri = Uri.fromFile(outputFile)
                            runOnUiThread { onRecordingFinished() }
                        } else {
                            Log.e("StarCreatorVideo", "Finalize error: ${event.error}")
                            outputFile.delete()
                        }
                    }
                    else -> Unit
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
        recordTimer?.cancel()
    }

    private fun onRecordingStarted() {
        isRecording = true
        // Switch button icon to "stop" by using mic_bg (lighter) while recording
        binding.ivRecordBtn.setBackgroundResource(R.drawable.star_creator_mic_bg)
        binding.llTimerBadge.visibility = View.VISIBLE
        binding.tvRecordHint.text = getString(R.string.star_creator_tap_stop)
        elapsedSecs = 0
        recordTimer = object : CountDownTimer(5 * 60 * 1000L, 1000) {
            override fun onTick(p0: Long) {
                elapsedSecs++
                binding.tvRecordTimer.text = "%02d:%02d".format(elapsedSecs / 60, elapsedSecs % 60)
            }
            override fun onFinish() { stopRecording() }
        }.start()
    }

    private fun onRecordingFinished() {
        binding.ivRecordBtn.setBackgroundResource(R.drawable.star_creator_mic_bg_recording)
        binding.llTimerBadge.visibility = View.GONE
        binding.llRecordedBadge.visibility = View.VISIBLE
        binding.tvRetake.visibility = View.VISIBLE
        binding.tvUseVideo.visibility = View.VISIBLE
        binding.ivFlipCamera.visibility = View.GONE
        binding.tvRecordHint.text = getString(R.string.star_creator_video_ready)
    }

    private fun retake() {
        recordedUri = null
        binding.llRecordedBadge.visibility = View.GONE
        binding.tvRetake.visibility = View.GONE
        binding.tvUseVideo.visibility = View.GONE
        binding.ivFlipCamera.visibility = View.VISIBLE
        binding.tvRecordHint.text = getString(R.string.star_creator_tap_to_record_video)
        startCamera()
    }

    private fun flipCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
            CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        startCamera()
    }

    private fun useVideo() {
        val uri = recordedUri ?: return
        setResult(RESULT_OK, Intent().putExtra(EXTRA_VIDEO_URI, uri))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        recording?.stop()
        recordTimer?.cancel()
    }
}
