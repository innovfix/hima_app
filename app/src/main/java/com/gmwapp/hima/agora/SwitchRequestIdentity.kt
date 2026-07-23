package com.gmwapp.hima.agora

import java.util.UUID

/**
 * Keeps one immutable server root and one idempotency key per pending
 * audio/video switch intent.
 *
 * Retries for the same target reuse the key. A different target starts a new
 * intent. The helper is payload-only: it does not create calls, send network
 * requests, change UI state, or affect billing.
 */
class SwitchRequestIdentity(
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    data class Contract(
        val rootCallId: Int,
        val requestId: String,
        val targetType: String
    )

    private var rootCallId: Int? = null
    private var pendingTargetType: String? = null
    private var pendingRequestId: String? = null

    @Synchronized
    fun captureRootCallId(callId: Int) {
        if (rootCallId == null && callId > 0) {
            rootCallId = callId
        }
    }

    @Synchronized
    fun begin(targetType: String): Contract? {
        val root = rootCallId ?: return null
        val normalizedTarget = targetType.trim().lowercase()
        if (normalizedTarget != "audio" && normalizedTarget != "video") return null

        if (pendingRequestId == null || pendingTargetType != normalizedTarget) {
            pendingTargetType = normalizedTarget
            pendingRequestId = idFactory()
        }

        return Contract(
            rootCallId = root,
            requestId = pendingRequestId ?: return null,
            targetType = normalizedTarget
        )
    }

    @Synchronized
    fun complete(targetType: String? = null) {
        val normalizedTarget = targetType?.trim()?.lowercase()
        if (normalizedTarget == null || normalizedTarget == pendingTargetType) {
            pendingTargetType = null
            pendingRequestId = null
        }
    }
}
