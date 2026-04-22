package com.gmwapp.hima.activities

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityStarCreatorApplicationBinding
import com.gmwapp.hima.retrofit.responses.StarCreatorSpeechResponse
import com.gmwapp.hima.utils.applySystemBarInsets
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.naman14.androidlame.Mp3Recorder
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import kotlin.math.ceil

@AndroidEntryPoint
class StarCreatorApplicationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStarCreatorApplicationBinding
    private val profileViewModel: ProfileViewModel by viewModels()

    private enum class CallMode { AUDIO, VIDEO, BOTH }

    private var selectedCallMode: CallMode? = null
    private var audioFile: File? = null
    private var mediaPlayer: MediaPlayer? = null
    private var mp3Recorder: Mp3Recorder? = null

    @Volatile private var isRecording = false
    private var isPlaying = false
    private var recordTimer: CountDownTimer? = null
    private var elapsedSecs = 0
    private var micRippleAnimator: ValueAnimator? = null
    private var videoUri: Uri? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Populated from API; fallback to string resource until loaded
    private var audioSpeechText: String = ""
    private var videoSpeechText: String = ""

    private val videoRecordLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.getParcelableExtra<Uri>(StarCreatorVideoRecordActivity.EXTRA_VIDEO_URI)
                if (uri != null) {
                    videoUri = uri
                    binding.llVideoSuccess.isVisible = true
                    binding.btnOpenVideoRecorder.text = getString(R.string.star_creator_record_again)
                    binding.tvVideoStatus.isVisible = false
                }
            }
        }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(this, R.string.star_creator_perm_audio, Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStarCreatorApplicationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, R.color.white, applyIme = true, darkStatusBarIcons = true)

        binding.ivBack.setOnClickListener { finish() }
        setupChips()
        setupAudioRecorder()
        setupVideoSection()
        loadSpeechTextFromApi()

        binding.btnContinue.setOnClickListener {
            if (validateForm()) submitApplication()
        }

        observeSubmit()
    }

    // ─── Submit ──────────────────────────────────────────────────────────────

    private fun submitApplication() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        val experience = binding.etExperience.text?.toString()?.trim().orEmpty()
        val callPref = selectedCallMode!!.name.lowercase()

        val audioPart: MultipartBody.Part? = audioFile?.takeIf { it.exists() }?.let { file ->
            val body = file.asRequestBody("audio/mpeg".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("audio_recording", file.name, body)
        }

        val videoPart: MultipartBody.Part? = videoUri?.let { uri ->
            try {
                val stream = contentResolver.openInputStream(uri) ?: return@let null
                val tempFile = File.createTempFile("video_", ".mp4", cacheDir)
                tempFile.outputStream().use { stream.copyTo(it) }
                val body = tempFile.asRequestBody("video/mp4".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("video_recording", tempFile.name, body)
            } catch (e: Exception) {
                Log.e("StarCreator", "Video part error: ${e.message}")
                null
            }
        }

        setSubmitLoading(true)
        profileViewModel.submitStarCreatorApplication(
            userId, experience, callPref, audioPart, videoPart
        )
    }

    private fun observeSubmit() {
        profileViewModel.starCreatorSubmitLiveData.observe(this) { response ->
            setSubmitLoading(false)
            showSuccessDialog(response.message)
        }
        profileViewModel.starCreatorSubmitErrorLiveData.observe(this) { error ->
            setSubmitLoading(false)
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }
    }

    private fun setSubmitLoading(loading: Boolean) {
        binding.btnContinue.isEnabled = !loading
        binding.btnContinue.alpha = if (loading) 0.6f else 1f
        binding.btnContinue.text = if (loading)
            getString(R.string.star_creator_submitting)
        else
            getString(R.string.star_creator_submit)
    }

    private fun showSuccessDialog(message: String) {
        val dialog = Dialog(this, R.style.FullWidthDialog)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        val view = layoutInflater.inflate(R.layout.dialog_star_creator_success, null)
        view.findViewById<TextView>(R.id.tv_dialog_message).text = message
        view.findViewById<TextView>(R.id.btn_dialog_done).setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    // ─── API load ────────────────────────────────────────────────────────────

    private fun loadSpeechTextFromApi() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        profileViewModel.getStarCreatorSpeechText(userId)

        profileViewModel.starCreatorSpeechLiveData.observe(this) { data ->
            data ?: return@observe
            applyApiData(data)
        }

        profileViewModel.starCreatorSpeechErrorLiveData.observe(this) { error ->
            // Fallback text already set; silently continue
            Log.w("StarCreator", "Speech text API error: $error")
        }
    }

    private fun applyApiData(data: StarCreatorSpeechResponse) {
        // Speech texts
        data.audio_speech_text?.text?.takeIf { it.isNotBlank() }?.let { audioSpeechText = it }
        data.video_speech_text?.text?.takeIf { it.isNotBlank() }?.let { videoSpeechText = it }

        // Question 1
        data.question_1?.let { q ->
            // The step card label & sublabel are TextViews inside the first card —
            // we update them directly since they are static layout views.
            binding.tvQ1Label.text = q.title
            binding.tvQ1Sublabel.text = q.subtitle
            binding.tvQ1Question.text = q.question
        }

        // Question 2
        data.question_2?.let { q ->
            binding.tvQ2Label.text = q.title
            binding.tvQ2Sublabel.text = q.subtitle
            binding.tvQ2Question.text = q.question
        }

        // Refresh speech text if user already picked a mode
        selectedCallMode?.let { updateRecordSection(it) }
    }

    // ─── Chips ──────────────────────────────────────────────────────────────

    private fun setupChips() {
        binding.chipAudio.setOnClickListener { selectMode(CallMode.AUDIO) }
        binding.chipVideo.setOnClickListener { selectMode(CallMode.VIDEO) }
        binding.chipBoth.setOnClickListener { selectMode(CallMode.BOTH) }
    }

    private fun selectMode(mode: CallMode) {
        selectedCallMode = mode
        val white = ContextCompat.getColor(this, R.color.white)
        val pink = ContextCompat.getColor(this, R.color.pink_bold)

        fun style(chip: TextView, active: Boolean) {
            chip.setBackgroundResource(if (active) R.drawable.star_creator_chip_selected else R.drawable.star_creator_chip_unselected)
            chip.setTextColor(if (active) white else pink)
        }
        style(binding.chipAudio, mode == CallMode.AUDIO)
        style(binding.chipVideo, mode == CallMode.VIDEO)
        style(binding.chipBoth, mode == CallMode.BOTH)
        updateRecordSection(mode)
    }

    private fun updateRecordSection(mode: CallMode) {
        // Stop any ongoing recording before switching modes
        if (isRecording) safeStopRecorder()

        binding.cardRecordSection.isVisible = true

        when (mode) {
            CallMode.AUDIO -> {
                binding.tvSpeechText.text = audioSpeechText
                binding.tvRecordStepTitle.text = getString(R.string.star_creator_audio_instruction_title)
                binding.tvRecordStepSubtitle.text = getString(R.string.star_creator_audio_step_sub)
                binding.llAudioRecord.isVisible = true
                binding.llVideoRecord.isVisible = false
            }
            CallMode.VIDEO, CallMode.BOTH -> {
                binding.tvSpeechText.text = videoSpeechText
                binding.tvRecordStepTitle.text = getString(R.string.star_creator_video_instruction_title)
                binding.tvRecordStepSubtitle.text = getString(R.string.star_creator_video_step_sub)
                binding.llAudioRecord.isVisible = false
                binding.llVideoRecord.isVisible = true
            }
        }
        resetAudioUI()
    }

    // ─── Audio recording — tap-to-start / tap-again-to-stop ─────────────────

    private fun setupAudioRecorder() {
        // Single click toggles record / stop — far more reliable than tap-and-hold
        binding.flMicBtn.setOnClickListener {
            if (isRecording) stopAudioRecording() else startAudioRecording()
        }
        binding.ivPlayPause.setOnClickListener {
            if (isPlaying) pauseAudio() else playAudio()
        }
        binding.tvRecordAgain.setOnClickListener { resetAudioUI() }
    }

    private fun hasAudioPermission(): Boolean {
        val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!ok) audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        return ok
    }

    private fun startAudioRecording() {
        if (isRecording) return            // guard against double-tap
        if (!hasAudioPermission()) return

        // Release any previous recorder safely
        safeStopRecorder()

        // Create output file
        try {
            audioFile = File.createTempFile(DConstants.AUDIO_FILE, ".mp3", cacheDir)
                .also { it.setReadable(true, false) }
        } catch (e: IOException) {
            Log.e("StarCreator", "Temp file: ${e.message}")
            Toast.makeText(this, R.string.please_try_again_later, Toast.LENGTH_SHORT).show()
            return
        }

        // Start Mp3Recorder
        val recorder = Mp3Recorder(audioFile)
        try {
            recorder.start()
        } catch (e: Exception) {
            Log.e("StarCreator", "Recorder start: ${e.message}")
            audioFile?.delete(); audioFile = null
            Toast.makeText(this, R.string.please_try_again_later, Toast.LENGTH_SHORT).show()
            return
        }
        mp3Recorder = recorder
        isRecording = true

        // UI — keep the mic pink (active shade), just animate ripple
        binding.flMicBtn.setBackgroundResource(R.drawable.star_creator_mic_bg)
        binding.tvMicHint.text = getString(R.string.star_creator_tap_to_stop)
        binding.tvTimer.isVisible = true
        binding.tvTimer.text = "00:00"
        binding.llPlayback.isVisible = false
        binding.tvRecordAgain.isVisible = false
        startMicRipple()

        elapsedSecs = 0
        recordTimer?.cancel()
        recordTimer = object : CountDownTimer(60 * 60_000L, 1000) {
            override fun onTick(p0: Long) {
                if (!isRecording || isDestroyed) { cancel(); return }
                elapsedSecs++
                if (!isDestroyed) binding.tvTimer.text = "%02d:%02d".format(elapsedSecs / 60, elapsedSecs % 60)
            }
            override fun onFinish() { if (isRecording && !isDestroyed) stopAudioRecording() }
        }.start()
    }

    private fun stopAudioRecording() {
        if (!isRecording) return
        isRecording = false
        recordTimer?.cancel()
        stopMicRipple()
        safeStopRecorder()

        binding.tvTimer.isVisible = false

        // Check recording is long enough
        val file = audioFile
        if (file == null || !file.exists() || file.length() == 0L) {
            Toast.makeText(this, R.string.star_creator_audio_too_short, Toast.LENGTH_SHORT).show()
            resetAudioUI(); return
        }

        val durationSecs: Int = try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            val secs = ceil(mp.duration / 1000.0).toInt()
            mp.release()
            secs
        } catch (e: Exception) {
            Log.e("StarCreator", "Duration check: ${e.message}")
            0
        }

        if (durationSecs < 3) {
            Toast.makeText(this, R.string.star_creator_audio_too_short, Toast.LENGTH_SHORT).show()
            audioFile?.delete(); audioFile = null
            resetAudioUI(); return
        }

        // Good recording — show playback
        binding.tvMicHint.text = getString(R.string.star_creator_recording_done)
        binding.llPlayback.isVisible = true
        binding.tvRecordAgain.isVisible = true

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer()
        try {
            mediaPlayer?.setDataSource(file.absolutePath)
            mediaPlayer?.prepare()
            binding.pbAudio.max = mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            Log.e("StarCreator", "MediaPlayer setup: ${e.message}")
        }
    }

    /** Safely stop and discard the Mp3Recorder without crashing */
    private fun safeStopRecorder() {
        val recorder = mp3Recorder
        mp3Recorder = null          // null first so no other thread touches it
        try { recorder?.stop() } catch (_: Exception) {}
    }

    // ─── Playback ────────────────────────────────────────────────────────────

    private fun playAudio() {
        try {
            mediaPlayer?.start()
            isPlaying = true
            binding.ivPlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pause))
            trackAudioProgress()
        } catch (e: Exception) { Log.e("StarCreator", "Play: ${e.message}") }
    }

    private fun pauseAudio() {
        try {
            mediaPlayer?.pause()
            isPlaying = false
            binding.ivPlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.play))
        } catch (e: Exception) { Log.e("StarCreator", "Pause: ${e.message}") }
    }

    private fun trackAudioProgress() {
        val runnable = object : Runnable {
            override fun run() {
                val mp = mediaPlayer ?: return
                try {
                    val pos = mp.currentPosition
                    val total = mp.duration
                    binding.pbAudio.progress = pos
                    val s = pos / 1000; val t = total / 1000
                    binding.tvPlaybackTime.text = "%d:%02d / %d:%02d".format(s / 60, s % 60, t / 60, t % 60)
                    if (isPlaying && pos < total) {
                        mainHandler.postDelayed(this, 100)
                    } else if (pos >= total) {
                        isPlaying = false
                        binding.ivPlayPause.setImageDrawable(ContextCompat.getDrawable(this@StarCreatorApplicationActivity, R.drawable.play))
                        mp.seekTo(0)
                        binding.pbAudio.progress = 0
                    }
                } catch (_: Exception) {}
            }
        }
        mainHandler.postDelayed(runnable, 100)
    }

    // ─── Reset audio UI ──────────────────────────────────────────────────────

    private fun resetAudioUI() {
        // Stop recorder if running
        if (isRecording) { isRecording = false; recordTimer?.cancel(); safeStopRecorder() }
        stopMicRipple()

        // Release player
        mediaPlayer?.release(); mediaPlayer = null
        audioFile?.delete(); audioFile = null
        isPlaying = false

        binding.flMicBtn.setBackgroundResource(R.drawable.star_creator_mic_bg)
        binding.tvMicHint.text = getString(R.string.star_creator_tap_record)
        binding.tvTimer.isVisible = false
        binding.llPlayback.isVisible = false
        binding.tvRecordAgain.isVisible = false
    }

    private fun startMicRipple() {
        micRippleAnimator?.cancel()
        micRippleAnimator = ObjectAnimator.ofFloat(binding.ivMicRipple1, "alpha", 0.05f, 0.45f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopMicRipple() {
        micRippleAnimator?.cancel()
        micRippleAnimator = null
        binding.ivMicRipple1.alpha = 0.15f
    }

    // ─── Video section ───────────────────────────────────────────────────────

    private fun setupVideoSection() {
        binding.btnOpenVideoRecorder.setOnClickListener {
            val intent = Intent(this, StarCreatorVideoRecordActivity::class.java).apply {
                putExtra(StarCreatorVideoRecordActivity.EXTRA_SPEECH_TEXT, videoSpeechText)
            }
            videoRecordLauncher.launch(intent)
        }

        binding.btnPlayVideoPreview.setOnClickListener {
            videoUri?.let { showVideoPreviewDialog(it) }
        }
    }

    private fun showVideoPreviewDialog(uri: Uri) {
        val dialog = Dialog(this, R.style.FullWidthDialog)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        val view = layoutInflater.inflate(R.layout.dialog_video_preview, null)

        val videoView = view.findViewById<android.widget.VideoView>(R.id.vv_preview)
        val loading = view.findViewById<android.widget.ProgressBar>(R.id.pb_video_loading)
        val btnPlayPause = view.findViewById<TextView>(R.id.btn_preview_play_pause)
        val btnReplay = view.findViewById<TextView>(R.id.btn_preview_replay)
        val btnClose = view.findViewById<TextView>(R.id.btn_close_preview)

        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mp ->
            loading.isVisible = false
            mp.isLooping = false
            videoView.start()
            btnPlayPause.text = "⏸  Pause"
        }
        videoView.setOnCompletionListener {
            btnPlayPause.text = "▶  Play"
        }

        btnPlayPause.setOnClickListener {
            if (videoView.isPlaying) {
                videoView.pause()
                btnPlayPause.text = "▶  Play"
            } else {
                videoView.start()
                btnPlayPause.text = "⏸  Pause"
            }
        }

        btnReplay.setOnClickListener {
            videoView.seekTo(0)
            videoView.start()
            btnPlayPause.text = "⏸  Pause"
        }

        btnClose.setOnClickListener {
            videoView.stopPlayback()
            dialog.dismiss()
        }

        dialog.setOnDismissListener { videoView.stopPlayback() }

        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    // ─── Validation ──────────────────────────────────────────────────────────

    private fun validateForm(): Boolean {
        val experience = binding.etExperience.text?.toString()?.trim().orEmpty()
        val mode = selectedCallMode
        if (experience.isEmpty()) {
            Toast.makeText(this, R.string.star_creator_error_experience, Toast.LENGTH_SHORT).show()
            return false
        }
        if (mode == null) {
            Toast.makeText(this, R.string.star_creator_error_call_preference, Toast.LENGTH_SHORT).show()
            return false
        }
        if (mode == CallMode.AUDIO && audioFile == null) {
            Toast.makeText(this, R.string.star_creator_error_audio_upload, Toast.LENGTH_SHORT).show()
            return false
        }
        if ((mode == CallMode.VIDEO || mode == CallMode.BOTH) && videoUri == null) {
            Toast.makeText(this, R.string.star_creator_error_video_upload, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        recordTimer?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        safeStopRecorder()
        mediaPlayer?.release()
        micRippleAnimator?.cancel()
    }
}
