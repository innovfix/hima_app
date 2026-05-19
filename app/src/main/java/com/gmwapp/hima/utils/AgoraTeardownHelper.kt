package com.gmwapp.hima.utils

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.agora.rtc2.RtcEngine

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

    private inline fun safe(logTag: String, op: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.e(TAG, "$logTag: $op threw", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
