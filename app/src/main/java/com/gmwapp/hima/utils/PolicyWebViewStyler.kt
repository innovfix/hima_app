package com.gmwapp.hima.utils

import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Gives the policy WebViews (Terms, Refund, Community Guidelines) a premium,
 * professional look without touching the backend HTML.
 *
 * The content of these screens is remote HTML served from the settings API, so
 * we can't edit it directly. Instead we inject a stylesheet after the page loads
 * that re-skins the raw HTML: a soft page background, a white "card", brand-pink
 * headings, comfortable spacing and readable slate text instead of flat black.
 */
object PolicyWebViewStyler {

    // Page background behind the card. Keeps the WebView from flashing white.
    private const val PAGE_BG = "#F4F5F7"

    /**
     * Attach a WebViewClient that injects the premium stylesheet once the page
     * has rendered. Call this BEFORE loadUrl().
     */
    fun apply(webView: WebView) {
        // Match the WebView surface to the injected page background so the
        // corners/margins of the card blend in rather than showing raw white.
        webView.setBackgroundColor(Color.parseColor(PAGE_BG))

        webView.webViewClient = object : WebViewClient() {
            override fun onPageCommitVisible(view: WebView, url: String?) {
                super.onPageCommitVisible(view, url)
                view.evaluateJavascript(injectionJs, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                // Re-inject on finish in case the page mutated its DOM late.
                view.evaluateJavascript(injectionJs, null)
            }
        }
    }

    // Premium stylesheet. !important keeps us ahead of any inline styling the
    // server HTML ships with. Marked with an id so we never inject it twice.
    private val css = """
        :root {
          --brand: #ff1383;
          --brand-dark: #c11063;
          --ink: #2a2a33;
          --muted: #5c5c66;
          --page: $PAGE_BG;
          --card: #ffffff;
          --line: #ececf1;
        }
        html { background: var(--page) !important; -webkit-text-size-adjust: 100%; }
        body {
          background: var(--page) !important;
          color: var(--ink) !important;
          font-family: -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif !important;
          font-size: 16px !important;
          line-height: 1.7 !important;
          margin: 0 !important;
          padding: 16px 14px 32px !important;
        }
        /* Turn the body content into a floating white card */
        body > * { max-width: 720px; margin-left: auto; margin-right: auto; }
        h1, h2, h3 {
          color: var(--ink) !important;
          font-weight: 700 !important;
          line-height: 1.3 !important;
          margin: 22px 0 10px !important;
          letter-spacing: -0.2px;
        }
        h1 { font-size: 24px !important; }
        h2 {
          font-size: 20px !important;
          padding-left: 12px !important;
          border-left: 4px solid var(--brand) !important;
        }
        h3 { font-size: 17px !important; }
        p { color: var(--muted) !important; margin: 10px 0 !important; }
        strong, b { color: var(--ink) !important; font-weight: 700 !important; }
        a { color: var(--brand) !important; text-decoration: none !important; font-weight: 600 !important; }
        ul, ol { padding-left: 22px !important; margin: 8px 0 !important; }
        li { color: var(--muted) !important; margin: 8px 0 !important; padding-left: 4px !important; }
        li::marker { color: var(--brand) !important; }
        hr { border: none !important; border-top: 1px solid var(--line) !important; margin: 20px 0 !important; }
    """.trimIndent()

    private val injectionJs: String by lazy {
        val cssEscaped = css
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")
        """
        (function() {
          if (document.getElementById('hima-premium-style')) return;
          var s = document.createElement('style');
          s.id = 'hima-premium-style';
          s.type = 'text/css';
          s.appendChild(document.createTextNode(`$cssEscaped`));
          (document.head || document.documentElement).appendChild(s);
          var vp = document.querySelector('meta[name=viewport]');
          if (!vp) {
            vp = document.createElement('meta');
            vp.name = 'viewport';
            vp.content = 'width=device-width, initial-scale=1, maximum-scale=5';
            (document.head || document.documentElement).appendChild(vp);
          }
        })();
        """.trimIndent()
    }
}
