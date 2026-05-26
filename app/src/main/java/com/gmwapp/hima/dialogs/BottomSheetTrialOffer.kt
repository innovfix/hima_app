package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.VideoView
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
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BottomSheetTrialOffer : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetTrialOfferBinding
    private var onTryNowClick: (() -> Unit)? = null
    private var heroVideoView: VideoView? = null
    private var heroPlaceholderCover: View? = null
    private var heroYouTubePlayerView: YouTubePlayerView? = null
    private var heroYouTubePlayer: YouTubePlayer? = null
    private var currentVideoUrl: String? = null
    private var currentYouTubeId: String? = null

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
            // 2026-05-26 — track INTENT in our backend funnel only (not
            // marketing platforms). Marketing's StartTrial event fires
            // post-payment in AutopayCheckoutActivity once the mandate
            // is active — see "trial_active" block there. Keeping this
            // backend event lets /autopay-events show the click→pay
            // conversion ratio.
            val language = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.language
            com.gmwapp.hima.utils.AutopayEventTracker.trackStartTrial(
                apiManager, planType = "trial_new", language = language
            )

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
            override fun onConfig(
                videoUrl: String?,
                youtubeUrl: String?,
                texts: TrialOfferConfigCache.TextOverrides
            ) {
                if (!isAdded) return
                applyConfig(videoUrl, youtubeUrl, texts)
            }
        })
    }

    private fun applyConfig(
        videoUrl: String?,
        youtubeUrl: String?,
        texts: TrialOfferConfigCache.TextOverrides
    ) {
        if (!::binding.isInitialized) return

        // Per-language admin text overrides. Each line falls back to the
        // hardcoded XML default when the override is null/blank, so an
        // admin can change one line without retyping all the others.
        applyTextOverride(binding.tvTitle, texts.headline)
        applyTextOverride(binding.tvPrice, texts.priceText)
        applyTextOverride(binding.tvFeature1, texts.feature1Text)
        applyTextOverride(binding.tvFeature2, texts.feature2Text)
        applyTextOverride(binding.tvAutopayFooter, texts.footerText)
        applyTextOverride(binding.btnTryNow, texts.ctaText)
        applyTextOverride(binding.tvPurchaseCoins, texts.secondaryLinkText)

        // mp4 (video_url) wins when both are set. Falls through to the
        // YouTube embed when admin only saved a YouTube link, and to the
        // static placeholder when neither is configured.
        if (!videoUrl.isNullOrBlank()) {
            tearDownYouTubeHero()
            applyVideoHero(videoUrl)
            return
        }
        val ytId = TrialOfferConfigCache.extractYouTubeId(youtubeUrl)
        if (ytId != null) {
            tearDownVideoHero()
            applyYouTubeHero(ytId)
            return
        }
        // Neither mp4 nor parseable YouTube URL — placeholder drawable in
        // flHeroContainer stays as-is.
        tearDownVideoHero()
        tearDownYouTubeHero()
    }

    private fun applyVideoHero(videoUrl: String) {
        // Skip reload if the URL is unchanged — avoids the player stutter
        // when the background refresh returns the same value we already
        // rendered from cache.
        if (videoUrl == currentVideoUrl && heroVideoView != null) return
        currentVideoUrl = videoUrl

        val container = binding.flHeroContainer
        val video = heroVideoView ?: VideoView(requireContext()).also {
            it.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            container.removeAllViews()
            container.addView(it)
            heroVideoView = it
        }
        // Stack a placeholder cover ON TOP of the VideoView so the user
        // sees the gradient placeholder (not the VideoView's opaque-black
        // SurfaceView) while MediaPlayer prepares. We can't hide the
        // VideoView itself — SurfaceView destroys its surface when
        // INVISIBLE, which blocks MediaPlayer from ever firing prepared
        // on subsequent fragment instances.
        val cover = heroPlaceholderCover ?: View(requireContext()).also {
            it.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Block taps so the user can't interact with the (audio-only)
            // VideoView underneath while we're showing the placeholder.
            it.isClickable = true
            container.addView(it)
            heroPlaceholderCover = it
        }
        // Prefer the cached first-frame poster as the cover so the swap
        // from cover → playback is imperceptible (same image, then it
        // moves). Falls back to the container's gradient drawable when
        // the poster hasn't been extracted yet (first-ever prefetch run).
        val poster = TrialOfferConfigCache.getCachedPosterFile(requireContext(), videoUrl)
        cover.background = if (poster != null) {
            BitmapFactory.decodeFile(poster.absolutePath)?.let {
                BitmapDrawable(resources, it)
            } ?: container.background
        } else {
            container.background
        }
        // Ensure the cover is on top of the VideoView and starts fully
        // opaque — re-bound sheets must show the placeholder again while
        // their MediaPlayer prepares.
        cover.bringToFront()
        cover.alpha = 1f
        cover.visibility = View.VISIBLE

        // Autoplay looping at device media volume. Earlier revisions muted
        // this with setVolume(0,0) to mirror the previous YouTube embed's
        // mute=1 — product called for audio on, so MediaPlayer now uses
        // its default volume against STREAM_MUSIC.
        video.setOnInfoListener { _, what, _ ->
            // MEDIA_INFO_VIDEO_RENDERING_START fires the moment the first
            // frame is actually painted onto the SurfaceView — fade the
            // placeholder cover then so the swap is smooth.
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                fadeOutCover()
            }
            false
        }
        video.setOnPreparedListener { mp ->
            mp.isLooping = true
            // Show the video at its natural aspect ratio — never stretch
            // or crop. The hero container shrinks to match (see
            // resizeHeroContainerToRatio below) so portrait 9:16 clips
            // appear as a centered narrower frame and landscape 16:9
            // fills the sheet width.
            val w = mp.videoWidth
            val h = mp.videoHeight
            if (w > 0 && h > 0) {
                resizeHeroContainerToRatio(w.toFloat() / h.toFloat())
            }
            video.start()
            // Fallback: some devices / codecs never fire
            // MEDIA_INFO_VIDEO_RENDERING_START (e.g. emulators with broken
            // OMX). After prepare we wait briefly for the first frame to
            // paint, then drop the cover regardless.
            cover.postDelayed({
                if (isAdded && cover.alpha > 0f) fadeOutCover()
            }, 600L)
        }
        video.setOnErrorListener { _, _, _ ->
            // Network/codec failure — leave the placeholder cover up so
            // the user sees the gradient instead of a black surface, and
            // remove the (broken) VideoView. Return true so MediaPlayer
            // doesn't post the system "Can't play this video" dialog.
            container.removeView(video)
            heroVideoView = null
            currentVideoUrl = null
            true
        }
        // Prefer the disk-cached copy when available — playback starts
        // in <1 s instead of the 5–7 s the user reported while streaming
        // the 20+ MB clip from demohima. Falls back to the remote URL
        // when the prefetch hasn't completed (or failed).
        val localFile = TrialOfferConfigCache.getCachedVideoFile(requireContext(), videoUrl)
        if (localFile != null) {
            video.setVideoURI(Uri.fromFile(localFile))
        } else {
            video.setVideoURI(Uri.parse(videoUrl))
        }
    }

    /**
     * Fallback path: admin saved a YouTube URL but no uploaded mp4. We
     * load the parsed video id into a YouTubePlayerView placed inside the
     * same flHeroContainer the mp4 path would use. Autoplay + loop are
     * driven by IFramePlayerOptions; the placeholder cover stays out of
     * the way here because YouTube renders its own poster.
     */
    private fun applyYouTubeHero(videoId: String) {
        if (videoId == currentYouTubeId && heroYouTubePlayerView != null) return
        currentYouTubeId = videoId
        val container = binding.flHeroContainer
        val playerView = heroYouTubePlayerView ?: YouTubePlayerView(requireContext()).also {
            it.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            it.enableAutomaticInitialization = false
            container.removeAllViews()
            container.addView(it)
            viewLifecycleOwner.lifecycle.addObserver(it)
            heroYouTubePlayerView = it
            // 16:9 cap — YouTube clips are landscape; resize the hero
            // container so the sheet doesn't reserve dead space below.
            resizeHeroContainerToRatio(16f / 9f)
        }
        // Loop the same video by re-cuing it on END. autoplay=1, mute=1
        // (browsers/Android both require muted for unattended autoplay).
        val options = IFramePlayerOptions.Builder(requireContext())
            .controls(0)
            .autoplay(1)
            .mute(1)
            .rel(0)
            .ivLoadPolicy(3)
            .build()
        playerView.initialize(object : AbstractYouTubePlayerListener() {
            override fun onReady(player: YouTubePlayer) {
                heroYouTubePlayer = player
                player.loadVideo(videoId, 0f)
            }
            override fun onStateChange(
                player: YouTubePlayer,
                state: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState
            ) {
                if (state == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState.ENDED) {
                    player.loadVideo(videoId, 0f)
                }
            }
        }, options)
    }

    private fun tearDownVideoHero() {
        heroVideoView?.let {
            it.stopPlayback()
            (it.parent as? ViewGroup)?.removeView(it)
        }
        heroPlaceholderCover?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        heroVideoView = null
        heroPlaceholderCover = null
        currentVideoUrl = null
    }

    private fun tearDownYouTubeHero() {
        heroYouTubePlayer?.pause()
        heroYouTubePlayerView?.let {
            it.release()
            (it.parent as? ViewGroup)?.removeView(it)
        }
        heroYouTubePlayer = null
        heroYouTubePlayerView = null
        currentYouTubeId = null
    }

    /**
     * Resize the hero container to match the source video's aspect
     * ratio. Width is capped at the parent's available width; height
     * is capped at ~360 dp so a portrait 9:16 clip never pushes the
     * sheet past ~70 % of the viewport. Within those caps the video
     * shows at its natural aspect — no stretching, no cropping.
     */
    private fun resizeHeroContainerToRatio(ratio: Float) {
        if (!::binding.isInitialized) return
        val container = binding.flHeroContainer
        container.post {
            if (!::binding.isInitialized) return@post
            val parent = container.parent as? View ?: return@post
            val parentWidth = parent.width.coerceAtLeast(container.width)
            if (parentWidth <= 0) return@post
            val availableWidth = parentWidth - parent.paddingLeft - parent.paddingRight
            val maxHeightPx = (360f * resources.displayMetrics.density).toInt()
            var height = (availableWidth / ratio).toInt()
            var width = availableWidth
            if (height > maxHeightPx) {
                height = maxHeightPx
                width = (maxHeightPx * ratio).toInt()
            }
            val params = container.layoutParams
            if (params.height != height || params.width != width) {
                params.height = height
                params.width = width
                container.layoutParams = params
            }
        }
    }

    private fun fadeOutCover() {
        val cover = heroPlaceholderCover ?: return
        if (cover.alpha == 0f) return
        cover.animate()
            .alpha(0f)
            .setDuration(200L)
            .withEndAction { cover.visibility = View.INVISIBLE }
            .start()
    }

    /**
     * Apply an admin-supplied override to a TextView/Button. Null or
     * blank leaves the XML default in place — admins set only the
     * lines they want changed for that language.
     */
    private fun applyTextOverride(view: TextView, value: String?) {
        if (!value.isNullOrBlank()) {
            view.text = value
        }
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
    override fun onPause() {
        super.onPause()
        // VideoView pauses cleanly via pause(); MediaPlayer keeps its
        // position so onResume() resumes from where the loop was.
        heroVideoView?.pause()
        heroYouTubePlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        heroVideoView?.start()
        heroYouTubePlayer?.play()
    }

    override fun onDestroyView() {
        tearDownVideoHero()
        tearDownYouTubeHero()
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
