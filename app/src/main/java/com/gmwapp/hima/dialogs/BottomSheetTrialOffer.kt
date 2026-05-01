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
    private var heroLoadingMask: View? = null

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

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
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

        // Resize the hero container to match the video's aspect ratio so
        // there are no letterbox/pillarbox bars and no cropping. Detected
        // from URL pattern: shorts → 9:16, everything else → 16:9 (the
        // overwhelming default for non-shorts uploads).
        val aspectRatio = aspectRatioFor(youtubeUrl)
        resizeHeroContainerToRatio(aspectRatio)

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
            // Transparent click-blocker on top so taps can't surface
            // YouTube's controls / share / "Watch on YouTube" links.
            // The video stays ambient — autoplay + mute + loop, no
            // interaction. Without this overlay any tap brings the
            // YouTube UI back even with controls=0.
            val touchBlocker = View(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setOnTouchListener { _, _ -> true }
            }
            container.addView(touchBlocker)
            // Black mask shown for ~1.6s on first paint so the YouTube
            // chrome flash (title bar, controls) is hidden until controls=0
            // takes effect and the player auto-hides its UI.
            val mask = View(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(0xFF000000.toInt())
                isClickable = false
            }
            container.addView(mask)
            heroLoadingMask = mask
            heroWebView = it
        }
        // Reset mask to opaque on every config change so re-bound
        // sheets don't show the chrome flash.
        heroLoadingMask?.let { mask ->
            mask.alpha = 1f
            mask.animate().alpha(0f).setStartDelay(1600L).setDuration(220L).start()
        }

        // YouTube IFrame Player API — start muted (browsers/WebView block
        // autoplay with audio), then unmute programmatically once the
        // player is ready so the user actually hears the trial pitch.
        // Loop via loop+playlist trick. UI-hiding flags YouTube still
        // respects: no controls, no fullscreen, no captions overlay by
        // default, no annotations, no keyboard input, no related grid.
        val embedHtml = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>html,body{margin:0;padding:0;background:#000;height:100%;width:100%;overflow:hidden;}
            #player,iframe{position:absolute;top:0;left:0;width:100%!important;height:100%!important;border:0;}</style>
            </head><body>
            <div id="player"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
            var player;
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                width: '100%', height: '100%',
                videoId: '$videoId',
                playerVars: {
                  autoplay: 1, mute: 1, loop: 1, playlist: '$videoId',
                  controls: 0, modestbranding: 1, playsinline: 1,
                  rel: 0, fs: 0, disablekb: 1,
                  iv_load_policy: 3, cc_load_policy: 0
                },
                events: {
                  onReady: function(e) {
                    // Autoplay started muted (browser policy). Unmute now
                    // that playback is registered so the user actually
                    // hears the trial pitch. Brief setTimeout lets the
                    // muted-play heuristic stick first.
                    setTimeout(function() {
                      try { player.unMute(); player.setVolume(100); } catch (e) {}
                    }, 250);
                  }
                }
              });
            }
            </script>
            </body></html>
        """.trimIndent()
        // baseURL must be a non-youtube origin so the iframe is cross-origin
        // to the parent document — same-origin embeds (baseURL=youtube.com)
        // trigger YouTube's self-embed protection (error 152).
        web.loadDataWithBaseURL("https://hima.app/", embedHtml, "text/html", "utf-8", null)
    }

    /**
     * Aspect ratio (width / height) inferred from the YouTube URL pattern:
     * `/shorts/...` is always 9:16, everything else is treated as 16:9 —
     * the dominant default for regular YouTube uploads. Anything outside
     * those two ratios (rare 4:3 / 1:1 / vertical-non-shorts) will still
     * letterbox slightly; admins are expected to upload the right format.
     */
    private fun aspectRatioFor(url: String?): Float {
        val u = url?.lowercase() ?: return 16f / 9f
        return if (u.contains("/shorts/")) 9f / 16f else 16f / 9f
    }

    /**
     * Resize the hero container so width:height matches the supplied
     * aspect ratio, while capping height so the rest of the sheet (title,
     * badge, benefits card, CTA, link) always fits on screen without the
     * NestedScrollView ever needing to scroll.
     *
     * If the natural height (parentWidth / ratio) would exceed the cap,
     * the container's WIDTH is reduced too so the aspect ratio is
     * preserved — a 9:16 portrait video shows in a centered, narrower
     * frame instead of stretching the sheet vertically. Container has
     * start+end constraints to parent, so a narrower width auto-centers.
     */
    private fun resizeHeroContainerToRatio(ratio: Float) {
        if (!::binding.isInitialized) return
        val container = binding.flHeroContainer
        container.post {
            if (!::binding.isInitialized) return@post
            val parent = container.parent as? View ?: return@post
            val parentWidth = parent.width.coerceAtLeast(container.width)
            if (parentWidth <= 0) return@post

            // Available width inside the parent's horizontal padding.
            val availableWidth = parentWidth - parent.paddingLeft - parent.paddingRight
            // Cap hero at 40% of screen height — leaves ~60% for the
            // rest of the sheet on every phone size, so the sheet never
            // becomes taller than viewport and never scrolls.
            val maxHeight = (resources.displayMetrics.heightPixels * 0.40f).toInt()

            var height = (availableWidth / ratio).toInt()
            var width = availableWidth
            if (height > maxHeight) {
                height = maxHeight
                width = (maxHeight * ratio).toInt()
            }

            val params = container.layoutParams
            if (params.height != height || params.width != width) {
                params.height = height
                params.width = width
                container.layoutParams = params
            }
        }
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
