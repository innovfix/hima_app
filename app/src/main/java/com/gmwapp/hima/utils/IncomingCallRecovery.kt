package com.gmwapp.hima.utils

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.activities.IplRoomCallActivity
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.agora.female.FemaleCallAcceptActivity
import com.gmwapp.hima.agora.female.FemaleAudioCallingActivity
import com.gmwapp.hima.agora.female.FemaleCallConnectingActivity
import com.gmwapp.hima.agora.female.FemaleVideoCallingActivity
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * TC-INC-001 — recover a DROPPED incoming-call ring.
 *
 * The creator's ring banner is driven by a single best-effort FCM push. When
 * that push never lands (OneSignal fallback that doesn't ring, stale/"0" token,
 * Doze deferral while backgrounded), she has no call_id and no way to learn she
 * was rung — the call is silently missed.
 *
 * This pulls authoritative server state keyed on HER OWN user_id
 * (POST pending_incoming_call) and, if the backend reports a call ringing right
 * now, surfaces the same FemaleCallAcceptActivity banner the FCM path would
 * have. Triggered on app-foreground (FemaleHomeFragment.onResume) — exactly when
 * a Doze-suppressed push is most likely to have been lost.
 *
 * Cheap and event-driven: one indexed backend lookup per foreground, NOT a
 * steady poll (the DB box is overloaded — a per-online-creator interval poll
 * would be a real load risk). Fire-and-forget on a background thread; all
 * surfacing happens back on the main thread behind the same busy/duplicate
 * guards the FCM path uses, so it can never double-ring.
 */
object IncomingCallRecovery {

    private const val TAG = "IncomingCallRecovery"

    fun check() {
        val app = BaseApplication.getInstance() ?: return
        val prefs = app.getPrefs()
        val userData = prefs?.getUserData() ?: return
        // Recipients only. Males receive calls too, but TC-INC-001 is the
        // creator-side report; keep the surface minimal and female-scoped.
        if (userData.gender != "female") return
        val userId = userData.id ?: return
        if (userId <= 0) return
        // Endpoint is auth:api — the recipient is derived from the token, so we
        // must send it. No token → can't authenticate → nothing to recover.
        val authToken = prefs.getAuthenticationToken().orEmpty()
        if (authToken.isBlank()) return

        // A ring is already on screen / fresh — the push DID land. Don't poll.
        if (app.isIncomingCallFresh()) {
            Log.d(TAG, "skip: an incoming call is already fresh")
            return
        }
        // Already busy in a call — surfacing a recovered ring would collide.
        // isUserAvailable mirrors the FCM path's B156a guard: it is set the
        // instant she taps to place her OWN outgoing call, before the connecting
        // activity exists, so isInActiveCall() alone would miss that race window.
        if (app.isInActiveCall() || FcmUtils.isUserAvailable == 1) {
            Log.d(TAG, "skip: already in / starting a call")
            return
        }

        Thread({
            try {
                val url = BuildConfig.BASE_URL + "pending_incoming_call"
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .writeTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(4, TimeUnit.SECONDS)
                    .build()
                // Body unused by the backend (it reads auth()->id()); send an
                // empty form so this is a well-formed POST.
                val body = okhttp3.FormBody.Builder().build()
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $authToken")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string() ?: return@Thread
                    Log.d(TAG, "userId=$userId resp=$raw")
                    val json = JSONObject(raw)
                    if (!json.optBoolean("pending", false)) return@Thread

                    val callId = json.optInt("call_id", 0)
                    val callerId = json.optInt("caller_id", 0)
                    val callType = json.optString("call_type", "")
                    val channelName = json.optString("channel_name", "")
                    val callerName = json.optString("caller_name", "")
                    val callerImage = json.optString("caller_image", "")

                    if (callId <= 0 || callerId <= 0) return@Thread
                    if (callType != "audio" && callType != "video") return@Thread
                    if (channelName.isBlank()) return@Thread

                    Handler(Looper.getMainLooper()).post {
                        surface(callId, callerId, callType, channelName, callerName, callerImage)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "check failed (silently — recovery is best-effort): ${t.message}")
            }
        }, "IncomingCallRecovery-$userId").start()
    }

    private fun surface(
        callId: Int,
        callerId: Int,
        callType: String,
        channelName: String,
        callerName: String,
        callerImage: String,
    ) {
        val app = BaseApplication.getInstance() ?: return
        // Only surface while the app is genuinely foregrounded. The trigger is
        // FemaleHomeFragment.onResume so this normally holds, but the 4s network
        // round-trip can outlive the foreground — and launching FemaleCallAccept
        // from this non-Activity (Application) context while backgrounded would
        // be silently blocked by Android's background-activity-start (BAL) limit,
        // leaving a "recovered" call that never actually shows. Skip instead.
        if (!app.isAppInForeground()) {
            Log.d(TAG, "surface skip: app no longer foreground (BAL would block launch)")
            return
        }
        // Re-check the guards on the main thread — state may have moved while the
        // network call was in flight (the real FCM may have just landed, she may
        // have started a call, or this exact call may have just ended).
        if (app.isIncomingCallFresh()) {
            Log.d(TAG, "surface skip: incoming call became fresh during fetch")
            return
        }
        if (app.isInActiveCall() || FcmUtils.isUserAvailable == 1) {
            Log.d(TAG, "surface skip: entered / started a call during fetch")
            return
        }
        if (app.wasCallRecentlyEnded(callId) || app.wasCallBusyRejected(callId)) {
            Log.d(TAG, "surface skip: call_id=$callId recently ended / busy-rejected")
            return
        }
        val current = app.getCurrentActivity()
        if (current is FemaleCallAcceptActivity ||
            current is FemaleAudioCallingActivity ||
            current is FemaleVideoCallingActivity ||
            current is FemaleCallConnectingActivity ||
            current is IplRoomCallActivity
        ) {
            Log.d(TAG, "surface skip: a call activity is already on screen")
            return
        }

        Log.d(TAG, "surfacing recovered incoming call_id=$callId from caller=$callerId type=$callType")
        // Mirror the FCM female branch's ring setup so the accept screen behaves
        // identically to a normally-delivered call.
        app.saveSenderId(callerId)
        app.requestRingtoneFocus()
        app.setIncomingCall(callerId, callType, channelName, callId)
        app.setIncomingCallerInfo(callerName, callerImage)

        val intent = Intent(app, FemaleCallAcceptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", callerId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("Caller_NAME", callerName)
            putExtra("Caller_Image", callerImage)
            putExtra("CALL_ID", callId)
        }
        try {
            app.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startActivity FemaleCallAccept threw: ${e.message}")
        }
    }
}
