package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.gmwapp.hima.R
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.BottomSheetVoiceIdentificationBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.naman14.androidlame.Mp3Recorder
import java.io.File
import java.io.IOException
import kotlin.math.ceil


class BottomSheetVoiceIdentification : BottomSheetDialogFragment() {
    private var isPlaying = false
    private var audiofile: File? = null
    private var mediaPlayer: MediaPlayer? = null
    private var onItemSelectionListener: OnItemSelectionListener<String?>? = null
    private var mRecorder: Mp3Recorder? = null
    lateinit var binding: BottomSheetVoiceIdentificationBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetVoiceIdentificationBinding.inflate(layoutInflater)

        initUI()
        binding.tvSpeechText.text = arguments?.getString(DConstants.TEXT)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            onItemSelectionListener = activity as OnItemSelectionListener<String?>
        } catch (e: ClassCastException) {
            Log.e("BottomSheetCountry", "onAttach: ClassCastException: " + e.message)
        }
    }


    override fun getTheme(): Int = R.style.BottomSheetDialogTheme
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), theme)

    private fun initUI() {
        var timer:CountDownTimer?=null;
        mediaPlayer = MediaPlayer()
        binding.clMicrophone.setOnTouchListener(OnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                binding.tlSpeechTextHintTimer.text = "00:00"
                binding.tlSpeechTextHintTimer.visibility = View.VISIBLE
                var secs=0;
                timer = object : CountDownTimer(60 * 60000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        secs++;
                        val mins = secs/60;
                        val remainingSecs = secs%60;
                        val result = (if(mins>9) mins.toString() else "0"+mins)+":"+(if(remainingSecs>9) remainingSecs.toString() else "0"+remainingSecs)
                        binding.tlSpeechTextHintTimer.text = result
                    }

                    override fun onFinish() {
                    }
                }.start()

                binding.content.startRippleAnimation()
                binding.tlSpeechTextHint.text = getString(R.string.release_to_stop)
                binding.ivMicroPhoneCircle.setImageDrawable(resources.getDrawable(R.drawable.ic_microphone_circle_selected))
                val mConstrainLayout = binding.content
                val lp = mConstrainLayout.layoutParams as ConstraintLayout.LayoutParams
                lp.matchConstraintPercentHeight = 0.35.toFloat()
                lp.matchConstraintPercentWidth = 0.49.toFloat()
                mConstrainLayout.layoutParams = lp
                startRecording()
            } else if (event.action == MotionEvent.ACTION_UP) {
                timer?.cancel()
                binding.tlSpeechTextHintTimer.visibility = View.GONE
                binding.tlSpeechTextHint.text = getString(R.string.tap_and_hold_to_speak)
                binding.content.stopRippleAnimation()
                binding.ivMicroPhoneCircle.setImageDrawable(resources.getDrawable(R.drawable.ic_microphone_circle))

                try {
                    mRecorder?.stop()

                    // Reset and prepare MediaPlayer for new recording
                    mediaPlayer?.reset()
                    mediaPlayer?.setDataSource(audiofile?.absolutePath)
                    mediaPlayer?.prepare()

                    // Check recording duration
                    val durationInMillis = mediaPlayer?.duration ?: 0
                    val durationInSeconds = ceil(durationInMillis / 1000.0).toInt()

                    if (durationInSeconds < 3) {
                        Toast.makeText(context, "Recording must be above 3 seconds", Toast.LENGTH_SHORT).show()
                        audiofile?.delete() // Delete short recording
                        return@OnTouchListener true
                    }else if (durationInSeconds < 1){
                        Toast.makeText(context, "Recording must be above 3 seconds", Toast.LENGTH_SHORT).show()

                    }


                    else if (durationInSeconds == 3){
                        binding.tvPlayToListen.visibility = View.VISIBLE
                        binding.clPlayer.visibility = View.VISIBLE
                        binding.clRecordAgain.visibility = View.VISIBLE
                        binding.btnSubmit.visibility = View.VISIBLE
                        binding.tlSpeechTextHint.visibility = View.GONE
                        binding.clMicrophone.visibility = View.GONE
                    }else if (durationInSeconds > 3){
                        binding.tvPlayToListen.visibility = View.VISIBLE
                        binding.clPlayer.visibility = View.VISIBLE
                        binding.clRecordAgain.visibility = View.VISIBLE
                        binding.btnSubmit.visibility = View.VISIBLE
                        binding.tlSpeechTextHint.visibility = View.GONE
                        binding.clMicrophone.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }


            }
            true
        })
        binding.ivPlay.setOnSingleClickListener({
            if (isPlaying) {
                binding.ivPlay.setImageDrawable(context?.getDrawable(R.drawable.play))
                mediaPlayer?.pause()
                setAudioProgress()
            } else {
                binding.ivPlay.setImageDrawable(context?.getDrawable(R.drawable.pause))
                mediaPlayer?.start()
                setAudioProgress()
            }
            isPlaying = !isPlaying
        })
        binding.clRecordAgain.setOnSingleClickListener({
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer()
            isPlaying = false;
            binding.ivPlay.setImageDrawable(context?.getDrawable(R.drawable.play))
            binding.tvPlayToListen.visibility = View.GONE
            binding.clPlayer.visibility = View.GONE
            binding.clRecordAgain.visibility = View.GONE
            binding.btnSubmit.visibility = View.GONE
            binding.tlSpeechTextHint.visibility = View.VISIBLE
            binding.clMicrophone.visibility = View.VISIBLE
        })
        binding.btnSubmit.setOnSingleClickListener({
            onItemSelectionListener?.onItemSelected(audiofile.toString())
        })

    }

    fun setAudioProgress() {
        val totalDuration = mediaPlayer?.duration

        binding.pbProgress.max = totalDuration as Int

        val handlerProgressBar = Handler()
        var runnable = object : Runnable {
            override fun run() {
                try {
                    val currentPos = mediaPlayer?.currentPosition
                    binding.pbProgress.progress = currentPos as Int
                    if(currentPos<totalDuration){
                        handlerProgressBar.postDelayed(this, 100)

                    }else{
                        isPlaying = false
                        binding.ivPlay.setImageDrawable(context?.getDrawable(R.drawable.play))
                        mediaPlayer?.seekTo(0)
                    }

                } catch (ed: IllegalStateException) {
                    ed.printStackTrace()
                }
            }
        }
        handlerProgressBar.postDelayed(runnable, 1000)
    }

    private fun startRecording() {
        val dir = context?.cacheDir
        try {
            audiofile = File.createTempFile(DConstants.AUDIO_FILE, ".mp3", dir)
            audiofile?.setReadable(true, false)
        } catch (e: IOException) {
            Log.e("TAG", "external storage access error" + e.stackTraceToString())

            return
        }

        mRecorder = Mp3Recorder(audiofile)

        try {
            mRecorder?.start()
        } catch (e: IOException) {
            Log.e("TAG", "prepare() failed" + e.stackTraceToString())
        }
    }

    private fun stopRecording(isCancelled: Boolean) {
        binding.tlSpeechTextHintTimer.visibility = View.GONE
        binding.tlSpeechTextHint.text = getString(R.string.tap_and_hold_to_speak)
        binding.content.stopRippleAnimation()
        binding.ivMicroPhoneCircle.setImageDrawable(resources.getDrawable(R.drawable.ic_microphone_circle))

        if (isCancelled) {
            mRecorder?.stop()
            audiofile?.delete() // Delete file if swipe cancelled
            return
        }

        try {
            mRecorder?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(audiofile?.absolutePath)
            mediaPlayer?.prepare()

            val durationInSeconds = ceil((mediaPlayer?.duration ?: 0) / 1000.0).toInt()
            if (durationInSeconds < 3) {
                Toast.makeText(context, "Recording must be above 3 seconds", Toast.LENGTH_SHORT).show()
                audiofile?.delete()
            } else {
                binding.tvPlayToListen.visibility = View.VISIBLE
                binding.clPlayer.visibility = View.VISIBLE
                binding.clRecordAgain.visibility = View.VISIBLE
                binding.btnSubmit.visibility = View.VISIBLE
                binding.tlSpeechTextHint.visibility = View.GONE
                binding.clMicrophone.visibility = View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}