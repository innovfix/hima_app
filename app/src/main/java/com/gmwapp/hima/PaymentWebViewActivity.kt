package com.gmwapp.hima

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.viewmodels.LudoFcmViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PaymentWebViewActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_IS_LUDO_GAME = "extra_is_ludo_game"
        const val EXTRA_LUDO_FROM_USER_ID = "extra_ludo_from_user_id"
        const val EXTRA_LUDO_TO_USER_ID = "extra_ludo_to_user_id"
        const val EXTRA_LUDO_CALL_ID = "extra_ludo_call_id"
        const val EXTRA_LUDO_INVITE_ID = "extra_ludo_invite_id"

        fun createLudoIntent(
            context: Context,
            url: String,
            fromUserId: Int,
            toUserId: Int,
            callId: String?,
            inviteId: String?
        ): Intent {
            return Intent(context, PaymentWebViewActivity::class.java).apply {
                data = Uri.parse(url)
                putExtra(EXTRA_IS_LUDO_GAME, true)
                putExtra(EXTRA_LUDO_FROM_USER_ID, fromUserId)
                putExtra(EXTRA_LUDO_TO_USER_ID, toUserId)
                putExtra(EXTRA_LUDO_CALL_ID, callId)
                putExtra(EXTRA_LUDO_INVITE_ID, inviteId)
            }
        }
    }

    private val ludoFcmViewModel: LudoFcmViewModel by viewModels()
    private lateinit var webView: WebView
    private var isLudoGameScreen: Boolean = false
    private var ludoFromUserId: Int = 0
    private var ludoToUserId: Int = 0
    private var ludoCallId: String? = null
    private var ludoInviteId: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_payment_web_view)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        webView = findViewById(R.id.webview)

        // Get URL from Intent
        val url = intent?.data.toString()
        isLudoGameScreen = intent.getBooleanExtra(EXTRA_IS_LUDO_GAME, false)
        ludoFromUserId = intent.getIntExtra(EXTRA_LUDO_FROM_USER_ID, 0)
        ludoToUserId = intent.getIntExtra(EXTRA_LUDO_TO_USER_ID, 0)
        ludoCallId = intent.getStringExtra(EXTRA_LUDO_CALL_ID)
        ludoInviteId = intent.getStringExtra(EXTRA_LUDO_INVITE_ID)

        // Configure WebView settings
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.webChromeClient = WebChromeClient()

        // Set WebViewClient with UPI Intent Handling
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()

                return if (url.startsWith("upi://") || url.startsWith("intent://")) {
                    try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        startActivity(intent)
                        true // Prevent WebView from loading the UPI link
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this@PaymentWebViewActivity, "No UPI app found", Toast.LENGTH_SHORT).show()
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        true
                    }
                } else {
                    false // Let WebView handle normal URLs
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isLudoGameScreen) {
                    showEndLudoDialog()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        FcmUtils.ludoEvent.observe(this) { event ->
            if (event == null) return@observe
            if (isLudoGameScreen && event.type == "game_end") {
                Toast.makeText(this, "Ludo game ended", Toast.LENGTH_SHORT).show()
                FcmUtils.clearLudoEvent()
                finish()
            }
        }

        // Load the URL
        if (url.isNotEmpty() && url.startsWith("http")) {
            webView.loadUrl(url)
        } else {
            Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showEndLudoDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.dialog_ludo_end_game, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(true)

        view.findViewById<TextView>(R.id.btn_keep_playing).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<TextView>(R.id.btn_end_game).setOnClickListener {
            dialog.dismiss()
            sendGameEndAndExit()
        }
        dialog.show()
    }

    private fun sendGameEndAndExit() {
        if (ludoFromUserId > 0 && ludoToUserId > 0) {
            ludoFcmViewModel.sendLudoFcm(
                action = "game_end",
                fromUserId = ludoFromUserId,
                toUserId = ludoToUserId,
                inviteId = ludoInviteId,
                callId = ludoCallId
            )
        }
        finish()
    }
}
