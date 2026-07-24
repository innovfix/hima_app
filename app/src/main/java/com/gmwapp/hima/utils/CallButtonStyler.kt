package com.gmwapp.hima.utils

import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.gmwapp.hima.R

/**
 * Single source of truth for the "home creator-card" call-button look
 * (adapter_female_user.xml + FemaleUserAdapter), so every screen that shows an
 * audio/video call button — Home, Recent, Favourite, Chat, Profile Detail —
 * renders identically:
 *
 *   available   -> white circle, coloured icon (pink phone / purple video),
 *                  coin + "<rate>/min"
 *   unavailable -> white circle, muted-grey icon, "Offline", 0.5 alpha
 *
 * Callers set the circle to white (card bg / bg_call_circle_white) and pass the
 * icon + optional coin + optional rate label here so the colouring stays in one
 * place. Click handling and per-state feedback stay with each screen.
 */
object CallButtonStyler {
    val AUDIO_COLOR: Int = Color.parseColor("#E91E63")   // pink phone
    val VIDEO_COLOR: Int = Color.parseColor("#9C27B0")   // purple video
    val DISABLED_COLOR: Int = Color.parseColor("#6B7280") // muted grey (offline)
    private val RATE_COLOR: Int = Color.parseColor("#545454") // black_light

    /** App-wide default per-min rates — recent/favourite rows carry no per-contact price. */
    const val DEFAULT_AUDIO_RATE = 10
    const val DEFAULT_VIDEO_RATE = 60

    const val OFFLINE_LABEL = "Offline"

    /**
     * Applies the home icon + coin + label styling for one call button.
     * The caller is responsible for the white circle background, the button's
     * alpha, isEnabled/isClickable and the click listener.
     *
     * @param icon      phone/video icon (drawable + tint set here)
     * @param coin      small coin icon under the button (hidden when unavailable); may be null
     * @param label     rate/Offline text under the button; may be null
     * @param available whether the button can be tapped right now
     * @param forAudio  true = audio (phone, pink), false = video (videocam, purple)
     * @param rateText  the "<n>/min" text to show when available
     */
    fun style(
        icon: ImageView,
        coin: ImageView?,
        label: TextView?,
        available: Boolean,
        forAudio: Boolean,
        rateText: String
    ) {
        icon.setImageResource(if (forAudio) R.drawable.ic_phone else R.drawable.ic_videocam)
        icon.setColorFilter(
            when {
                !available -> DISABLED_COLOR
                forAudio -> AUDIO_COLOR
                else -> VIDEO_COLOR
            }
        )
        coin?.visibility = if (available) View.VISIBLE else View.GONE
        label?.let {
            it.text = if (available) rateText else OFFLINE_LABEL
            it.setTextColor(if (available) RATE_COLOR else DISABLED_COLOR)
        }
    }
}
