package com.gmwapp.hima.utils

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.workers.CallUpdateWorker
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Single point of enqueue for the post-call `individual_update_connected_call`
 * server call. Replaces the per-activity ad-hoc WorkManager builder.
 *
 * Tester report: a male user saw 2–3 identical "Audio session with Ranzi"
 * transactions (same duration, same amount) for what was clearly one call.
 * Pattern reportedly fires after a recharge + call-end with a network blip.
 *
 * Root cause: each calling activity invokes `updateCallEndDetails()` from
 * MANY lifecycle paths — `onUserOffline`, `leaveChannel`, mid-call switch
 * accept/decline handlers, force-end FCM observers. Each call enqueued a
 * fresh `OneTimeWorkRequest` to `CallUpdateWorker` with no de-duplication,
 * and the server's `individual_update_connected_call` endpoint inserts a
 * transaction per hit. With 2-3 lifecycle paths firing in quick succession
 * (e.g., peer hangs up → onUserOffline + leaveChannel both run; a stale
 * FCM lands during teardown), the user got billed 2-3× for one call.
 *
 * Two layers of defence:
 *
 *  1. **Process-wide in-memory Set of callIds** — the first call to
 *     [enqueueIfFresh] for a given callId returns true and the work is
 *     enqueued. Every subsequent call for the same callId is dropped
 *     silently. Catches simultaneous enqueues AND late re-enqueues.
 *
 *  2. **WorkManager unique-work policy KEEP** — even if the Set is wiped
 *     by process death between the first enqueue and a later duplicate,
 *     WorkManager itself rejects the duplicate while the original is
 *     pending (ENQUEUED/RUNNING). After completion the policy no longer
 *     blocks, but at that point the server has already received its single
 *     update — the Set in a fresh process won't know to skip, so a forced
 *     "extra" enqueue would still slip through. The first layer is the
 *     primary; this is just paranoia for the within-process case.
 *
 * Server-side dedup on call_id would be cleaner but is out of scope of an
 * Android-only change; this client-side guard prevents the double/triple
 * billing the tester reported.
 */
object CallEndUpdater {

    private const val TAG = "CallEndUpdater"

    // Thread-safe set — WorkManager enqueue happens from the UI thread but
    // some end paths (onUserOffline, FCM observers) hop threads. Using
    // ConcurrentHashMap-backed set so concurrent .add() is atomic; we rely
    // on add() returning false when the element was already present.
    private val enqueuedCallIds: MutableSet<Int> =
        Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())

    /**
     * Enqueue an `individual_update_connected_call` work for the given call.
     * Returns true if the work was actually enqueued, false if it was
     * de-duped (callId previously seen this process).
     *
     * @param callId the server-side call id whose end is being reported.
     *               Must be > 0; callId == 0 is treated as a no-op (we
     *               can't dedupe a missing id and the worker would just
     *               send 0 to the server anyway).
     */
    fun enqueueIfFresh(
        context: Context,
        userId: Int,
        callId: Int,
        startedTime: String,
        endedTime: String,
        isIndividual: Boolean = true
    ): Boolean {
        if (callId <= 0) {
            Log.w(TAG, "Skipping enqueue: callId=$callId is not valid")
            return false
        }
        // Set.add() returns false if the element was already present —
        // exactly the dedupe semantics we need.
        if (!enqueuedCallIds.add(callId)) {
            Log.d(TAG, "Dedupe hit: callId=$callId already enqueued this process — skipping")
            return false
        }
        Log.d(TAG, "Enqueueing call-end update for callId=$callId duration=$startedTime → $endedTime")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val data = Data.Builder()
            .putInt(DConstants.USER_ID, userId)
            .putInt(DConstants.CALL_ID, callId)
            .putString(DConstants.STARTED_TIME, startedTime)
            .putBoolean(DConstants.IS_INDIVIDUAL, isIndividual)
            .putString(DConstants.ENDED_TIME, endedTime)
            .build()

        val request = OneTimeWorkRequest.Builder(CallUpdateWorker::class.java)
            .setInputData(data)
            .setConstraints(constraints)
            .build()

        // Belt-and-braces unique-work guard — see class doc.
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "call_update_$callId",
            ExistingWorkPolicy.KEEP,
            request
        )
        return true
    }
}
