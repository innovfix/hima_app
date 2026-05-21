package com.gmwapp.hima.utils

import com.gmwapp.hima.retrofit.responses.UserData

/**
 * B075/DND — preserve client-controlled fields when refreshing UserData from the server.
 *
 * Several screens (in-call ludo refresh, profile open, IPL room flows, payment / wallet
 * refresh, MainActivity bootstrap, ...) re-fetch the user via getUsers() purely to read
 * one or two fields, then overwrite the entire cached UserData with the response. That
 * silently clobbers fields the user actually controls from the app — DND status and the
 * audio / video call-availability toggles — whenever the server's view of those fields
 * disagrees with the user's last intent.
 *
 * The two legitimate writers of audio_status / video_status (the updateCallStatus
 * response observers in FemaleHomeFragment and ChatActivityInHouse) call setUserData
 * directly with the server response and bypass this merge by design.
 */
object UserDataLocalIntentMerge {
    fun mergePreserveLocalIntent(prev: UserData?, fresh: UserData): UserData {
        if (prev == null) return fresh
        return fresh.copy(
            dnd_enabled = fresh.dnd_enabled ?: prev.dnd_enabled,
            dnd_until = fresh.dnd_until ?: prev.dnd_until,
            audio_status = prev.audio_status ?: fresh.audio_status,
            video_status = prev.video_status ?: fresh.video_status
        )
    }
}
