package com.gmwapp.hima.activities

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityIplRoomCallBinding
import com.gmwapp.hima.models.IplTeam
import com.gmwapp.hima.models.RoomMember
import com.gmwapp.hima.viewmodels.AgoraViewModel
import com.gmwapp.hima.viewmodels.IplRoomViewModel
import com.gmwapp.hima.viewmodels.toRoomMember
import dagger.hilt.android.AndroidEntryPoint
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig

@AndroidEntryPoint
class IplRoomCallActivity : AppCompatActivity() {

    private val TAG = "IplRoomCall"
    private lateinit var binding: ActivityIplRoomCallBinding
    private val viewModel: IplRoomViewModel by viewModels()
    private val agoraViewModel: AgoraViewModel by viewModels()

    // Agora
    private var agoraEngine: RtcEngine? = null
    private var agoraToken: String? = null
    private var agoraAppId: String? = null
    private var channelName: String = ""
    private var isAgoraJoined = false
    private val PERMISSION_REQ_ID = 22
    private val REQUESTED_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)

    // Map Agora uid -> member index for speaking detection
    private val uidToMemberIndex = mutableMapOf<Int, Int>()

    // Room state
    private var isMuted = false
    private var isSpeakerOn = true
    private var hasJoinedToSpeak = false
    private var isCreator = false
    private var timerSeconds = 0
    private val handler = Handler(Looper.getMainLooper())
    private var roomId = 0
    private val userId: Int
        get() = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0

    // Poll room details every 5 seconds
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (roomId > 0) {
                viewModel.getRoomDetails(roomId)
            }
            handler.postDelayed(this, 5000)
        }
    }

    private lateinit var participantViews: List<ParticipantSlot>
    private var members = mutableListOf<RoomMember>()

    private val avatarColors = listOf(
        "#4CAF50", "#2196F3", "#FF9800", "#9C27B0",
        "#E91E63", "#00BCD4", "#FF5722", "#3F51B5"
    )

    data class ParticipantSlot(
        val root: View,
        val flAvatar: FrameLayout,
        val avatarBg: View,
        val avatarInitials: TextView,
        val name: TextView,
        val flMuteIndicator: FrameLayout,
        val creatorBadge: TextView,
        val speakingRing: View,
        val emptyCricketBall: ImageView,
        val emptyLabel: TextView,
        val remainingMinutes: TextView
    )

    // ===== AGORA EVENT HANDLER =====
    private val mRtcEventHandler = object : IRtcEngineEventHandler() {

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            Log.d(TAG, "Agora: Joined channel $channel, uid=$uid")
            isAgoraJoined = true
            runOnUiThread {
                showNotification(getString(R.string.you_joined))
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.d(TAG, "Agora: Remote user joined uid=$uid")
            runOnUiThread {
                // Find first non-self member without a uid mapping
                val unmappedIndex = members.indexOfFirst { it.id != userId && !uidToMemberIndex.containsValue(members.indexOf(it)) }
                if (unmappedIndex >= 0) {
                    uidToMemberIndex[uid] = unmappedIndex
                }
                showNotification("A user joined the room")
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d(TAG, "Agora: Remote user offline uid=$uid, reason=$reason")
            uidToMemberIndex.remove(uid)
            // Room details polling will update the member list
        }

        override fun onUserMuteAudio(uid: Int, muted: Boolean) {
            Log.d(TAG, "Agora: User $uid mute=$muted")
            runOnUiThread {
                val memberIndex = uidToMemberIndex[uid]
                if (memberIndex != null && memberIndex < members.size) {
                    members[memberIndex] = members[memberIndex].copy(isMuted = muted)
                    if (memberIndex < participantViews.size) {
                        participantViews[memberIndex].flMuteIndicator.visibility =
                            if (muted) View.VISIBLE else View.GONE
                    }
                }
            }
        }

        override fun onAudioVolumeIndication(
            speakers: Array<out AudioVolumeInfo>,
            totalVolume: Int
        ) {
            runOnUiThread {
                // Reset all speaking states
                val speakingUids = mutableSetOf<Int>()

                for (speaker in speakers) {
                    if (speaker.volume > 50) {
                        speakingUids.add(speaker.uid)
                    }
                }

                for (i in members.indices) {
                    val wasSpeaking = members[i].isSpeaking
                    val isSpeaking = if (members[i].id == userId || members[i].name == "You") {
                        // uid 0 = local user in Agora
                        speakingUids.contains(0)
                    } else {
                        // Check if any mapped Agora uid for this member is speaking
                        uidToMemberIndex.any { (agoraUid, memberIdx) ->
                            memberIdx == i && speakingUids.contains(agoraUid)
                        }
                    }

                    if (wasSpeaking != isSpeaking) {
                        members[i] = members[i].copy(isSpeaking = isSpeaking)
                        if (i < participantViews.size) {
                            val slot = participantViews[i]
                            if (isSpeaking) {
                                slot.speakingRing.visibility = View.VISIBLE
                                animateSpeakingRing(slot.speakingRing)
                            } else {
                                slot.speakingRing.visibility = View.INVISIBLE
                                slot.speakingRing.clearAnimation()
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== LIFECYCLE =====

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIplRoomCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roomId = intent.getIntExtra("room_id", 0)
        channelName = "ipl_room_$roomId"

        handleNavBarInsets()
        setupRoomInfo()
        setupParticipantViews()
        updateParticipantSlots()
        setupControls()
        setupReactions()
        startTimer()
        observeRoomDetails()
        observeAgoraToken()

        // Fetch room details immediately, then poll every 5 seconds
        if (roomId > 0) {
            viewModel.getRoomDetails(roomId)
        }
        handler.postDelayed(pollRunnable, 5000)

        // Start with controls hidden until user joins to speak
        setControlsEnabled(false)
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacksAndMessages(null)
        leaveAgoraChannel()
        super.onDestroy()
    }

    // ===== AGORA SETUP =====

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initAgoraAndJoin()
            } else {
                Toast.makeText(this, "Audio permission required for voice chat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeAgoraToken() {
        agoraViewModel.agoraTokenLiveData.observe(this) { response ->
            if (response != null && response.success == true && !response.token.isNullOrEmpty()) {
                agoraToken = response.token
                agoraAppId = response.app_id
                Log.d(TAG, "Agora token received, appId=${agoraAppId != null}")

                if (agoraAppId.isNullOrEmpty()) {
                    Log.e(TAG, "AppId not received from backend")
                    // Still allow room to work without voice
                    startSpeakingSimulation()
                    return@observe
                }

                if (checkAudioPermission()) {
                    initAgoraAndJoin()
                } else {
                    ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
                }
            } else {
                Log.e(TAG, "Failed to get Agora token: ${response?.message}")
                // Fallback to simulation if token fails
                startSpeakingSimulation()
            }
        }

        agoraViewModel.agoraTokenErrorLiveData.observe(this) { error ->
            Log.e(TAG, "Agora token error: $error")
            // Fallback to simulation
            startSpeakingSimulation()
        }
    }

    private fun requestAgoraToken() {
        Log.d(TAG, "Requesting Agora token for channel: $channelName")
        agoraViewModel.getAgoraToken(channelName, userId, "publisher", 3600)
    }

    private fun initAgoraAndJoin() {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = agoraAppId!!
            config.mEventHandler = mRtcEventHandler
            agoraEngine = RtcEngine.create(config)

            agoraEngine?.enableAudio()
            agoraEngine?.setAudioProfile(
                Constants.AUDIO_PROFILE_SPEECH_STANDARD,
                Constants.AUDIO_SCENARIO_DEFAULT
            )
            // Enable speaking detection: 200ms interval, report for 3 speakers
            agoraEngine?.enableAudioVolumeIndication(200, 3, true)

            // Set speaker on by default
            agoraEngine?.setEnableSpeakerphone(true)

            // Join channel
            val options = ChannelMediaOptions()
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            options.autoSubscribeAudio = true

            agoraEngine?.joinChannel(agoraToken, channelName, userId, options)
            Log.d(TAG, "Agora: Joining channel $channelName")

        } catch (e: Exception) {
            Log.e(TAG, "Agora init failed: ${e.message}")
            // Fallback to simulation
            startSpeakingSimulation()
        }
    }

    private fun leaveAgoraChannel() {
        if (isAgoraJoined) {
            agoraEngine?.leaveChannel()
            isAgoraJoined = false
        }
        Thread {
            RtcEngine.destroy()
            agoraEngine = null
        }.start()
    }

    // ===== UI SETUP =====

    private fun handleNavBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.reactionsBar) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom + (8 * resources.displayMetrics.density).toInt())
            insets
        }
    }

    private fun setupRoomInfo() {
        val roomName = intent.getStringExtra("room_name") ?: "IPL Room"
        val teamAName = intent.getStringExtra("team_a") ?: "MI"
        val teamBName = intent.getStringExtra("team_b") ?: "CSK"

        binding.tvRoomName.text = roomName

        val teamA = try { IplTeam.valueOf(teamAName) } catch (e: Exception) { IplTeam.MI }
        val teamB = try { IplTeam.valueOf(teamBName) } catch (e: Exception) { IplTeam.CSK }

        setTeamBadge(binding.viewHeaderTeamA, binding.tvHeaderTeamA, teamA)
        setTeamBadge(binding.viewHeaderTeamB, binding.tvHeaderTeamB, teamB)

        updateMemberCount()
    }

    private fun setTeamBadge(bgView: View, textView: TextView, team: IplTeam) {
        val bg = bgView.background.mutate() as GradientDrawable
        bg.setColor(Color.parseColor(team.primaryColor))
        bgView.background = bg
        textView.text = team.abbreviation
    }

    private fun setupParticipantViews() {
        participantViews = listOf(
            createParticipantSlot(binding.participant1.root),
            createParticipantSlot(binding.participant2.root),
            createParticipantSlot(binding.participant3.root),
            createParticipantSlot(binding.participant4.root)
        )
    }

    private fun createParticipantSlot(view: View): ParticipantSlot {
        return ParticipantSlot(
            root = view,
            flAvatar = view.findViewById(R.id.fl_avatar),
            avatarBg = view.findViewById(R.id.view_avatar_bg),
            avatarInitials = view.findViewById(R.id.tv_avatar_initials),
            name = view.findViewById(R.id.tv_participant_name),
            flMuteIndicator = view.findViewById(R.id.fl_mute_indicator),
            creatorBadge = view.findViewById(R.id.tv_creator_badge),
            speakingRing = view.findViewById(R.id.view_speaking_ring),
            emptyCricketBall = view.findViewById(R.id.iv_empty_cricket_ball),
            emptyLabel = view.findViewById(R.id.tv_empty_label),
            remainingMinutes = view.findViewById(R.id.tv_remaining_minutes)
        )
    }

    private fun observeRoomDetails() {
        viewModel.roomDetailLiveData.observe(this) { response ->
            if (response?.success == true && response.data != null) {
                // If room is no longer live, exit
                if (!response.data.isLive) {
                    Toast.makeText(this, "Room has ended", Toast.LENGTH_SHORT).show()
                    leaveAgoraChannel()
                    finish()
                    return@observe
                }

                if (response.data.members != null) {
                    members = response.data.members.map { it.toRoomMember() }.toMutableList()
                    updateParticipantSlots()
                    updateMemberCount()

                    // Check if current user is the creator
                    val self = members.find { it.id == userId }
                    if (self?.isCreator == true) {
                        isCreator = true
                    }

                    // Creator auto-join: if creator is in members but hasn't joined voice yet
                    if (isCreator && !hasJoinedToSpeak && self != null) {
                        hasJoinedToSpeak = true
                        binding.btnJoinToSpeak.visibility = View.GONE
                        setControlsEnabled(true)
                        requestAgoraToken()
                    }

                    // Auto-kick detection: if user joined but is no longer in the member list
                    if (hasJoinedToSpeak && members.none { it.id == userId }) {
                        Toast.makeText(this, "You were removed - not enough coins", Toast.LENGTH_LONG).show()
                        hasJoinedToSpeak = false
                        leaveAgoraChannel()
                        finish()
                        return@observe
                    }

                    // Low coins warning: show notification when 2 or fewer minutes remaining
                    if (hasJoinedToSpeak && self != null && !self.isCreator && self.remainingMinutes in 1..2) {
                        showNotification("Low coins! ~${self.remainingMinutes} min remaining")
                    }
                }
            }
        }

        viewModel.joinRoomLiveData.observe(this) { response ->
            if (response == null || response.success != true) {
                val msg = response?.message ?: ""
                // "Already in room" means user is a member — just proceed with voice
                if (msg.contains("already", ignoreCase = true)) {
                    // Not an error — user was auto-added (creator) or rejoining
                    requestAgoraToken()
                    return@observe
                }
                // Actual join failure — rollback UI
                Toast.makeText(this, msg.ifEmpty { "Failed to join room" }, Toast.LENGTH_SHORT).show()
                hasJoinedToSpeak = false
                binding.btnJoinToSpeak.visibility = View.VISIBLE
                setControlsEnabled(false)
                members.removeAll { it.id == userId }
                updateParticipantSlots()
                leaveAgoraChannel()
            }
        }

        viewModel.errorLiveData.observe(this) { error ->
            if (!error.isNullOrEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getInitials(name: String): String {
        val parts = name.trim().split(" ")
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            name.length >= 2 -> name.take(2).uppercase()
            else -> name.uppercase()
        }
    }

    // ===== JOIN TO SPEAK =====

    private fun setControlsEnabled(enabled: Boolean) {
        binding.controlsContainer.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun joinToSpeak() {
        if (hasJoinedToSpeak) return
        if (members.size >= 4) return

        hasJoinedToSpeak = true
        binding.btnJoinToSpeak.visibility = View.GONE

        // Call join API
        viewModel.joinRoom(userId, roomId)

        // Add "You" to members locally
        members.add(RoomMember(
            id = userId,
            name = "You",
            isCreator = false
        ))
        updateParticipantSlots()

        // Enable controls
        setControlsEnabled(true)

        // Request Agora token and join voice channel
        requestAgoraToken()
    }

    private fun showNotification(text: String) {
        binding.tvJoinNotification.text = text
        binding.tvJoinNotification.visibility = View.VISIBLE
        binding.tvJoinNotification.alpha = 0f

        binding.tvJoinNotification.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        handler.postDelayed({
            binding.tvJoinNotification.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction {
                    binding.tvJoinNotification.visibility = View.GONE
                }
                .start()
        }, 2500)
    }

    // ===== PARTICIPANT SLOTS =====

    private fun updateParticipantSlots() {
        for (i in participantViews.indices) {
            val slot = participantViews[i]
            if (i < members.size) {
                showOccupiedSlot(slot, members[i], i)
            } else {
                showEmptySlot(slot)
            }
        }
        updateMemberCount()

        if (members.size >= 4 && !hasJoinedToSpeak) {
            binding.btnJoinToSpeak.visibility = View.GONE
        }
    }

    private fun showOccupiedSlot(slot: ParticipantSlot, member: RoomMember, index: Int) {
        slot.flAvatar.visibility = View.VISIBLE
        slot.name.visibility = View.VISIBLE
        slot.emptyCricketBall.visibility = View.GONE
        slot.emptyLabel.visibility = View.GONE

        val color = avatarColors[index % avatarColors.size]
        val avatarDrawable = slot.avatarBg.background.mutate() as GradientDrawable
        avatarDrawable.setColor(Color.parseColor(color))
        slot.avatarInitials.text = getInitials(member.name)
        slot.name.text = member.name
        slot.creatorBadge.visibility = if (member.isCreator) View.VISIBLE else View.GONE
        slot.flMuteIndicator.visibility = if (member.isMuted) View.VISIBLE else View.GONE

        // Show remaining minutes for non-creators
        if (!member.isCreator && member.remainingMinutes > 0) {
            slot.remainingMinutes.visibility = View.VISIBLE
            slot.remainingMinutes.text = "${member.remainingMinutes} min left"
            if (member.remainingMinutes <= 2) {
                slot.remainingMinutes.setTextColor(Color.parseColor("#FF6D00"))
            } else {
                slot.remainingMinutes.setTextColor(Color.parseColor("#80FFFFFF"))
            }
        } else {
            slot.remainingMinutes.visibility = View.GONE
        }

        if (member.isSpeaking) {
            slot.speakingRing.visibility = View.VISIBLE
            animateSpeakingRing(slot.speakingRing)
        } else {
            slot.speakingRing.visibility = View.INVISIBLE
            slot.speakingRing.clearAnimation()
        }

        slot.root.setBackgroundResource(R.drawable.bg_participant_card)
    }

    private fun animateSpeakingRing(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f).apply {
            repeatCount = ObjectAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f).apply {
            repeatCount = ObjectAnimator.INFINITE
        }
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.6f, 1f).apply {
            repeatCount = ObjectAnimator.INFINITE
        }
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.duration = 1000
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()
    }

    private fun showEmptySlot(slot: ParticipantSlot) {
        slot.flAvatar.visibility = View.GONE
        slot.name.visibility = View.GONE
        slot.flMuteIndicator.visibility = View.GONE
        slot.creatorBadge.visibility = View.GONE
        slot.speakingRing.visibility = View.INVISIBLE
        slot.remainingMinutes.visibility = View.GONE
        slot.emptyCricketBall.visibility = View.VISIBLE
        slot.emptyLabel.visibility = View.VISIBLE
        slot.root.setBackgroundResource(R.drawable.bg_empty_slot_dashed)
    }

    private fun updateMemberCount() {
        binding.tvMemberCount.text = "${members.size}/4"
    }

    // ===== REACTIONS =====

    private fun setupReactions() {
        binding.btnReactionFour.setOnClickListener {
            showFloatingReaction("FOUR!", "4️⃣", "#4CAF50")
            viewModel.sendReaction(userId, roomId, "four")
        }
        binding.btnReactionSix.setOnClickListener {
            showFloatingReaction("SIX!", "6️⃣", "#FF6D00")
            viewModel.sendReaction(userId, roomId, "six")
        }
        binding.btnReactionWicket.setOnClickListener {
            showFloatingReaction("WICKET!", "🏏", "#E53935")
            viewModel.sendReaction(userId, roomId, "wicket")
        }
        binding.btnReactionGreat.setOnClickListener {
            showFloatingReaction("GREAT!", "👏", "#2196F3")
            viewModel.sendReaction(userId, roomId, "great")
        }
    }

    private fun leaveAndFinish() {
        // Always call leave for creator (auto-added) or anyone who joined
        if (hasJoinedToSpeak || isCreator) {
            viewModel.leaveRoom(userId, roomId)
        }
        handler.removeCallbacks(pollRunnable)
        leaveAgoraChannel()
        finish()
    }

    private fun showFloatingReaction(text: String, emoji: String, colorHex: String) {
        val overlay = binding.flReactionOverlay

        val reactionView = TextView(this).apply {
            this.text = "$emoji $text"
            textSize = 32f
            setTextColor(Color.WHITE)
            typeface = resources.getFont(R.font.poppins_bold)
            setShadowLayer(16f, 0f, 4f, Color.parseColor(colorHex))
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        overlay.addView(reactionView, params)

        val scaleX = ObjectAnimator.ofFloat(reactionView, "scaleX", 0f, 1.2f, 1f, 1f, 0.8f)
        val scaleY = ObjectAnimator.ofFloat(reactionView, "scaleY", 0f, 1.2f, 1f, 1f, 0.8f)
        val translateY = ObjectAnimator.ofFloat(reactionView, "translationY", 0f, 0f, 0f, -200f)
        val alphaAnim = ObjectAnimator.ofFloat(reactionView, "alpha", 0f, 1f, 1f, 0f)

        val animSet = AnimatorSet()
        animSet.playTogether(scaleX, scaleY, translateY, alphaAnim)
        animSet.duration = 1800
        animSet.start()

        handler.postDelayed({
            overlay.removeView(reactionView)
        }, 1900)
    }

    // ===== CONTROLS =====

    private fun setupControls() {
        binding.btnJoinToSpeak.setOnClickListener { joinToSpeak() }

        // Mute toggle - real Agora mute
        binding.btnMute.setOnClickListener {
            if (!hasJoinedToSpeak) return@setOnClickListener
            isMuted = !isMuted
            agoraEngine?.muteLocalAudioStream(isMuted)
            binding.btnMute.setImageResource(
                if (isMuted) R.drawable.mute_img else R.drawable.unmute_img
            )
            binding.tvMuteLabel.text = if (isMuted) "Unmute" else "Mic"
            viewModel.toggleMute(userId, roomId, isMuted)
        }

        // Speaker toggle - real Agora speaker
        binding.btnSpeaker.setOnClickListener {
            if (!hasJoinedToSpeak) return@setOnClickListener
            isSpeakerOn = !isSpeakerOn
            agoraEngine?.setEnableSpeakerphone(isSpeakerOn)
            binding.btnSpeaker.alpha = if (isSpeakerOn) 1.0f else 0.5f
        }

        // Leave room
        binding.btnLeave.setOnClickListener { leaveAndFinish() }
        binding.ivBack.setOnClickListener { leaveAndFinish() }
    }

    // ===== TIMER =====

    private fun startTimer() {
        handler.post(object : Runnable {
            override fun run() {
                timerSeconds++
                val minutes = timerSeconds / 60
                val seconds = timerSeconds % 60
                binding.tvTimer.text = String.format("%02d:%02d", minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        })
    }

    // Fallback: simulate speaking when Agora not available
    private fun startSpeakingSimulation() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (members.isNotEmpty() && agoraEngine == null) {
                    val randomIndex = (0 until members.size).random()
                    members = members.mapIndexed { index, member ->
                        if (index == randomIndex) {
                            member.copy(isSpeaking = !member.isSpeaking)
                        } else {
                            member.copy(isSpeaking = false)
                        }
                    }.toMutableList()
                    updateParticipantSlots()
                }
                if (agoraEngine == null) {
                    handler.postDelayed(this, 2000 + (Math.random() * 2000).toLong())
                }
            }
        }, 2000)
    }
}
