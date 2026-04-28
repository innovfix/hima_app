package com.gmwapp.hima.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.databinding.BottomSheetTrialOfferBinding
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.utils.TrialOfferConfigCache
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BottomSheetTrialOffer : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetTrialOfferBinding
    private var onTryNowClick: (() -> Unit)? = null
    private var heroWebView: WebView? = null

    @Inject
    lateinit var apiManager: ApiManager

    fun setOnTryNowClickListener(listener: () -> Unit) {
        onTryNowClick = listener
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), theme)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetTrialOfferBinding.inflate(inflater, container, false)
        binding.btnTryNow.setOnSingleClickListener {
            onTryNowClick?.invoke()
            dismissAllowingStateLoss()
        }
        binding.tvPurchaseCoins.paintFlags =
            binding.tvPurchaseCoins.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvPurchaseCoins.setOnSingleClickListener {
            startActivity(Intent(requireContext(), WalletActivity::class.java))
            dismissAllowingStateLoss()
        }
        loadHeroVideo()
        return binding.root
    }

    /**
     * Cache-first load of the trial-offer hero video. Cache hit renders
     * synchronously; cache miss / stale triggers a background fetch and
     * re-renders if the URL actually changed (avoids flicker).
     */
    private fun loadHeroVideo() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        TrialOfferConfigCache.load(requireContext(), apiManager, userId, object : TrialOfferConfigCache.Listener {
            override fun onConfig(youtubeUrl: String?, headline: String?) {
                if (!isAdded) return
                applyConfig(youtubeUrl, headline)
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applyConfig(youtubeUrl: String?, headline: String?) {
        if (!::binding.isInitialized) return

        // Optional admin override of the headline copy.
        if (!headline.isNullOrBlank()) {
            binding.tvTitle.text = headline
        }

        val videoId = TrialOfferConfigCache.extractVideoId(youtubeUrl)
        if (videoId == null) {
            // Either no admin asset yet OR the URL didn't parse — leave the
            // static placeholder drawable in flHeroContainer untouched.
            return
        }

        // Build (or reuse) a WebView inside flHeroContainer.
        val container = binding.flHeroContainer
        val web = heroWebView ?: WebView(requireContext()).also {
            it.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            it.setBackgroundColor(0x00000000)
            it.settings.javaScriptEnabled = true
            it.settings.mediaPlaybackRequiresUserGesture = false
            it.settings.domStorageEnabled = true
            it.webViewClient = WebViewClient()
            container.removeAllViews()
            container.addView(it)
            heroWebView = it
        }

        // YouTube IFrame embed with autoplay+mute (browsers/WebView only
        // allow autoplay when muted) and looping (loop+playlist param).
        val embedHtml = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>html,body{margin:0;padding:0;background:#000;height:100%;}
            iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0;}</style>
            </head><body>
            <iframe src="https://www.youtube.com/embed/$videoId?autoplay=1&mute=1&controls=0&loop=1&playlist=$videoId&modestbranding=1&playsinline=1&rel=0"
                    allow="autoplay; encrypted-media" allowfullscreen></iframe>
            </body></html>
        """.trimIndent()
        web.loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "utf-8", null)
    }

    override fun onPause() {
        super.onPause()
        heroWebView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        heroWebView?.onResume()
    }

    override fun onDestroyView() {
        // Tear down the WebView properly to avoid leaks.
        heroWebView?.let {
            it.loadUrl("about:blank")
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        heroWebView = null
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val sheet = dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        behavior.isFitToContents = true
    }

    companion object {
        const val TAG = "BottomSheetTrialOffer"
        fun newInstance(): BottomSheetTrialOffer = BottomSheetTrialOffer()
    }
}
