package com.gmwapp.hima.activities

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.gmwapp.hima.R
import de.hdodenhof.circleimageview.CircleImageView
import com.gmwapp.hima.utils.applyImmersiveSystemBars

/**
 * CHAT-047 image viewer. Replaces the bare-dialog full-screen Glide image that
 * the chat adapter used to launch — that view had no header, no sender info,
 * and no Reply / React affordances. WhatsApp-style: gradient header (back +
 * avatar + name + time) and footer (Reply + React pills) sit over the photo.
 *
 * Tap on the photo toggles chrome (200ms fade). Once the user pinches in,
 * chrome is hidden until pinch returns to fit. Reply / React tap finishes
 * the activity with `RESULT_OK` + an action extra so the host
 * ChatActivityInHouse can run its existing reply / react flow without the
 * viewer knowing about the in-thread state.
 */
class FullscreenImageActivity : AppCompatActivity() {

    private lateinit var photoView: PhotoView
    private lateinit var chromeTop: View
    private lateinit var chromeBottom: View

    private var chromeVisible: Boolean = true
    private var chromeAnimator: ObjectAnimator? = null
    private var chromeAnimatorBottom: ObjectAnimator? = null

    private var messageId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The chat surfaces are screenshot/recording-proof (FLAG_SECURE); this
        // viewer is a separate window and shows the same sensitive chat images,
        // so it must set its own FLAG_SECURE to avoid a screenshot regression.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_fullscreen_image)

        // Edge-to-edge: photo lives behind the system bars, chrome handles
        // its own top + bottom safe-area insets via fitsSystemWindows.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyImmersiveSystemBars()
        // Light icons on the dark photo background.
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        photoView = findViewById(R.id.photo_view)
        chromeTop = findViewById(R.id.chrome_top)
        chromeBottom = findViewById(R.id.chrome_bottom)

        // Safe-area: photo stays edge-to-edge behind the bars, but push the top
        // chrome (back/avatar/name/time) below the status bar and the bottom
        // chrome (Reply/React) above the nav bar. fitsSystemWindows was unreliable
        // here, so apply the system-bar insets explicitly (matches the rest of
        // the app). Capture the XML paddings once so repeated callbacks don't stack.
        val topBasePadding = chromeTop.paddingTop
        val bottomBasePadding = chromeBottom.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            chromeTop.updatePadding(top = topBasePadding + bars.top)
            chromeBottom.updatePadding(bottom = bottomBasePadding + bars.bottom)
            insets
        }

        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL).orEmpty()
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME).orEmpty()
        val peerAvatar = intent.getStringExtra(EXTRA_PEER_AVATAR).orEmpty()
        val timestamp = intent.getStringExtra(EXTRA_TIMESTAMP).orEmpty()
        messageId = intent.getStringExtra(EXTRA_MESSAGE_ID).orEmpty()

        findViewById<TextView>(R.id.tv_peer_name).text = peerName
        findViewById<TextView>(R.id.tv_timestamp).text = timestamp

        val avatarView = findViewById<CircleImageView>(R.id.iv_peer_avatar)
        if (peerAvatar.isNotBlank()) {
            Glide.with(this)
                .load(peerAvatar)
                .placeholder(R.drawable.small_profile)
                .error(R.drawable.small_profile)
                .into(avatarView)
        }

        if (imageUrl.isNotBlank()) {
            Glide.with(this)
                .load(imageUrl)
                .into(photoView)
        }

        findViewById<ImageView>(R.id.btn_close).setOnClickListener { finish() }

        photoView.setOnPhotoTapListener { _, _, _ -> toggleChrome() }
        // While zoomed, hide chrome — keep it visible only at fit-scale.
        photoView.setOnScaleChangeListener { _, _, _ ->
            val zoomed = photoView.scale > 1.05f
            if (zoomed && chromeVisible) setChromeVisible(false)
            if (!zoomed && !chromeVisible) setChromeVisible(true)
        }

        findViewById<View>(R.id.btn_reply).setOnClickListener {
            finishWithAction(ACTION_REPLY)
        }
        findViewById<View>(R.id.btn_react).setOnClickListener {
            finishWithAction(ACTION_REACT)
        }
    }

    private fun toggleChrome() {
        setChromeVisible(!chromeVisible)
    }

    private fun setChromeVisible(visible: Boolean) {
        if (chromeVisible == visible) return
        chromeVisible = visible
        chromeAnimator?.cancel()
        chromeAnimatorBottom?.cancel()
        val target = if (visible) 1f else 0f
        chromeAnimator = ObjectAnimator.ofFloat(chromeTop, View.ALPHA, chromeTop.alpha, target).apply {
            duration = 200L
            start()
        }
        chromeAnimatorBottom = ObjectAnimator.ofFloat(chromeBottom, View.ALPHA, chromeBottom.alpha, target).apply {
            duration = 200L
            start()
        }
    }

    private fun finishWithAction(action: String) {
        val data = Intent().apply {
            putExtra(EXTRA_RESULT_ACTION, action)
            putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {
        const val EXTRA_IMAGE_URL = "image_url"
        const val EXTRA_PEER_NAME = "peer_name"
        const val EXTRA_PEER_AVATAR = "peer_avatar"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_MESSAGE_ID = "message_id"

        const val EXTRA_RESULT_ACTION = "result_action"
        const val ACTION_REPLY = "reply"
        const val ACTION_REACT = "react"

        fun intent(
            context: android.content.Context,
            imageUrl: String,
            peerName: String,
            peerAvatar: String,
            timestamp: String,
            messageId: String,
        ): Intent = Intent(context, FullscreenImageActivity::class.java).apply {
            putExtra(EXTRA_IMAGE_URL, imageUrl)
            putExtra(EXTRA_PEER_NAME, peerName)
            putExtra(EXTRA_PEER_AVATAR, peerAvatar)
            putExtra(EXTRA_TIMESTAMP, timestamp)
            putExtra(EXTRA_MESSAGE_ID, messageId)
        }
    }
}
