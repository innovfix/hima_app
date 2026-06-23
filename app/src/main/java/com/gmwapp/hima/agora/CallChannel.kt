package com.gmwapp.hima.agora

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Single source of truth for whether an incoming-call push carries a USABLE
 * Agora channel.
 *
 * The caller always joins a UNIQUE channel — "<callerId>_<timestamp>", see
 * [com.gmwapp.hima.agora.male.MaleCallConnectingActivity.generateUniqueChannelName].
 * That channel name reaches the recipient only through the FCM "channel relay"
 * (send-fcm-notification). The OneSignal call_request ring — and any malformed
 * FCM — carries NO channelName, so the receive paths
 * ([com.gmwapp.hima.agora.MyFirebaseMessagingService] and
 * the OneSignal notification extension) historically substituted the literal
 * [DEFAULT_SENTINEL] just to keep the payload non-empty.
 *
 * Answering such a call joined that shared "default_channel" room, so EVERY
 * channel-less answerer landed in the same room together — black screen plus the
 * crossed-over audio of unrelated callers — while the real caller sat alone in
 * his unique channel, stuck on "connecting". The shared room can never connect
 * the intended pair (the caller is never in it), so joining it is always wrong.
 *
 * A call is therefore joinable ONLY when the channel is non-blank AND is not the
 * default_channel sentinel. The Kotlin contract lets call sites smart-cast the
 * nullable channel to non-null inside the `isJoinable(...)` guard.
 */
object CallChannel {
    const val DEFAULT_SENTINEL = "default_channel"

    @OptIn(ExperimentalContracts::class)
    fun isJoinable(channel: String?): Boolean {
        contract { returns(true) implies (channel != null) }
        return !channel.isNullOrBlank() && channel != DEFAULT_SENTINEL
    }
}
