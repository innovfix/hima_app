package com.gmwapp.hima.utils

/**
 * CHAT-090: shared helpers for the audio-typed reply snippet so the compose-bar
 * preview ([com.gmwapp.hima.activities.ChatActivityInHouse.updateReplyPreviewUi])
 * and the in-bubble reply quote (ChatAdapter.TextMessageViewHolder.bind) agree
 * on detection and formatting.
 *
 * The "🎤 " prefix doubles as (a) the persisted, cross-client visual marker so
 * older clients without the mic-drawable swap still render something
 * meaningful, and (b) the detection token both renderers strip before applying
 * a real `R.drawable.ic_mic` compound drawable.
 */
const val AUDIO_REPLY_SNIPPET_PREFIX = "🎤 "

/** Formats `audioDurationMs` as M:SS. Returns `""` for non-positive durations. */
fun formatAudioReplyDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
