package com.gmwapp.hima

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class PaymentWebViewActivity : AppCompatActivity() {
    private var selectedUPIPackage: String? = null
    private var orderId: String? = null
    
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_payment_web_view)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val webView = findViewById<WebView>(R.id.webview)

        // Get URL and selected UPI package from Intent
        val url = intent?.data?.toString() ?: ""
        selectedUPIPackage = intent?.getStringExtra("SELECTED_UPI_PACKAGE")
        orderId = intent?.getStringExtra("ORDER_ID")
        
        Log.d("paynowcasfree", "========== PaymentWebViewActivity Started ==========")
        Log.d("paynowcasfree", "Payment URL: $url")
        Log.d("paynowcasfree", "Selected UPI Package: $selectedUPIPackage")
        Log.d("paynowcasfree", "Order ID: $orderId")

        // Configure WebView settings
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString + " CashfreeApp"
        }

        // Add JavaScript interface to catch JavaScript-based redirects
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onUPIRedirect(upiUrl: String) {
                Log.d("paynowcasfree", "========== JavaScript UPI Redirect Detected ==========")
                Log.d("paynowcasfree", "UPI URL from JS: $upiUrl")
                runOnUiThread {
                    handleUPIIntent(upiUrl)
                }
            }
        }, "AndroidUPIHandler")

        // Set WebViewClient with UPI Intent Handling
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                Log.d("paynowcasfree", "========== WebView URL Intercepted (shouldOverrideUrlLoading) ==========")
                Log.d("paynowcasfree", "URL: $url")
                Log.d("paynowcasfree", "Method: ${request?.method}")
                Log.d("paynowcasfree", "Is Redirect: ${request?.isRedirect}")

                return handleUPIIntent(url)
            }
            
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString()
                Log.d("paynowcasfree", "========== WebView Resource Request ==========")
                Log.d("paynowcasfree", "Resource URL: $url")
                
                // Check for UPI intents in resource requests too
                if (url.startsWith("upi://") || url.startsWith("intent://") || url.startsWith("tez://") || url.startsWith("gpay://")) {
                    Log.d("paynowcasfree", "UPI Intent detected in resource request!")
                    runOnUiThread {
                        handleUPIIntent(url)
                    }
                }
                
                return super.shouldInterceptRequest(view, request)
            }
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("paynowcasfree", "========== WebView Page Started Loading ==========")
                Log.d("paynowcasfree", "URL: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("paynowcasfree", "========== WebView Page Finished Loading ==========")
                Log.d("paynowcasfree", "URL: $url")
                
                // Inject JavaScript to monitor for UPI redirects
                view?.evaluateJavascript("""
                    (function() {
                        // Monitor for window.location changes
                        var originalLocation = window.location;
                        Object.defineProperty(window, 'location', {
                            get: function() {
                                return originalLocation;
                            },
                            set: function(url) {
                                console.log('Location changed to: ' + url);
                                if (url && (url.startsWith('upi://') || url.startsWith('intent://') || url.startsWith('tez://') || url.startsWith('gpay://'))) {
                                    AndroidUPIHandler.onUPIRedirect(url);
                                }
                                originalLocation.href = url;
                            }
                        });
                        
                        // Monitor for link clicks
                        document.addEventListener('click', function(e) {
                            var target = e.target;
                            while (target && target.tagName !== 'A') {
                                target = target.parentElement;
                            }
                            if (target && target.href) {
                                var href = target.href;
                                if (href.startsWith('upi://') || href.startsWith('intent://') || href.startsWith('tez://') || href.startsWith('gpay://')) {
                                    AndroidUPIHandler.onUPIRedirect(href);
                                }
                            }
                        }, true);
                    })();
                """.trimIndent(), null)
                
                // Check if page contains payment completion indicators
                url?.let {
                    if (it.contains("cashfree") && (it.contains("success") || it.contains("failure") || it.contains("status"))) {
                        Log.d("paynowcasfree", "Payment completion detected in page URL")
                        handlePaymentCompletion()
                    }
                }
            }
        }

        // Load the URL
        if (url.isNotEmpty() && url.startsWith("http")) {
            Log.d("paynowcasfree", "Loading URL in WebView: $url")
            webView.loadUrl(url)
        } else {
            Log.e("paynowcasfree", "Invalid URL: $url")
            Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun handleUPIIntent(url: String): Boolean {
        Log.d("paynowcasfree", "========== handleUPIIntent Called ==========")
        Log.d("paynowcasfree", "URL: $url")
        
        // Check for UPI intent URLs
        if (url.startsWith("upi://") || url.startsWith("intent://") || url.startsWith("tez://") || url.startsWith("gpay://")) {
            Log.d("paynowcasfree", "UPI Intent URL detected: $url")
            try {
                val upiIntent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                Log.d("paynowcasfree", "Parsed UPI Intent successfully")
                
                // If we have a selected UPI package, try to open it directly
                selectedUPIPackage?.let { packageName ->
                    Log.d("paynowcasfree", "Attempting to open selected UPI app: $packageName")
                    
                    // Set the package to open specific UPI app
                    upiIntent.setPackage(packageName)
                    
                    // Check if the app can handle this intent
                    if (upiIntent.resolveActivity(packageManager) != null) {
                        Log.d("paynowcasfree", "========== Opening Selected UPI App ==========")
                        Log.d("paynowcasfree", "Package: $packageName")
                        Log.d("paynowcasfree", "Intent URI: $url")
                        startActivity(upiIntent)
                        Log.d("paynowcasfree", "UPI app opened successfully")
                        return true
                    } else {
                        Log.w("paynowcasfree", "Selected UPI app ($packageName) cannot handle this intent, trying without package")
                    }
                }
                
                // Fallback: Let system choose UPI app
                Log.d("paynowcasfree", "Opening UPI app (system will choose)")
                startActivity(upiIntent)
                Log.d("paynowcasfree", "UPI app opened (system choice)")
                return true
                
            } catch (e: ActivityNotFoundException) {
                Log.e("paynowcasfree", "========== UPI App NOT FOUND ==========")
                Log.e("paynowcasfree", "Error: ${e.message}")
                Toast.makeText(this, "No UPI app found", Toast.LENGTH_SHORT).show()
                return true
            } catch (e: Exception) {
                Log.e("paynowcasfree", "========== Error Opening UPI App ==========")
                Log.e("paynowcasfree", "Error: ${e.message}")
                Log.e("paynowcasfree", "Exception: ${e.stackTraceToString()}")
                e.printStackTrace()
                return true
            }
        }
        
        // Check for payment success/failure URLs (Cashfree redirects)
        if (url.contains("cashfree") && (url.contains("success") || url.contains("failure") || url.contains("status"))) {
            Log.d("paynowcasfree", "========== Payment Completion Detected ==========")
            Log.d("paynowcasfree", "URL: $url")
            handlePaymentCompletion()
            return true
        }
        
        Log.d("paynowcasfree", "Not a UPI intent, allowing WebView to handle normally")
        return false // Let WebView handle normal URLs
    }
    
    private fun handlePaymentCompletion() {
        Log.d("paynowcasfree", "========== handlePaymentCompletion ==========")
        Log.d("paynowcasfree", "Order ID: $orderId")
        Log.d("paynowcasfree", "Finishing WebView and returning to PaymentActivity")
        // Return to PaymentActivity which will check payment status in onResume
        finish()
    }
    
    override fun onBackPressed() {
        Log.d("paynowcasfree", "========== WebView Back Pressed ==========")
        Log.d("paynowcasfree", "Returning to PaymentActivity")
        // If user presses back, return to PaymentActivity
        super.onBackPressed()
    }
}
