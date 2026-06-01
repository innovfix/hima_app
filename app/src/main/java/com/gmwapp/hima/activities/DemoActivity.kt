package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.MicPermissionHelper
import com.gmwapp.hima.utils.showAppToast

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale
import android.os.Handler
import android.os.Looper

import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityDemoBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DemoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDemoBinding

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechIntent: Intent
    private val abusiveWords = listOf("idiot", "damn")
    private val recognitionHandler = Handler(Looper.getMainLooper())
    // LAI-095: gates the delayed restartRecognition() callback so it cannot
    // touch a destroyed SpeechRecognizer after the activity is finishing.
    private var isShuttingDown = false

    // LAI-107: route a denied result through the helper so the second-deny
    // permanently-denied state opens App Settings instead of looping.
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechRecognition()
        } else if (MicPermissionHelper.isPermanentlyDenied(this)) {
            MicPermissionHelper.showSettingsDeepLinkDialog(this, onCancel = { finish() })
        } else {
            showAppToast("Microphone permission is required for the demo.", Toast.LENGTH_LONG)
            finish()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.colorAccent)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // LAI-107: gate the recognizer behind a proper permission flow instead
        // of unconditionally starting and looping on ERROR_INSUFFICIENT_PERMISSIONS.
        if (MicPermissionHelper.hasMicPermission(this)) {
            startSpeechRecognition()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startSpeechRecognition() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")

        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let {
                    val spokenText = it[0].lowercase(Locale.getDefault())
                    Log.d("spokenText","$spokenText")
                    showAppToast("$spokenText", Toast.LENGTH_LONG)

                    if (abusiveWords.any { word -> spokenText.contains(word) }) {
                        disconnectAgoraCall()
                        //return
                    }
                }
                restartRecognition()
            }

            override fun onError(error: Int) {
                // LAI-107: stop the infinite restart loop when the mic is unavailable.
                // ERROR_INSUFFICIENT_PERMISSIONS means the user revoked mic permission
                // mid-session; ERROR_RECOGNIZER_BUSY / CLIENT happen during teardown.
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    if (MicPermissionHelper.isPermanentlyDenied(this@DemoActivity)) {
                        MicPermissionHelper.showSettingsDeepLinkDialog(
                            this@DemoActivity, onCancel = { finish() }
                        )
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    return
                }
                restartRecognition()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(speechIntent)
    }

    private fun restartRecognition() {
        if (isShuttingDown) return
        recognitionHandler.postDelayed({
            if (isShuttingDown) return@postDelayed
            speechRecognizer.startListening(speechIntent)
        }, 200)
    }

    private fun disconnectAgoraCall() {

        showAppToast("Call disconnected due to abusive language", Toast.LENGTH_LONG)
    }

    // LAI-095: release the SpeechRecognizer (and the underlying mic) when the
    // user backs out of the demo. Without this the recognizer keeps the mic
    // locked, and the pending postDelayed() callback in restartRecognition()
    // can fire after the activity is gone and crash on a destroyed instance.
    private fun releaseSpeechRecognizer() {
        if (isShuttingDown) return
        isShuttingDown = true
        recognitionHandler.removeCallbacksAndMessages(null)
        if (::speechRecognizer.isInitialized) {
            try {
                speechRecognizer.stopListening()
            } catch (_: Exception) {
            }
            try {
                speechRecognizer.cancel()
            } catch (_: Exception) {
            }
            try {
                speechRecognizer.destroy()
            } catch (_: Exception) {
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        releaseSpeechRecognizer()
        super.onBackPressed()
    }

    override fun onDestroy() {
        releaseSpeechRecognizer()
        super.onDestroy()
    }
}