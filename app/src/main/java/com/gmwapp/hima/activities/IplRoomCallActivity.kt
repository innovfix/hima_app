package com.gmwapp.hima.activities

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
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
import androidx.appcompat.app.AlertDialog
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
    private val profileViewModel: com.gmwapp.hima.viewmodels.ProfileViewModel by viewModels()

    // Agora
    private var agoraEngine: RtcEngine? = null
    private var agoraToken: String? = null
    private var agoraAppId: String? = null
    private var channelName: String = ""
    private var isAgoraJoined = false
    private var dataStreamId: Int = -1
    private val PERMISSION_REQ_ID = 22
    private val REQUESTED_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)

    // Map Agora uid -> member index for speaking detection
    private val uidToMemberIndex = mutableMapOf<Int, Int>()

    // Room state
    private var isMuted = false
    private var isSpeakerOn = true
    private var hasJoinedToSpeak = false
    private var isCreator = false
    private var inviteCode: String = ""
    private var creatorUserId: Int = -1
    private var timerSeconds = 0
    private val handler = Handler(Looper.getMainLooper())
    private var roomId = 0
    private val userId: Int
        get() = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0

    // No polling — room details fetched once on entry; countdown is local-only.

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
        val remainingMinutes: TextView,
        val flTeamBadge: FrameLayout,
        val teamBadgeBg: View,
        val teamBadgeAbbr: TextView
    )

    // ===== AGORA EVENT HANDLER =====
    private val mRtcEventHandler = object : IRtcEngineEventHandler() {

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            Log.d(TAG, "Agora: Joined channel $channel, uid=$uid")
            isAgoraJoined = true
            // Create a reliable data stream so we can broadcast mute/leave/reaction events
            try {
                val cfg = io.agora.rtc2.DataStreamConfig().apply {
                    syncWithAudio = false
                    ordered = true
                }
                val streamId = agoraEngine?.createDataStream(cfg) ?: -1
                if (streamId >= 0) dataStreamId = streamId
                Log.d(TAG, "Agora: data stream id=$dataStreamId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create data stream: ${e.message}")
            }
        }

        override fun onStreamMessage(uid: Int, streamId: Int, data: ByteArray?) {
            if (data == null) return
            try {
                val msg = String(data, Charsets.UTF_8)
                Log.d(TAG, "Agora: stream msg from uid=$uid: $msg")
                runOnUiThread { handleStreamMessage(uid, msg) }
            } catch (e: Exception) {
                Log.e(TAG, "onStreamMessage parse failed: ${e.message}")
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
                // Re-fetch room details so the slot updates with the new member
                if (roomId > 0) viewModel.getRoomDetails(roomId)
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d(TAG, "Agora: Remote user offline uid=$uid, reason=$reason")
            uidToMemberIndex.remove(uid)
            runOnUiThread {
                // If the host (creator) just left, end the call for everyone immediately
                if (creatorUserId > 0 && uid == creatorUserId && !isCreator) {
                    Toast.makeText(this@IplRoomCallActivity, "Host ended the room", Toast.LENGTH_SHORT).show()
                    leaveAndFinish()
                    return@runOnUiThread
                }
                // Remove the leaver from the local member list immediately
                // (Agora uid == app userId in our channel join)
                val idx = members.indexOfFirst { it.id == uid }
                if (idx >= 0) {
                    members.removeAt(idx)
                    updateParticipantSlots()
                    updateMemberCount()
                }
                // Also refresh from server to stay consistent
                if (roomId > 0) viewModel.getRoomDetails(roomId)
            }
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
        inviteCode = intent.getStringExtra("invite_code") ?: ""

        // Intercept the system back press (works on Android 13+ predictive back too)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showLeaveConfirmation()
            }
        })

        handleNavBarInsets()
        setupRoomInfo()
        setupParticipantViews()
        updateParticipantSlots()
        setupControls()
        setupReactions()
        startTimer()
        observeRoomDetails()
        observeAgoraToken()

        // Check if user is creator from intent
        val userGender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender ?: ""
        val isFemaleUser = userGender.equals("female", ignoreCase = true)
        if (isFemaleUser) {
            // Female created the room — she's the creator, auto-join immediately
            isCreator = true
            hasJoinedToSpeak = true
            binding.btnJoinToSpeak.visibility = View.GONE
            setControlsEnabled(true)
            requestAgoraToken()
        } else {
            // Male joining — also auto-join immediately (already joined via API)
            hasJoinedToSpeak = true
            binding.btnJoinToSpeak.visibility = View.GONE
            setControlsEnabled(true)
            requestAgoraToken()
        }

        // Fetch room details ONCE on entry — no polling.
        // The first response delivers remaining_seconds; the local countdown takes over from there.
        if (roomId > 0) {
            viewModel.getRoomDetails(roomId)
        }

        // For male, fetch remaining time via the existing get_remaining_time API (call_type=audio)
        if (!isFemaleUser) {
            fetchRemainingTimeAndStart()
        }
    }

    private fun fetchRemainingTimeAndStart() {
        val uid = userId
        if (uid <= 0) return
        profileViewModel.getRemainingTime(uid, "audio", object : com.gmwapp.hima.retrofit.callbacks.NetworkCallback<com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse> {
            override fun onResponse(call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse>, response: retrofit2.Response<com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse>) {
                val body = response.body()
                if (body?.success == true && body.data?.remaining_time != null) {
                    // remaining_time is in "M:SS" format
                    val parts = body.data.remaining_time.split(":")
                    val mins = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val secs = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    val totalSeconds = mins * 60 + secs
                    runOnUiThread {
                        if (totalSeconds > 0) {
                            startMaleCountdown(totalSeconds)
                        } else {
                            Toast.makeText(this@IplRoomCallActivity, "No time remaining", Toast.LENGTH_LONG).show()
                            leaveAndFinish()
                        }
                    }
                }
            }

            override fun onFailure(call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse>, t: Throwable) {
                Log.e(TAG, "getRemainingTime failed: ${t.message}")
            }

            override fun onNoNetwork() {
                Log.e(TAG, "getRemainingTime: no network")
            }
        })
    }

    override fun onDestroy() {
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

    // ===== AGORA DATA STREAM HELPERS =====

    private fun sendDataStreamMessage(payload: org.json.JSONObject) {
        try {
            payload.put("from_uid", userId)
            if (dataStreamId < 0 || agoraEngine == null) return
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            agoraEngine?.sendStreamMessage(dataStreamId, bytes)
            Log.d(TAG, "Sent data stream: $payload")
        } catch (e: Exception) {
            Log.e(TAG, "sendDataStreamMessage failed: ${e.message}")
        }
    }

    private fun handleStreamMessage(senderUid: Int, message: String) {
        try {
            val json = org.json.JSONObject(message)
            val type = json.optString("type", "")
            when (type) {
                "mute" -> {
                    val isMuted = json.optBoolean("muted", false)
                    val targetUid = json.optInt("from_uid", senderUid)
                    val idx = members.indexOfFirst { it.id == targetUid }
                    if (idx >= 0) {
                        members[idx] = members[idx].copy(isMuted = isMuted)
                        if (idx < participantViews.size) {
                            participantViews[idx].flMuteIndicator.visibility =
                                if (isMuted) View.VISIBLE else View.GONE
                        }
                    }
                }
                "leave" -> {
                    val targetUid = json.optInt("from_uid", senderUid)
                    val idx = members.indexOfFirst { it.id == targetUid }
                    if (idx >= 0) {
                        val wasCreator = members[idx].isCreator
                        members.removeAt(idx)
                        updateParticipantSlots()
                        updateMemberCount()
                        if (wasCreator || (creatorUserId > 0 && targetUid == creatorUserId)) {
                            Toast.makeText(this, "Host ended the room", Toast.LENGTH_SHORT).show()
                            leaveAndFinish()
                            return
                        }
                    }
                    // Refresh from server too just in case
                    if (roomId > 0) viewModel.getRoomDetails(roomId)
                }
                "reaction" -> {
                    val rxType = json.optString("reaction", "")
                    when (rxType) {
                        "four" -> showFloatingReaction("FOUR!", "4️⃣", "#4CAF50")
                        "six" -> showFloatingReaction("SIX!", "6️⃣", "#FF6D00")
                        "wicket" -> showFloatingReaction("WICKET!", "🏏", "#E53935")
                        "great" -> showFloatingReaction("GREAT!", "👏", "#2196F3")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleStreamMessage error: ${e.message}")
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
            remainingMinutes = view.findViewById(R.id.tv_remaining_minutes),
            flTeamBadge = view.findViewById(R.id.fl_team_badge),
            teamBadgeBg = view.findViewById(R.id.view_team_badge_bg),
            teamBadgeAbbr = view.findViewById(R.id.tv_team_badge_abbr)
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

                // Capture the room creator's user id so we can detect when they leave
                creatorUserId = response.data.creatorId

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

        // Hide the per-slot "min left" text — replaced by the global countdown timer at top
        slot.remainingMinutes.visibility = View.GONE

        // Show team badge if member has selected a team
        val teamAbbr = member.iplTeam
        if (!teamAbbr.isNullOrEmpty()) {
            val team = com.gmwapp.hima.models.IplTeam.values().find { it.abbreviation == teamAbbr }
            if (team != null) {
                slot.flTeamBadge.visibility = View.VISIBLE
                try {
                    val badgeDrawable = slot.teamBadgeBg.background.mutate() as GradientDrawable
                    badgeDrawable.setColor(Color.parseColor(team.primaryColor))
                    badgeDrawable.setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#0F172A"))
                } catch (_: Exception) {}
                slot.teamBadgeAbbr.text = team.abbreviation
            } else {
                slot.flTeamBadge.visibility = View.GONE
            }
        } else {
            slot.flTeamBadge.visibility = View.GONE
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
        slot.flTeamBadge.visibility = View.GONE
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
            broadcastReaction("four")
        }
        binding.btnReactionSix.setOnClickListener {
            showFloatingReaction("SIX!", "6️⃣", "#FF6D00")
            viewModel.sendReaction(userId, roomId, "six")
            broadcastReaction("six")
        }
        binding.btnReactionWicket.setOnClickListener {
            showFloatingReaction("WICKET!", "🏏", "#E53935")
            viewModel.sendReaction(userId, roomId, "wicket")
            broadcastReaction("wicket")
        }
        binding.btnReactionGreat.setOnClickListener {
            showFloatingReaction("GREAT!", "👏", "#2196F3")
            viewModel.sendReaction(userId, roomId, "great")
            broadcastReaction("great")
        }
    }

    private fun broadcastReaction(rxType: String) {
        sendDataStreamMessage(org.json.JSONObject().apply {
            put("type", "reaction")
            put("reaction", rxType)
        })
    }

    private fun leaveAndFinish() {
        // Notify other room members instantly via Agora data stream.
        // Agora's data stream is best-effort; sending 3 times improves reliability.
        try {
            val payload = org.json.JSONObject().apply {
                put("type", "leave")
                put("is_creator", isCreator)
            }
            sendDataStreamMessage(payload)
            sendDataStreamMessage(payload)
            sendDataStreamMessage(payload)
        } catch (_: Exception) {}

        if (isCreator) {
            viewModel.closeRoom(userId, roomId)
        } else if (hasJoinedToSpeak) {
            viewModel.leaveRoom(userId, roomId)
        }

        // Give Agora a tiny moment to flush the stream messages, then leave.
        handler.postDelayed({
            try {
                handler.removeCallbacksAndMessages(null)
                leaveAgoraChannel()
                finish()
            } catch (_: Exception) { finish() }
        }, 250)
    }

    private fun showLeaveConfirmation() {
        val view = layoutInflater.inflate(R.layout.dialog_ipl_leave_confirm, null)
        val dialog = AlertDialog.Builder(this, R.style.AlertDialogTransparent)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val title = view.findViewById<TextView>(R.id.tv_dialog_title)
        val message = view.findViewById<TextView>(R.id.tv_dialog_message)
        val btnConfirm = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dialog_confirm)
        val btnCancel = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dialog_cancel)

        if (isCreator) {
            title.text = "Close Room?"
            message.text = "If you leave, the room will be closed for everyone. Are you sure?"
            btnConfirm.text = "Close"
        } else {
            title.text = "Leave Room?"
            message.text = "Are you sure you want to leave this IPL room?"
            btnConfirm.text = "Leave"
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            leaveAndFinish()
        }

        dialog.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showLeaveConfirmation()
    }

    private fun copyInviteCode() {
        if (inviteCode.isNotEmpty()) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("IPL Invite Code", inviteCode))
            Toast.makeText(this, "Invite code copied: $inviteCode", Toast.LENGTH_SHORT).show()
        }
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
            // Broadcast to other room members in real time
            sendDataStreamMessage(org.json.JSONObject().apply {
                put("type", "mute")
                put("muted", isMuted)
            })
        }

        // Speaker toggle - real Agora speaker
        binding.btnSpeaker.setOnClickListener {
            if (!hasJoinedToSpeak) return@setOnClickListener
            isSpeakerOn = !isSpeakerOn
            agoraEngine?.setEnableSpeakerphone(isSpeakerOn)
            binding.btnSpeaker.alpha = if (isSpeakerOn) 1.0f else 0.5f
        }

        // Leave room — always show confirmation dialog
        binding.btnLeave.setOnClickListener { showLeaveConfirmation() }
        binding.ivBack.setOnClickListener { showLeaveConfirmation() }

        // Invite code display for creator
        if (inviteCode.isNotEmpty()) {
            binding.tvInviteCode.visibility = View.VISIBLE
            binding.tvInviteCode.text = "Code: $inviteCode"
            binding.tvInviteCode.setOnClickListener { copyInviteCode() }
            binding.btnShareCode.visibility = View.VISIBLE
            binding.btnShareCode.setOnClickListener {
                // Open chat list to share invite code
                val chatIntent = Intent(this, ChatListActivity::class.java)
                chatIntent.putExtra("share_message", "Join my IPL Room! Use invite code: $inviteCode")
                startActivity(chatIntent)
            }
        }
    }

    // ===== TIMER =====

    private var remainingSecondsLocal: Int = 0
    private var timerStarted = false

    private fun startTimer() {
        // Female (creator) doesn't need a timer — hide it
        val gender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender ?: ""
        if (gender.equals("female", ignoreCase = true)) {
            try { binding.tvTimer.visibility = View.GONE } catch (_: Exception) {}
            try { (binding.tvTimer.parent as? View)?.visibility = View.GONE } catch (_: Exception) {}
            return
        }

        // Male timer is started ONCE from the first room details response (which has remaining_seconds).
        // No more API polling — purely local countdown.
    }

    private fun startMaleCountdown(initialSeconds: Int) {
        if (timerStarted) return
        timerStarted = true
        remainingSecondsLocal = initialSeconds

        binding.tvTimer.text = formatHms(remainingSecondsLocal)

        handler.post(object : Runnable {
            override fun run() {
                if (remainingSecondsLocal <= 0) {
                    Toast.makeText(this@IplRoomCallActivity, "Time's up — coins exhausted", Toast.LENGTH_LONG).show()
                    leaveAndFinish()
                    return
                }
                remainingSecondsLocal--
                binding.tvTimer.text = formatHms(remainingSecondsLocal)
                if (remainingSecondsLocal <= 60) {
                    binding.tvTimer.setTextColor(Color.parseColor("#FF6D00"))
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun formatHms(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
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
