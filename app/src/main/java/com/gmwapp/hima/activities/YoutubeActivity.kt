package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityYoutubeBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.ExplanationVideoViewModel
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class YoutubeActivity : BaseActivity() {
    lateinit var binding: ActivityYoutubeBinding
    private val ExplanationVideoViewModel: ExplanationVideoViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYoutubeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUI()
    }

    private fun initUI() {
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val language = prefs?.getUserData()?.language
//        binding.youtubePlayerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
//            override fun onReady(youTubePlayer: YouTubePlayer) {
//                prefs?.getSettingsData()?.demo_video?.let { youTubePlayer.loadVideo(it, 0f) }
//
//            }
//        })

        language?.let { ExplanationVideoViewModel.fetchVideos(it) }
        ExplanationVideoViewModel.videoResponseLiveData.observe(this) { response ->
            response?.let {
                if (it.success && it.data.isNotEmpty()) {
                    val videoUrl = it.data[0].video_link

                    Log.d("VideoURL", "Loaded Video: $videoUrl")

                    // Extract video ID
                    val videoId = extractVideoId(videoUrl)
                    if (videoId != null) {
                        Log.d("VideoID", "Extracted ID: $videoId")
                        binding.youtubePlayerView.addYouTubePlayerListener(object :
                            AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                youTubePlayer.loadVideo(videoId, 0f)
                            }

                            override fun onError(
                                youTubePlayer: YouTubePlayer,
                                error: PlayerConstants.PlayerError
                            ) {
                                super.onError(youTubePlayer, error)
                                Log.e("YouTubePlayerError", "Error occurred: $error")

                            }

                        })
                    } else {
                        Log.e("VideoError", "Invalid YouTube URL: $videoUrl")
                    }
                } else {
                    Log.e("VideoError", "Failed to load video")
                }
            }
        }






        binding.btnComplete.setOnSingleClickListener({
            val intent = Intent(this, AlmostDoneActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        })
        binding.tvSkip.setOnSingleClickListener({
            val intent = Intent(this, AlmostDoneActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        })
    }

    private fun extractVideoId(url: String): String? {
        val regex = Regex("(?:youtu\\.be/|youtube\\.com/(?:.*v=|.*\\/|.*embed\\/|.*watch\\?v=|.*shorts/))([a-zA-Z0-9_-]{11})")
        return regex.find(url)?.groupValues?.get(1)
    }


}