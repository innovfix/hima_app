package com.gmwapp.hima.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Uploads one call's VAD-trimmed local-mic WAV for moderation, then deletes it. */
class CallAudioUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val callId = inputData.getInt(KEY_CALL_ID, 0)
        val capturedUserId = inputData.getInt(KEY_LOCAL_USER_ID, 0)
        val consentVersion = inputData.getString(KEY_CONSENT_VERSION).orEmpty()
        val durationMs = inputData.getLong(KEY_DURATION_MS, 0)
        val speechMs = inputData.getLong(KEY_SPEECH_MS, 0)
        val audio = File(inputData.getString(KEY_AUDIO_PATH).orEmpty())

        if (callId <= 0 || capturedUserId <= 0 || consentVersion.isBlank() ||
            durationMs !in MIN_DURATION_MS..MAX_DURATION_MS || speechMs !in 1..durationMs ||
            !isUsablePrivateAudio(audio)
        ) {
            deletePrivateTemp(audio)
            return@withContext Result.failure()
        }

        if (isExpired(audio) || runAttemptCount >= MAX_ATTEMPTS) {
            deletePrivateTemp(audio)
            Log.w(TAG, "Dropping expired/exhausted audio call=$callId")
            return@withContext Result.failure()
        }

        val prefs = BaseApplication.getInstance()?.getPrefs()
        val currentUserId = prefs?.getUserData()?.id ?: 0
        val token = prefs?.getAuthenticationToken().orEmpty()
        // A delayed upload must never be attributed to whoever logged in afterwards.
        if (currentUserId != capturedUserId || token.isBlank()) {
            deletePrivateTemp(audio)
            return@withContext Result.failure()
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("consent_version", consentVersion)
            .addFormDataPart("duration_ms", durationMs.toString())
            .addFormDataPart("speech_ms", speechMs.toString())
            .addFormDataPart("audio", audio.name, audio.asRequestBody("audio/wav".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}call-audio-moderation/calls/$callId/audio")
            .header("Authorization", "Bearer $token")
            .header("X-Hima-Version-Code", BuildConfig.VERSION_CODE.toString())
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                return@withContext when {
                    response.isSuccessful -> {
                        deletePrivateTemp(audio)
                        Log.d(TAG, "Uploaded call=$callId")
                        Result.success()
                    }
                    // 404 pipeline off, 409 not sampled / consent moved on, 403 no consent,
                    // 426 app too old, 422 bad payload — all permanent. Retrying cannot fix
                    // them and would keep a recording on disk for no reason.
                    response.code in PERMANENT_CODES -> {
                        deletePrivateTemp(audio)
                        Log.d(TAG, "Dropping call=$callId — server declined (${response.code})")
                        Result.failure()
                    }
                    response.code == 408 || response.code == 425 ||
                        response.code == 429 || response.code >= 500 -> Result.retry()
                    else -> {
                        deletePrivateTemp(audio)
                        Log.w(TAG, "Permanent HTTP ${response.code} call=$callId")
                        Result.failure()
                    }
                }
            }
        } catch (exception: IOException) {
            Log.w(TAG, "Upload network failure call=$callId: ${exception.message}")
            Result.retry()
        } catch (throwable: Throwable) {
            deletePrivateTemp(audio)
            Log.e(TAG, "Unexpected upload failure call=$callId", throwable)
            Result.failure()
        }
    }

    private fun isUsablePrivateAudio(file: File): Boolean =
        file.isFile && file.length() in MIN_WAV_BYTES..MAX_WAV_BYTES &&
            runCatching {
                file.canonicalPath.startsWith(
                    File(applicationContext.cacheDir, CACHE_DIRECTORY).canonicalPath + File.separator,
                )
            }.getOrDefault(false)

    private fun isExpired(file: File): Boolean =
        file.lastModified() <= System.currentTimeMillis() - MAX_LOCAL_AGE_MS

    private fun deletePrivateTemp(file: File) {
        runCatching { if (file.exists()) file.delete() }
    }

    companion object {
        private const val TAG = "CallAudioUpload"
        const val CACHE_DIRECTORY = "call-audio-moderation"

        /** Lets logout cancel every pending audio upload in one call, like the image worker. */
        const val WORK_TAG = "call_audio_moderation_work"

        /** Backstop: no recording of a user's call outlives this, uploaded or not. */
        const val MAX_LOCAL_AGE_MS = 6L * 60L * 60L * 1000L

        private const val MAX_ATTEMPTS = 6
        private const val MIN_DURATION_MS = 1_000L
        private const val MAX_DURATION_MS = 36_000_000L
        private const val MIN_WAV_BYTES = 45L
        // Must stay <= the server's max_upload_bytes (12 MB) or every upload 422s.
        private const val MAX_WAV_BYTES = 12_582_912L
        private val PERMANENT_CODES = setOf(403, 404, 409, 422, 426)

        private const val KEY_CALL_ID = "call_id"
        private const val KEY_LOCAL_USER_ID = "local_user_id"
        private const val KEY_CONSENT_VERSION = "consent_version"
        private const val KEY_DURATION_MS = "duration_ms"
        private const val KEY_SPEECH_MS = "speech_ms"
        private const val KEY_AUDIO_PATH = "audio_path"

        fun enqueue(
            context: Context,
            callId: Int,
            localUserId: Int,
            consentVersion: String,
            durationMs: Long,
            speechMs: Long,
            audioPath: String,
        ): Boolean {
            if (callId <= 0 || localUserId <= 0 || consentVersion.isBlank() ||
                durationMs < MIN_DURATION_MS || speechMs <= 0 || audioPath.isBlank()
            ) return false

            val data = Data.Builder()
                .putInt(KEY_CALL_ID, callId)
                .putInt(KEY_LOCAL_USER_ID, localUserId)
                .putString(KEY_CONSENT_VERSION, consentVersion)
                .putLong(KEY_DURATION_MS, durationMs)
                .putLong(KEY_SPEECH_MS, speechMs)
                .putString(KEY_AUDIO_PATH, audioPath)
                .build()

            val request = OneTimeWorkRequestBuilder<CallAudioUploadWorker>()
                .setInputData(data)
                .addTag(WORK_TAG)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            // KEEP + a per-participant name: the server is idempotent on
            // (call_id, subject_user_id), and this stops a duplicate ever leaving the device.
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "call_audio_upload_${callId}_$localUserId",
                ExistingWorkPolicy.KEEP,
                request,
            )
            return true
        }

        /**
         * Sweeps anything left by a crash or a force-close before the finaliser ran. Called
         * on the next call's prepare(), so a stranded recording never sits in cache longer
         * than the retention promise.
         */
        fun deleteExpired(context: Context) {
            runCatching {
                val dir = File(context.cacheDir, CACHE_DIRECTORY)
                if (!dir.isDirectory) return
                val cutoff = System.currentTimeMillis() - MAX_LOCAL_AGE_MS
                dir.listFiles()?.forEach { if (it.isFile && it.lastModified() <= cutoff) it.delete() }
            }
        }
    }
}
