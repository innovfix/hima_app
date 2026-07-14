package com.gmwapp.hima.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.workers.CallModerationUploadWorker
import com.gmwapp.hima.workers.CallModerationFinalizeWorker
import io.agora.rtc2.RtcEngine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Schedules exactly three possible capture events for one connected video-call
 * leg: 60s, 300s and 600s. It snapshots only uid=0 (this device's local
 * publisher), never the combined UI or the remote tile, so server ownership is
 * tied to the authenticated uploader rather than a fallible face-gender label.
 */
class CallModerationCaptureManager(
    context: Context,
    private val callIdProvider: () -> Int,
    private val engineProvider: () -> RtcEngine?,
) {
    private data class PendingCapture(
        val callId: Int,
        val intervalSeconds: Int,
        val capturedAt: String,
        val consentVersion: String,
    )

    private data class RuntimeConfig(
        val captureEnabled: Boolean,
        val intervals: List<Int>,
        val consentVersion: String,
    )

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val started = AtomicBoolean(false)
    private val pendingByPath = ConcurrentHashMap<String, PendingCapture>()
    private val scheduled = mutableListOf<Runnable>()
    private var initialCallId = 0
    private var connectedAtElapsedMs = 0L
    private var disposed = false

    fun startAfterPeerConnected() {
        if (disposed) return
        if (!started.compareAndSet(false, true)) return
        initialCallId = callIdProvider()
        if (initialCallId <= 0) {
            started.set(false)
            return
        }
        connectedAtElapsedMs = SystemClock.elapsedRealtime()

        executor.execute {
            cleanupExpiredPrivateFiles()
            val config = fetchRuntimeConfig()
            if (config == null || !config.captureEnabled) {
                Log.d(TAG, "Capture disabled or consent missing for call=$initialCallId")
                return@execute
            }
            handler.post { schedule(config) }
        }
    }

    private fun fetchRuntimeConfig(): RuntimeConfig? {
        val token = BaseApplication.getInstance()?.getPrefs()?.getAuthenticationToken().orEmpty()
        if (token.isBlank()) return null

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}call-moderation/config")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            CONFIG_CLIENT.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val root = JSONObject(response.body?.string().orEmpty())
                val data = root.optJSONObject("data") ?: return null
                val intervalJson = data.optJSONArray("capture_intervals_seconds")
                val intervals = buildList {
                    if (intervalJson != null) {
                        for (i in 0 until intervalJson.length()) {
                            val value = intervalJson.optInt(i)
                            if (value in ALLOWED_INTERVALS) add(value)
                        }
                    }
                }.distinct().sorted()
                RuntimeConfig(
                    captureEnabled = data.optBoolean("capture_enabled", false),
                    intervals = intervals,
                    consentVersion = data.optString("consent_version", ""),
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Config unavailable for call=$initialCallId: ${t.message}")
            null
        }
    }

    private fun schedule(config: RuntimeConfig) {
        if (!started.get() || config.consentVersion.isBlank()) return
        config.intervals.forEach { intervalSeconds ->
            val target = connectedAtElapsedMs + TimeUnit.SECONDS.toMillis(intervalSeconds.toLong())
            val delay = (target - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            val task = Runnable { requestLocalSnapshot(intervalSeconds, config.consentVersion) }
            scheduled += task
            handler.postDelayed(task, delay)
        }
        Log.d(TAG, "Scheduled ${config.intervals} for call=$initialCallId")
    }

    private fun requestLocalSnapshot(intervalSeconds: Int, consentVersion: String) {
        if (!started.get()) return
        val currentCallId = callIdProvider()
        if (currentCallId != initialCallId) {
            // Call-switch rows require a fresh timing baseline. Skipping is safer
            // than attaching an image to the previous or next call record.
            Log.w(TAG, "Call id changed $initialCallId->$currentCallId; skipping interval=$intervalSeconds")
            return
        }

        val engine = engineProvider() ?: return
        val directory = File(appContext.cacheDir, "call-moderation/call-$initialCallId")
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w(TAG, "Unable to create private capture directory")
            return
        }
        val output = File(directory, "local-$intervalSeconds-${System.currentTimeMillis()}.jpg")
        val absolutePath = output.absolutePath
        pendingByPath[absolutePath] = PendingCapture(
            callId = initialCallId,
            intervalSeconds = intervalSeconds,
            capturedAt = isoUtcNow(),
            consentVersion = consentVersion,
        )

        val result = engine.takeSnapshot(0, absolutePath)
        if (result != 0) {
            pendingByPath.remove(absolutePath)
            runCatching { output.delete() }
            Log.w(TAG, "Agora rejected local snapshot interval=$intervalSeconds code=$result")
        }
    }

    fun onSnapshotTaken(filePath: String?, width: Int, height: Int, errorCode: Int) {
        if (filePath.isNullOrBlank()) return
        val file = File(filePath)
        val pending = pendingByPath.remove(file.absolutePath) ?: return
        if (errorCode != 0 || width < 64 || height < 64 || !file.isFile || file.length() <= 0L) {
            runCatching { file.delete() }
            Log.w(TAG, "Snapshot failed interval=${pending.intervalSeconds} error=$errorCode ${width}x$height")
            return
        }

        val localUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        val enqueued = CallModerationUploadWorker.enqueue(
            context = appContext,
            callId = pending.callId,
            localUserId = localUserId,
            intervalSeconds = pending.intervalSeconds,
            capturedAt = pending.capturedAt,
            consentVersion = pending.consentVersion,
            imagePath = file.absolutePath,
        )
        if (!enqueued) runCatching { file.delete() }
    }

    fun stopScheduling() {
        if (!started.getAndSet(false)) return
        scheduled.forEach(handler::removeCallbacks)
        scheduled.clear()
        // A snapshot already requested is allowed to finish and enqueue its
        // durable upload; only future interval callbacks are cancelled.
    }

    fun finishCall(expectedCallId: Int) {
        if (initialCallId <= 0 || expectedCallId != initialCallId || connectedAtElapsedMs <= 0L) return
        val durationSeconds = TimeUnit.MILLISECONDS.toSeconds(
            (SystemClock.elapsedRealtime() - connectedAtElapsedMs).coerceAtLeast(0L),
        ).coerceAtMost(86_400L).toInt()
        val localUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        stopScheduling()
        CallModerationFinalizeWorker.enqueue(
            context = appContext,
            callId = initialCallId,
            localUserId = localUserId,
            durationSeconds = durationSeconds,
            endedAt = isoUtcNow(),
        )
    }

    fun dispose() {
        stopScheduling()
        disposed = true
        executor.shutdown()
    }

    private fun isoUtcNow(): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US,
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    private fun cleanupExpiredPrivateFiles() {
        val root = File(appContext.cacheDir, "call-moderation")
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
        root.walkBottomUp().forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) runCatching { file.delete() }
            if (file.isDirectory && file.list()?.isEmpty() == true) runCatching { file.delete() }
        }
    }

    companion object {
        private const val TAG = "CallModerationCapture"
        private val ALLOWED_INTERVALS = setOf(60, 300, 600)
        private val CONFIG_CLIENT = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
