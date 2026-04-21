package com.gmwapp.hima.utils

import com.gmwapp.hima.retrofit.responses.UserData

/**
 * When `userdetails` JSON omits `dnd_enabled` / `dnd_until`, Gson leaves them null.
 * Preserve previous prefs so client-side DND (e.g. [DndController] 404 fallback) survives a profile refresh.
 */
object UserDataDndMerge {
    fun mergePreserveDnd(prev: UserData?, fresh: UserData): UserData {
        return fresh.copy(
            dnd_enabled = fresh.dnd_enabled ?: prev?.dnd_enabled,
            dnd_until = fresh.dnd_until ?: prev?.dnd_until
        )
    }
}
