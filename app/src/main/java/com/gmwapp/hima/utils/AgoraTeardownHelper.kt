package com.gmwapp.hima.utils

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig

/**
 * B143 / B176 fix.
 *
 * Why: the previous teardown left the mic and camera held by Agora for 2–3 minutes after a call
 * ended because `RtcEngine.destroy()` was queued on a fire-and-forget background thread and the
 * activity finished before that thread ran. There were also no explicit `disableAudio()` /
 * `disableVideo()` calls, so even a successful destroy could leave the hardware capture state
 * dirty on some devices.
 *
 * This helper performs a deterministic shutdown: disable both tracks, stop the camera preview,
 * leave the channel, then destroy the engine on a worker thread but block (with a hard timeout)
 * until that thread completes. Anything that throws is swallowed and reported — teardown must
 * never crash the host activity.
 */
object AgoraTeardownHelper {

    private const val TAG = "AgoraTeardown"

    /** Block on `RtcEngine.destroy()` for at most this long. Agora's docs say it normally
     *  returns within a few hundred ms; we cap so a frozen native call cannot hang `onDestroy`. */
    private const val DESTROY_TIMEOUT_MS = 1500L

    /** When `destroy()` exceeds [DESTROY_TIMEOUT_MS] it keeps running on its worker thread after
     *  `releaseEngineSync` returns. `RtcEngine` is a PROCESS-WIDE SINGLETON and Agora forbids
     *  `create()` overlapping a `destroy()` — overlap leaves the next call's engine black. So the
     *  NEXT call's create must wait for that straggler to finish. Bounded so the create thread
     *  (often the main thread) can never hang long enough to ANR. */
    private const val CREATE_WAIT_FOR_DESTROY_MS = 2000L

    /** The most recent (possibly still-running) `RtcEngine.destroy()` worker, so the next
     *  `createEngineSafely` can join it before creating a fresh engine. */
    @Volatile
    private var pendingDestroy: Thread? = null

    /**
     * Fully release the engine. Safe to call multiple times; safe to call from `onDestroy`.
     *
     * @param engine the active engine, or null if it was never created / already released
     * @param logTag activity name for log lines
     * @param hasVideo true for video activities — controls whether we touch the video stack
     * @return null, so callers can assign it to their `agoraEngine` field
     */
    fun releaseEngineSync(engine: RtcEngine?, logTag: String, hasVideo: Boolean): RtcEngine? {
        if (engine == null) return null

        if (hasVideo) {
            safe(logTag, "muteLocalVideoStream") { engine.muteLocalVideoStream(true) }
            safe(logTag, "stopPreview") { engine.stopPreview() }
            safe(logTag, "disableVideo") { engine.disableVideo() }
        }
        safe(logTag, "muteLocalAudioStream") { engine.muteLocalAudioStream(true) }
        safe(logTag, "disableAudio") { engine.disableAudio() }
        safe(logTag, "leaveChannel") { engine.leaveChannel() }

        val destroyThread = Thread({
            try {
                RtcEngine.destroy()
            } catch (e: Throwable) {
                Log.e(TAG, "$logTag: RtcEngine.destroy threw", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }, "AgoraDestroy-$logTag")
        // Publish BEFORE start so the next call's createEngineSafely can join it even if
        // destroy() outlives the cap below.
        pendingDestroy = destroyThread
        destroyThread.start()
        try {
            destroyThread.join(DESTROY_TIMEOUT_MS)
            if (destroyThread.isAlive) {
                Log.w(TAG, "$logTag: RtcEngine.destroy exceeded ${DESTROY_TIMEOUT_MS}ms, continuing")
            }
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return null
    }

    /**
     * Create a fresh [RtcEngine], but FIRST wait for any in-flight `destroy()` from the previous
     * call to finish. Fixes the "black screen after several video calls" race: under load a
     * straggling `destroy()` could overlap the next `create()` and corrupt the (singleton) engine.
     *
     * Normally a no-op — by the time the next call is answered the previous destroy has long since
     * finished (`pendingDestroy` is dead → join returns instantly). The wait only materialises in
     * the exact overlap case, and is bounded by [CREATE_WAIT_FOR_DESTROY_MS] so the calling thread
     * cannot ANR.
     */
    fun createEngineSafely(config: RtcEngineConfig, logTag: String): RtcEngine {
        val prev = pendingDestroy
        if (prev != null && prev.isAlive) {
            Log.w(TAG, "$logTag: waiting up to ${CREATE_WAIT_FOR_DESTROY_MS}ms for previous RtcEngine.destroy before create")
            try {
                prev.join(CREATE_WAIT_FOR_DESTROY_MS)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (prev.isAlive) {
                Log.w(TAG, "$logTag: previous destroy still running after wait; creating anyway")
            }
        }
        pendingDestroy = null
        return RtcEngine.create(config)
    }

    private inline fun safe(logTag: String, op: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.e(TAG, "$logTag: $op threw", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
