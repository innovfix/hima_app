package com.gmwapp.hima.activities

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivitySubqueryDetailBinding
import com.gmwapp.hima.retrofit.responses.SubqueryData
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.unescapeHelpContent
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import dagger.hilt.android.AndroidEntryPoint
import com.gmwapp.hima.utils.applyLightSystemBars

@AndroidEntryPoint
class SubqueryDetailActivity : BaseActivity() {
    lateinit var binding: ActivitySubqueryDetailBinding
    private lateinit var subquery: SubqueryData
    private var videoId: String? = null
    private var youTubePlayer: YouTubePlayer? = null
    private var pendingVideoId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubqueryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        // Status bar configuration
        applyLightSystemBars()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        subquery = intent.getParcelableExtra<SubqueryData>("subquery")
            ?: run {
                finish()
                return
            }

        initUI()
        setupYouTubePlayer()
        loadSubqueryDetails()
    }

    private fun initUI() {
        binding.cvBack.setOnSingleClickListener {
            finish()
        }

        // Watch Video button click
        binding.btnWatchVideo.setOnSingleClickListener {
            showVideoPlayer()
        }

        // Close video button click
        binding.ivCloseVideo.setOnSingleClickListener {
            hideVideoPlayer()
        }
    }

    private fun setupYouTubePlayer() {
        Log.d("YoutubeLoadingCheck", "Setting up YouTube player")
        lifecycle.addObserver(binding.youtubePlayerView)
        Log.d("YoutubeLoadingCheck", "Lifecycle observer added to YouTube player view")

        binding.youtubePlayerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(player: YouTubePlayer) {
                youTubePlayer = player
                Log.d("YoutubeLoadingCheck", "YouTube player ready - pendingVideoId: $pendingVideoId")
                
                // If there's a pending video to load, load it now
                pendingVideoId?.let { videoId ->
                    val validatedVideoId = validateVideoId(videoId)
                    if (validatedVideoId != null) {
                        Log.d("YoutubeLoadingCheck", "Loading pending video - ID: $validatedVideoId")
                        player.loadVideo(validatedVideoId, 0f)
                        pendingVideoId = null
                        Log.d("YoutubeLoadingCheck", "Pending video loaded, cleared pendingVideoId")
                    } else {
                        Log.e("YoutubeLoadingCheck", "Failed to validate pending video ID: $videoId")
                    }
                } ?: Log.d("YoutubeLoadingCheck", "No pending video to load")
            }

            override fun onError(player: YouTubePlayer, error: PlayerConstants.PlayerError) {
                Log.e("YoutubeLoadingCheck", "YouTube player error: $error")
            }
            
            override fun onCurrentSecond(player: YouTubePlayer, second: Float) {
                super.onCurrentSecond(player, second)
                // Just to confirm player is working
                if (second < 1f) {
                    Log.d("YoutubeLoadingCheck", "Video started playing at second: $second")
                }
            }
        })
        Log.d("YoutubeLoadingCheck", "YouTube player listener added")
    }

    private fun loadSubqueryDetails() {
        // Set title
        binding.tvTitle.text = subquery.title

        // Set image (hide if empty)
        if (!subquery.image.isNullOrEmpty()) {
            binding.cardImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(subquery.image)
                .apply(RequestOptions()
                    .transform(RoundedCorners(16))
                    .placeholder(R.drawable.ic_help)
                    .error(R.drawable.ic_help))
                .into(binding.ivImage)
        } else {
            binding.cardImage.visibility = View.GONE
        }

        // Handle YouTube video - extract video ID and show button if available
        val videoLink = subquery.videoLink
        if (!videoLink.isNullOrEmpty()) {
            Log.d("YoutubeLoadingCheck", "Video URL received: $videoLink")
            videoId = extractVideoId(videoLink)
            if (videoId != null) {
                Log.d("YoutubeLoadingCheck", "Extracted Video ID: $videoId")
                // Button will be shown in description card
            } else {
                Log.e("YoutubeLoadingCheck", "Failed to extract Video ID from URL: $videoLink")
            }
        } else {
            Log.d("YoutubeLoadingCheck", "No video URL provided")
        }

        // Set description (hide if empty)
        if (!subquery.description.isNullOrEmpty()) {
            binding.cardDescription.visibility = View.VISIBLE
            binding.tvDescription.text = subquery.description.unescapeHelpContent()
            
            // Show Watch Video button if video is available (top right of description card)
            if (videoId != null) {
                binding.btnWatchVideo.visibility = View.VISIBLE
            } else {
                binding.btnWatchVideo.visibility = View.GONE
            }
        } else {
            // If no description but video exists, still show description card with just video button
            if (videoId != null) {
                binding.cardDescription.visibility = View.VISIBLE
                binding.tvDescriptionLabel.visibility = View.GONE
                binding.tvDescription.visibility = View.GONE
                binding.btnWatchVideo.visibility = View.VISIBLE
            } else {
                binding.cardDescription.visibility = View.GONE
            }
        }
    }

    private fun showVideoPlayer() {
        Log.d("YoutubeLoadingCheck", "showVideoPlayer called - Video ID: $videoId")

        if (videoId == null) {
            Log.e("YoutubeLoadingCheck", "Cannot show video player - Video ID is null")
            return
        }

        // Show video player card first
        binding.cardVideoPlayer.visibility = View.VISIBLE
        binding.btnWatchVideo.visibility = View.GONE

        val validatedVideoId = validateVideoId(videoId)
        if (validatedVideoId == null) {
            Log.e("YoutubeLoadingCheck", "Invalid video ID format: $videoId")
            return
        }

        // Load video in YouTube player
        youTubePlayer?.let {
            Log.d("YoutubeLoadingCheck", "Loading video in ready player - ID: $validatedVideoId")
            it.loadVideo(validatedVideoId, 0f)
            pendingVideoId = null
        } ?: run {
            // Player not ready yet, store video ID to load when ready
            Log.d("YoutubeLoadingCheck", "Player not ready yet. Storing video ID for later: $validatedVideoId")
            pendingVideoId = validatedVideoId
            Log.d("YoutubeLoadingCheck", "Pending video ID stored: $pendingVideoId")
        }
    }


    private fun hideVideoPlayer() {
        binding.cardVideoPlayer.visibility = View.GONE
        binding.btnWatchVideo.visibility = View.VISIBLE

        // Pause video when hiding
        youTubePlayer?.pause()
    }
    
    /**
     * Validates and normalizes YouTube video ID
     * YouTube video IDs must be exactly 11 characters
     */
    private fun validateVideoId(videoId: String?): String? {
        if (videoId == null) {
            Log.e("YoutubeLoadingCheck", "Video ID is null")
            return null
        }
        
        // YouTube video IDs are exactly 11 characters
        if (videoId.length == 11) {
            Log.d("YoutubeLoadingCheck", "Video ID is valid: $videoId (length: ${videoId.length})")
            return videoId
        }
        
        // If longer than 11, try to extract first 11 characters
        if (videoId.length > 11) {
            val trimmed = videoId.substring(0, 11)
            Log.w("YoutubeLoadingCheck", "Video ID too long (${videoId.length} chars), trimmed to: $trimmed")
            return trimmed
        }
        
        // If shorter than 11, it's invalid
        Log.e("YoutubeLoadingCheck", "Video ID too short: $videoId (length: ${videoId.length}, required: 11)")
        return null
    }

    private fun extractVideoId(url: String): String? {
        // Enhanced regex to handle YouTube Shorts, regular videos, and various URL formats
        // YouTube video IDs are ALWAYS exactly 11 characters
        try {
            Log.d("YoutubeLoadingCheck", "Extracting Video ID from URL: $url")
            
            // YouTube Shorts: https://www.youtube.com/shorts/VIDEO_ID
            // Extract exactly 11 characters after /shorts/ (stop at query params, fragments, or end)
            val shortsPattern = Regex("youtube\\.com/shorts/([a-zA-Z0-9_-]{11})(?:[?&#]|$)")
            shortsPattern.find(url)?.let {
                val videoId = it.groupValues[1]
                if (videoId.length == 11) {
                    Log.d("YoutubeLoadingCheck", "Extracted Video ID from Shorts: $videoId (length: ${videoId.length})")
                    return videoId
                }
            }
            
            // YouTube regular: https://www.youtube.com/watch?v=VIDEO_ID
            val watchPattern = Regex("youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11})(?:[&?#]|$)")
            watchPattern.find(url)?.let {
                val videoId = it.groupValues[1]
                if (videoId.length == 11) {
                    Log.d("YoutubeLoadingCheck", "Extracted Video ID from watch: $videoId (length: ${videoId.length})")
                    return videoId
                }
            }
            
            // YouTube embed: https://www.youtube.com/embed/VIDEO_ID
            val embedPattern = Regex("youtube\\.com/embed/([a-zA-Z0-9_-]{11})(?:[?&#]|$)")
            embedPattern.find(url)?.let {
                val videoId = it.groupValues[1]
                if (videoId.length == 11) {
                    Log.d("YoutubeLoadingCheck", "Extracted Video ID from embed: $videoId (length: ${videoId.length})")
                    return videoId
                }
            }
            
            // YouTube youtu.be: https://youtu.be/VIDEO_ID
            val youtuBePattern = Regex("youtu\\.be/([a-zA-Z0-9_-]{11})(?:[?&#]|$)")
            youtuBePattern.find(url)?.let {
                val videoId = it.groupValues[1]
                if (videoId.length == 11) {
                    Log.d("YoutubeLoadingCheck", "Extracted Video ID from youtu.be: $videoId (length: ${videoId.length})")
                    return videoId
                }
            }
            
            // Fallback: Try to find any 11-character sequence after common YouTube path patterns
            // This handles edge cases where URL structure might be slightly different
            val fallbackPattern = Regex("(?:youtube\\.com/(?:shorts|watch\\?v=|embed)/|youtu\\.be/)([a-zA-Z0-9_-]{11})")
            fallbackPattern.find(url)?.let {
                val videoId = it.groupValues[1]
                if (videoId.length == 11) {
                    Log.d("YoutubeLoadingCheck", "Extracted Video ID using fallback: $videoId (length: ${videoId.length})")
                    return videoId
                }
            }
            
            // If we got a longer match from Shorts, try to extract first 11 characters
            // This handles cases where there might be extra characters
            val shortsLoosePattern = Regex("youtube\\.com/shorts/([a-zA-Z0-9_-]{11,})")
            shortsLoosePattern.find(url)?.let {
                val matched = it.groupValues[1]
                if (matched.length >= 11) {
                    val videoId = matched.substring(0, 11)
                    Log.d("YoutubeLoadingCheck", "Extracted Video ID from Shorts (trimmed): $videoId (original: $matched)")
                    return videoId
                }
            }
            
        } catch (e: Exception) {
            Log.e("YoutubeLoadingCheck", "Exception while extracting Video ID: ${e.message}", e)
        }
        
        Log.e("YoutubeLoadingCheck", "Could not extract valid 11-character Video ID from URL: $url")
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.youtubePlayerView.release()
    }
}

