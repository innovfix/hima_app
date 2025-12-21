package com.gmwapp.hima.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.gmwapp.hima.R

class LudoGameActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var gameUrl: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ludo_game)

        gameUrl = intent.getStringExtra("GAME_URL") ?: ""

        webView = findViewById(R.id.webView)
        
        // Configure WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        // Set WebViewClient to handle page navigation
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // Load URLs within WebView
                url?.let {
                    if (it.startsWith("http://") || it.startsWith("https://")) {
                        view?.loadUrl(it)
                        return true
                    }
                }
                return false
            }
        }

        // Load game URL
        if (gameUrl.isNotEmpty()) {
            webView.loadUrl(gameUrl)
        } else {
            webView.loadData(
                "<html><body><h1>Error: Game URL not provided</h1></body></html>",
                "text/html",
                "UTF-8"
            )
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}


