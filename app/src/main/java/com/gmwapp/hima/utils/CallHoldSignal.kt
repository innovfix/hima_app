package com.gmwapp.hima.utils

import android.util.Log
import io.agora.rtc2.DataStreamConfig
import io.agora.rtc2.RtcEngine

/**
 * In-call "on hold" signaling over an Agora reliable data stream.
 *
 * When one party puts the Hima call on hold to take a cellular / VoIP call
 * (detected by [CallPhoneStateHelper] / [CallAudioFocusHelper]) we already mute
 * the local mic — but the peer only sees a generic "muted" pill, which is
 * indistinguishable from a normal self-mute. This relays an explicit HOLD /
 * UNHOLD marker to the peer so the other side can show a dedicated
 * "‹Name› is on hold" banner.
 *
 * Why an Agora data stream (not FCM / socket): it's in-channel, instant, needs
 * no backend, and keeps working while the holder's app is backgrounded during
 * the external call (the RTC channel stays joined). Mirrors the data-stream
 * pattern already used by IplRoomCallActivity.
 *
 * Indication only — billing / call timer are deliberately untouched.
 */
class CallHoldSignal(private val engineProvider: () -> RtcEngine?) {

    private var dataStreamId: Int = -1
    // The last hold state we actually transmitted. Dedups so we only send on a
    // real change — prevents a spurious UNHOLD (e.g. an audio-focus regain with
    // no prior hold) from racing in and clearing a peer banner that a genuine
    // HOLD just put up.
    private var sentHold = false

    /** Call from onJoinChannelSuccess to (re)create the reliable data stream. */
    fun onChannelJoined() {
        try {
            val cfg = DataStreamConfig().apply {
                syncWithAudio = false
                ordered = true
            }
            val id = engineProvider()?.createDataStream(cfg) ?: -1
            if (id >= 0) {
                dataStreamId = id
                sentHold = false // fresh stream — peer state unknown, allow next send
            }
            Log.d(TAG, "data stream id=$dataStreamId")
        } catch (e: Exception) {
            Log.e(TAG, "createDataStream failed: ${e.message}")
        }
    }

    /** Broadcast our hold state to the peer. No-op if the stream isn't ready. */
    fun sendHold(onHold: Boolean) {
        val engine = engineProvider() ?: return
        if (dataStreamId < 0) return
        if (onHold == sentHold) return // no state change — don't spam / don't race
        val msg = if (onHold) MSG_HOLD else MSG_UNHOLD
        try {
            engine.sendStreamMessage(dataStreamId, msg.toByteArray(Charsets.UTF_8))
            sentHold = onHold
        } catch (e: Exception) {
            Log.e(TAG, "sendStreamMessage failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CallHoldSignal"
        private const val MSG_HOLD = "HOLD"
        private const val MSG_UNHOLD = "UNHOLD"

        /**
         * Parse an incoming data-stream payload.
         *
         * @return true if the peer is now on hold, false if they resumed, or
         *         null if it isn't a hold-related message (caller should ignore).
         */
        fun parse(data: ByteArray?): Boolean? {
            if (data == null) return null
            return when (String(data, Charsets.UTF_8).trim()) {
                MSG_HOLD -> true
                MSG_UNHOLD -> false
                else -> null
            }
        }
    }
}
