package com.gmwapp.hima.agora

import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object FcmUtils {
    data class LudoEvent(
        val type: String,
        val inviteId: String? = null,
        val roomCode: String? = null,
        val fromUserId: Int? = null,
        val fromUserName: String? = null,
        val joinUrl: String? = null
    )

    private val _callStatus = MutableLiveData<Pair<String, String>?>()  // Make it nullable
    val callStatus: LiveData<Pair<String, String>?> get() = _callStatus

    private val _callDeclinedStatus = MutableLiveData<Boolean>()
    val callDeclinedStatus: LiveData<Boolean> get() = _callDeclinedStatus

    var blockWordDetected = false

    // Global "user is currently busy with a call" state. Read by:
    //  • RecentFragment / HomeFragment onResume guards — refresh creator
    //    availability only when this is 0 (free).
    //  • MyFirebaseMessagingService busy-check — auto-reject incoming FCMs
    //    when this is 1 (B156a: closes the race window between caller-side
    //    "tap Call" and the connecting Activity actually being foregrounded).
    // Default 0 — a freshly-launched app must NOT be treated as busy. The
    // caller-side handlers flip this to 1 the instant the user expresses
    // call intent (RecentFragment.kt / FavouriteFragment.kt / adapters);
    // call cleanup paths flip it back to 0 (B181 fix).
    var isUserAvailable = 0

    var shouldRefreshCallList = 0

    private val _updatedTime = MutableLiveData<String?>()
    val updatedTime: LiveData<String?> get() = _updatedTime

    private val _updatedCallSwitch = MutableLiveData<Pair<String, Int>?>()
    val updatedCallSwitch: LiveData<Pair<String, Int>?> get() = _updatedCallSwitch

    // B069 — wall-clock timestamp of the most recent call-switch payload.
    // LiveData delivers its current value to any fresh observer immediately,
    // so a switchToVideo payload from a previous call would fire the next
    // call's activity observer on attach and pop a "video accept" dialog
    // even though no request was sent for the new call. Observers compare
    // this against their own attach timestamp and drop anything older.
    // Reset to 0L in [clearCallSwitch].
    @Volatile private var _callSwitchPostedAt: Long = 0L
    fun callSwitchPostedAt(): Long = _callSwitchPostedAt

    private val _userBusyStatus = MutableLiveData<Pair<String, String>?>() // (callType, userName)
    val userBusyStatus: LiveData<Pair<String, String>?> get() = _userBusyStatus

    private val _ludoEvent = MutableLiveData<LudoEvent?>()
    val ludoEvent: LiveData<LudoEvent?> get() = _ludoEvent

    fun updateCallStatus(status: String, channelName: String) {
        _callStatus.postValue(Pair(status, channelName))
        Log.d("FcmUtils", "Call status updated: $status, Channel: $channelName")
    }

    fun clearCallStatus() {
        // Use setValue when called from the main thread so callers reading
        // _callStatus.value on the very next line see null. postValue is
        // posted to the main-thread message queue and leaves the previous
        // value visible until the queue drains — MaleCallConnectingActivity
        // calls clear-then-read inside lifecycleScope.launch (Main), and a
        // stale Pair("accepted", oldChannel) from a prior call was making
        // every new connecting activity auto-redirect to MainActivity
        // ("call already accepted") before the call could even start.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _callStatus.value = null
        } else {
            _callStatus.postValue(null)
        }
        Log.d("FcmUtils", "Call status cleared")
    }



    fun updateRemainingTime(message: String) {
        _updatedTime.postValue(message)
    }

    fun clearRemainingTime() {
        _updatedTime.postValue(null)
    }



    fun UpdateCallSwitch(message: String, senderId: Int) {
        Log.d(
            "MaleVideoEndFlow",
            "FcmUtils.UpdateCallSwitch post message=$message senderId=$senderId"
        )
        _callSwitchPostedAt = System.currentTimeMillis()
        _updatedCallSwitch.postValue(Pair(message, senderId))
    }

    fun clearCallSwitch() {
        _callSwitchPostedAt = 0L
        _updatedCallSwitch.postValue(null)
    }

    fun updateUserBusyStatus(callType: String, userName: String) {
        _userBusyStatus.postValue(Pair(callType, userName))
        Log.d("FcmUtils", "User busy status updated: callType=$callType, userName=$userName")
    }

    fun clearUserBusyStatus() {
        _userBusyStatus.postValue(null)
        Log.d("FcmUtils", "User busy status cleared")
    }

    // Camera-unavailable signal. Posted by MyFirebaseMessagingService when the
    // creator-side detected a broken camera and is in the 30-second grace
    // window before disconnecting. Carries the channel name so the caller's
    // observer can ignore messages for other in-flight calls.
    private val _cameraUnavailableStatus = MutableLiveData<String?>()
    val cameraUnavailableStatus: LiveData<String?> get() = _cameraUnavailableStatus

    fun updateCameraUnavailable(channelName: String) {
        _cameraUnavailableStatus.postValue(channelName)
        Log.d("FcmUtils", "Camera-unavailable signal: channel=$channelName")
    }

    fun clearCameraUnavailable() {
        _cameraUnavailableStatus.postValue(null)
    }

    fun updateLudoEvent(event: LudoEvent) {
        _ludoEvent.postValue(event)
        Log.d("FcmUtils", "Ludo event updated: type=${event.type}, invite=${event.inviteId}")
    }

    fun clearLudoEvent() {
        _ludoEvent.postValue(null)
    }


    private val _giftReceived = MutableLiveData<String?>()
    val giftReceived: LiveData<String?> get() = _giftReceived

    fun giftReceivedImage(image: String) {
        _giftReceived.postValue(image)
    }

    fun cleargiftReceived() {
        _giftReceived.postValue(null)
    }


    val greyScreenLiveData = MutableLiveData<String>()

    /**
     * Server-driven force-end signal. Backend pushes
     * `message == "callEndedNoCoins"` FCM when the male's coins are
     * exhausted during an active call; we forward the call_id here so
     * the matching active-call activity can leaveChannel() without
     * depending on the client-side countdown timer (see B184 follow-up).
     *
     * Value carries `(callId, reason)` so observers can decide whether
     * the signal targets their current call.
     */
    private val _forceEndCall = MutableLiveData<Pair<Int, String>?>()
    val forceEndCall: LiveData<Pair<Int, String>?> get() = _forceEndCall

    fun forceEndCall(callId: Int, reason: String) {
        _forceEndCall.postValue(Pair(callId, reason))
        Log.d("FcmUtils", "forceEndCall: callId=$callId reason=$reason")
    }

    fun clearForceEndCall() {
        _forceEndCall.postValue(null)
    }
}
