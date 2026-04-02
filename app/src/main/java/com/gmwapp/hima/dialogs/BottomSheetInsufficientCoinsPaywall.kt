package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.gmwapp.hima.R
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

class BottomSheetInsufficientCoinsPaywall : BottomSheetDialogFragment() {

    interface OnPaywallAddCoinsListener {
        fun onAddCoins(amount: String, id: Int)
    }

    private var addCoinsListener: OnPaywallAddCoinsListener? = null
    private var youTubePlayerView: YouTubePlayerView? = null
    private var youTubePlayer: YouTubePlayer? = null

    private var titleText: String = ""
    private var subtitleText: String = ""
    private var youtubeVideoLink: String = ""
    private var coinId: Int = 0
    private var coinAmount: Int = 0
    private var coinValue: Int = 0

    companion object {
        fun newInstance(
            titleText: String?,
            subtitleText: String?,
            youtubeVideoLink: String?,
            coinId: Int?,
            coinAmount: Int?,
            coinValue: Int?
        ): BottomSheetInsufficientCoinsPaywall {
            return BottomSheetInsufficientCoinsPaywall().apply {
                arguments = Bundle().apply {
                    putString("titleText", titleText.orEmpty())
                    putString("subtitleText", subtitleText.orEmpty())
                    putString("youtubeVideoLink", youtubeVideoLink.orEmpty())
                    putInt("coinId", coinId ?: 0)
                    putInt("coinAmount", coinAmount ?: 0)
                    putInt("coinValue", coinValue ?: 0)
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnPaywallAddCoinsListener) {
            addCoinsListener = context
        } else {
            throw RuntimeException("$context must implement OnPaywallAddCoinsListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            titleText = it.getString("titleText").orEmpty()
            subtitleText = it.getString("subtitleText").orEmpty()
            youtubeVideoLink = it.getString("youtubeVideoLink").orEmpty()
            coinId = it.getInt("coinId")
            coinAmount = it.getInt("coinAmount")
            coinValue = it.getInt("coinValue")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.bottom_sheet_insufficient_coins_paywall, container, false)

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvSubtitle)
        val tvPrice = view.findViewById<TextView>(R.id.tvPrice)
        val btnAddCoin = view.findViewById<MaterialButton>(R.id.btnAddCoin)
        val playerView = view.findViewById<YouTubePlayerView>(R.id.youtubePlayerView)
        youTubePlayerView = playerView

        tvTitle.text = titleText
        tvSubtitle.text = subtitleText
        tvPrice.text = "Rs $coinAmount"
        btnAddCoin.text = if (coinValue > 0) {
            "Add $coinValue Coins"
        } else {
            "Add Coin"
        }

        lifecycle.addObserver(playerView)
        playerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(player: YouTubePlayer) {
                youTubePlayer = player
                extractVideoId(youtubeVideoLink)?.let { videoId ->
                    player.loadVideo(videoId, 0f)
                }
            }
        })

        btnAddCoin.setOnSingleClickListener {
            if (coinId > 0 && coinAmount > 0) {
                addCoinsListener?.onAddCoins(coinAmount.toString(), coinId)
                dismissAllowingStateLoss()
            }
        }

        return view
    }

    override fun onDestroyView() {
        youTubePlayer?.pause()
        youTubePlayerView?.release()
        youTubePlayer = null
        youTubePlayerView = null
        super.onDestroyView()
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    private fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null

        val shortsPattern = Regex("youtube\\.com/shorts/([a-zA-Z0-9_-]{11})(?:[?&#]|$)")
        shortsPattern.find(url)?.groupValues?.getOrNull(1)?.let { return it }

        val watchPattern = Regex("youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11})(?:[&?#]|$)")
        watchPattern.find(url)?.groupValues?.getOrNull(1)?.let { return it }

        val embedPattern = Regex("youtube\\.com/embed/([a-zA-Z0-9_-]{11})(?:[?&#]|$)")
        embedPattern.find(url)?.groupValues?.getOrNull(1)?.let { return it }

        val youtuBePattern = Regex("youtu\\.be/([a-zA-Z0-9_-]{11})(?:[?&#]|$)")
        youtuBePattern.find(url)?.groupValues?.getOrNull(1)?.let { return it }

        return null
    }
}
