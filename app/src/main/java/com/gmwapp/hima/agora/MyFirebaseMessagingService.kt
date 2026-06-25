package com.gmwapp.hima.agora

import android.Manifest
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.BankUpdateActivity
import com.gmwapp.hima.activities.EarningsActivity
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.activities.NewLoginActivity
import com.gmwapp.hima.agora.female.FemaleAudioCallingActivity
import com.gmwapp.hima.agora.female.FemaleCallAcceptActivity
import com.gmwapp.hima.agora.female.FemaleCallConnectingActivity
import com.gmwapp.hima.agora.female.FemaleVideoCallingActivity
import com.gmwapp.hima.agora.male.MaleAudioCallingActivity
import com.gmwapp.hima.agora.male.MaleCallAcceptActivity
import com.gmwapp.hima.agora.male.MaleCallConnectingActivity
import com.gmwapp.hima.agora.male.MaleVideoCallingActivity
import com.gmwapp.hima.agora.telecom.HimaConnection
import com.gmwapp.hima.agora.telecom.HimaTelecomManager
import com.gmwapp.hima.repositories.FcmNotificationRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FcmNotificationResponse
import com.gmwapp.hima.utils.MaleNotificationFcmGate
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.onesignal.OneSignal
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var fcmNotificationRepository: FcmNotificationRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCMNewToken", "New token: $token")
        Log.d("CreatorCallDiag", "FCM.onNewToken tokenPrefix=${token.take(12)}…")

        // FirebaseMessagingService is not a Hilt entry point, so the ViewModel/repository
        // graph isn't available here. Hand off to WorkManager so the registration survives
        // process death and retries transient failures.
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userId = prefs?.getUserData()?.id ?: 0
        val authToken = prefs?.getAuthenticationToken().orEmpty()
        if (userId <= 0 || authToken.isBlank()) {
            Log.d("FCMNewToken", "No signed-in user — skipping register (uid=$userId)")
            return
        }

        val input = androidx.work.Data.Builder()
            .putInt(com.gmwapp.hima.workers.FcmTokenRegisterWorker.KEY_USER_ID, userId)
            .putString(com.gmwapp.hima.workers.FcmTokenRegisterWorker.KEY_TOKEN, token)
            .putString(com.gmwapp.hima.workers.FcmTokenRegisterWorker.KEY_AUTH_TOKEN, authToken)
            .build()

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val request = androidx.work.OneTimeWorkRequestBuilder<
            com.gmwapp.hima.workers.FcmTokenRegisterWorker
        >()
            .setInputData(input)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                java.util.concurrent.TimeUnit.SECONDS
            )
            .build()

        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "${com.gmwapp.hima.workers.FcmTokenRegisterWorker.WORK_NAME_PREFIX}$userId",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
        Log.d("FCMNewToken", "Enqueued FcmTokenRegisterWorker for user=$userId")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        var gender = userData?.gender
        Log.d("FCM", "From: ${remoteMessage.from}")
        if (com.gmwapp.hima.BuildConfig.DEBUG) {
            // Full data payload — debug only. Release smoke verifies no payloads in logcat.
            Log.d("FCM_Data_Complete", "From: ${remoteMessage.data}")
            Log.d("FCM_Message", "Message data payload: ${remoteMessage.data["message"]}")
        }
        // Single-line catch-all so `adb logcat -s CreatorCallDiag` can confirm
        // whether any FCM at all is reaching this device during a call test.
        // Type/channel/callId are operational metadata; message body is omitted to
        // keep release logcat free of chat content.
        Log.d(
            "CreatorCallDiag",
            "FCM.rx priority=${remoteMessage.priority} from=${remoteMessage.from} " +
                "userId=${userData?.id} gender=$gender type=${remoteMessage.data["type"]} " +
                "channel=${remoteMessage.data["channelName"]} " +
                "callId=${remoteMessage.data["call_id"]}"
        )

        // DND check: drop ALL incoming notifications when DND is active and not yet expired
        if (BaseApplication.isDndActiveStatic(userData)) {
            Log.d("FCM", "DND is active, dropping notification.")
            Log.d("CreatorCallDiag", "FCM.rx.dndDropped userId=${userData?.id}")
            return
        }

        if (remoteMessage.getPriority() == RemoteMessage.PRIORITY_HIGH) {
            Log.d("FCM_Message", "🔥 High-priority notification received!");
        } else {
            Log.d("FCM_Message", "⚠️ Low-priority notification received!");
        }


        if (remoteMessage.data.isNotEmpty()) {
            val type = remoteMessage.data["type"] ?: ""
            val message = remoteMessage.data["message"] ?: ""
            // 2026-06-05 v1108 FIX: backend sends "caller_id" + "type" (not
            // "senderId" + "callType"). Without the fallbacks, senderId=-1 and
            // callType=null, so the FCM call path silently drops and the
            // recipient only sees a default missed-call notification with NO
            // ring UI. Same bug as the OneSignal extension; both paths need
            // both field-name aliases to survive backend payload changes.
            val callType = remoteMessage.data["callType"]
                ?: remoteMessage.data["call_type"]
                ?: remoteMessage.data["type"]?.takeIf { it == "audio" || it == "video" }
            val senderId = remoteMessage.data["senderId"]?.toIntOrNull()
                ?: remoteMessage.data["sender_id"]?.toIntOrNull()
                ?: remoteMessage.data["caller_id"]?.toIntOrNull()
                ?: remoteMessage.data["user_id"]?.toIntOrNull()
                ?: -1
            val channelName = remoteMessage.data["channelName"]
                ?: remoteMessage.data["channel_name"]
                ?: "default_channel"
            val fcmCurrentActivity = BaseApplication.getInstance()?.getCurrentActivity()
            if (com.gmwapp.hima.BuildConfig.DEBUG) {
                Log.d(
                    "MaleVideoEndFlow",
                    "FCM rx type=$type message=$message senderId=$senderId callType=$callType gender=$gender currentActivity=${fcmCurrentActivity?.javaClass?.simpleName}"
                )
            } else {
                Log.d(
                    "MaleVideoEndFlow",
                    "FCM rx type=$type senderId=$senderId callType=$callType gender=$gender currentActivity=${fcmCurrentActivity?.javaClass?.simpleName}"
                )
            }

            // Admin/server forced logout/clear session.
            if (type == "clear_data" || message == "clear_data") {
                handleClearDataFcm()
                return
            }

            if (type == "ludo_invite" ||
                type == "ludo_invite_accepted" ||
                type == "ludo_invite_rejected" ||
                type == "ludo_invite_expired" ||
                type == "game_end" ||
                message == "game_end"
            ) {
                // App-side kill-switch: drop Ludo pushes entirely when the
                // feature is disabled so no background work runs and no stale
                // FcmUtils.ludoEvent updates can surface the invite dialog.
                if (!com.gmwapp.hima.utils.FeatureFlags.LUDO_ENABLED) {
                    Log.d("FCM", "Ludo feature disabled — dropping $type / $message")
                    return
                }
                val ludoInviteId = remoteMessage.data["invite_id"]
                val roomCode = remoteMessage.data["room_code"]
                val fromUserId = remoteMessage.data["from_user_id"]?.toIntOrNull()
                    ?: remoteMessage.data["by_user_id"]?.toIntOrNull()
                val fromUserName = remoteMessage.data["from_user_name"]
                val joinUrl = remoteMessage.data["join_url"]

                FcmUtils.updateLudoEvent(
                    FcmUtils.LudoEvent(
                        type = if (message == "game_end") "game_end" else type,
                        inviteId = ludoInviteId,
                        roomCode = roomCode,
                        fromUserId = fromUserId,
                        fromUserName = fromUserName,
                        joinUrl = joinUrl
                    )
                )
                return
            }

            if (MaleNotificationFcmGate.shouldDropDataPayload(
                    gender,
                    remoteMessage.data,
                    BaseApplication.getInstance()?.getPrefs()
                )
            ) {
                Log.d("FCM", "Dropped by male Manage Notifications prefs (type=${remoteMessage.data["type"]})")
                return
            }

            val currentActivity = BaseApplication.getInstance()?.getCurrentActivity()

            if (message.startsWith("incoming call")) {
                val parts = message.split(" ")
                // B3/TC_005: callId is the only field required to show the
                // incoming-call banner. This was gated on `parts.size >= 5` with
                // receiverName = parts[4], so a creator name containing a space
                // ("Riya Sharma") was truncated to its first word, and a blank or
                // missing name/avatar could drop parts below 5 and suppress the
                // WHOLE banner (the "no banner" in TC_005). Now: require only the
                // callId; the avatar is the single token at [3]; the NAME is
                // EVERYTHING after it, so spaces are preserved.
                val callId = parts.getOrNull(2)
                if (!callId.isNullOrBlank()) {
                    // Suppress a retried/duplicate push for a call already ended or
                    // busy-rejected. Without this, a call auto-rejected while the creator
                    // was busy can resurface as a ghost incoming screen after her current
                    // call ends (the late/retried FCM gets processed once she is free).
                    val callIdInt = callId.toIntOrNull() ?: 0
                    val appGuard = BaseApplication.getInstance()
                    if (callIdInt > 0 &&
                        (appGuard?.wasCallRecentlyEnded(callIdInt) == true ||
                            appGuard?.wasCallBusyRejected(callIdInt) == true)) {
                        Log.d("FCM", "Incoming call_id=$callIdInt recently ended or busy-rejected — ignoring stale/retried push")
                        return
                    }
                    val receiverImg = parts.getOrNull(3).orEmpty()           // avatar URL (single token)
                    val receiverName = parts.drop(4).joinToString(" ").trim() // name (may contain spaces)

                    Log.d("startingActvity","$gender")


                    // 🕒 CHECK CALL TIME DIFFERENCE
                    val fcmTimestamp = remoteMessage.data["timestamp"]
                    val currentTime = System.currentTimeMillis()
                    val callTimestamp = try {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        sdf.timeZone = java.util.TimeZone.getDefault()
                        sdf.parse(fcmTimestamp)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                    val timeDiffSeconds = (currentTime - callTimestamp) / 1000
                    Log.w("FCM_Time", "⚠️ TimeDiffercne = ($timeDiffSeconds s late)")

                    if (timeDiffSeconds > 20) {
                        Log.w("FCM_Time", "⚠️ Ignoring old call notification ($timeDiffSeconds s late)")
                        return
                    }

                    // SINGLE-CALL GUARD: we already have a fresh pending incoming call (ringing
                    // or awaiting accept). A different caller arriving now would replace the
                    // notification and cause two rings, so auto-reject them as busy. Same-sender
                    // duplicate FCMs are also ignored to avoid double ringtone/notification.
                    val appForBusy = BaseApplication.getInstance()
                    if (appForBusy?.isIncomingCallFresh() == true) {
                        val pendingSenderId = appForBusy.getSenderIdForSplashActivity()
                        if (pendingSenderId != senderId) {
                            Log.d(
                                "FCM",
                                "Busy: already ringing from $pendingSenderId, auto-rejecting new incoming from $senderId"
                            )
                            sendAutoRejectNotification(
                                userData?.id,
                                senderId,
                                callType,
                                channelName
                            )
                            // Remember this busy-rejected call so a late/retried push for it
                            // doesn't resurface as a ghost incoming screen after the current
                            // call ends — even if that call lasts 20+ minutes.
                            if (callIdInt > 0) BaseApplication.getInstance()?.markCallBusyRejected(callIdInt)
                        } else {
                            Log.d(
                                "FCM",
                                "Duplicate incoming FCM from same sender $senderId — ignoring"
                            )
                        }
                        return
                    }


                    if (gender == "female") {
                        // Belt-and-suspenders: the currentActivity check is the
                        // primary gate but it goes null briefly during activity
                        // transitions / permission dialogs. isInActiveCall() is
                        // explicitly flipped in each CallingActivity.onCreate /
                        // onDestroy so it stays true across those dips.
                        // Capture once so we can differentiate the source of the
                        // busy state below — needed for B204 missed-call posting.
                        val femaleOnAnotherAppCall = isOnAnotherAppCall()

                        // Split the busy check into "definitely busy right now"
                        // (in-call activity is on screen, or another app holds
                        // a telecom call) and "boolean flags say busy" (which
                        // can stick across a crashed/killed call activity that
                        // never ran its onDestroy cleanup). When only the flags
                        // claim busy but no in-call activity is visible, the
                        // flags are stale — reset them and proceed with the
                        // incoming call instead of auto-rejecting forever.
                        val inCallActivityShown =
                            currentActivity is FemaleCallAcceptActivity ||
                            currentActivity is FemaleAudioCallingActivity ||
                            currentActivity is FemaleVideoCallingActivity ||
                            currentActivity is FemaleCallConnectingActivity ||
                            currentActivity is com.gmwapp.hima.activities.IplRoomCallActivity
                        val flagsSayBusy =
                            BaseApplication.getInstance()?.isInActiveCall() == true ||
                            FcmUtils.isUserAvailable == 1

                        if (inCallActivityShown || femaleOnAnotherAppCall) {

                            // B134 — Connecting Activity instance counts as busy.
                            // B156a — FcmUtils.isUserAvailable closes the race window
                            // between the user tapping Call (flag set synchronously
                            // in the fragment click handler, before startActivity)
                            // and the Connecting Activity actually being foregrounded
                            // (currentActivity check otherwise misses that ~50ms gap).
                            // Without this, an incoming FCM in that window launched a
                            // second Accept Activity on top of the outgoing one and
                            // both calls connected with mixed audio.
                            Log.d("FCM", "User is already in a call. Ignoring incoming call notification.")

                            val receiverId = senderId
                            sendAutoRejectNotification(userData?.id, receiverId, callType, channelName)

                            // B204 + TC_014 — whenever we auto-reject an incoming call
                            // because the recipient is busy, leave a local "Missed call
                            // from X" notification so they can call back once free. This
                            // now fires for an in-Hima-call busy state too (previously only
                            // an another-app/SIM call did), which was the TC_014 gap. One
                            // post per FCM; the SINGLE-CALL GUARD above already drops
                            // duplicate same-sender FCMs so this can't double-post.
                            postBusyMissedCall(callType, senderId, receiverName, receiverImg)
                            // Remember this busy-rejected call so a late/retried push for it
                            // doesn't resurface as a ghost incoming screen after the current
                            // call ends — even if that call lasts 20+ minutes (the reported
                            // duplicate-incoming-after-busy bug).
                            if (callIdInt > 0) BaseApplication.getInstance()?.markCallBusyRejected(callIdInt)
                            return
                        }

                        if (flagsSayBusy) {
                            // Flags say busy but no in-call activity is on top and
                            // the system is not in a telecom call. The flags are
                            // stale — most likely from a prior call activity that
                            // was force-stopped / OEM-killed without running its
                            // onDestroy cleanup. Reset and continue so this fresh
                            // incoming call actually rings.
                            Log.w(
                                INCOMING_CALL_LOG_TAG,
                                "Stale busy flags detected on female side " +
                                    "(no in-call activity, no telecom call). " +
                                    "Resetting isInActiveCall + isUserAvailable " +
                                    "before processing this FCM."
                            )
                            BaseApplication.getInstance()?.markCallEnded()
                            FcmUtils.isUserAvailable = 0
                        }

                        BaseApplication.getInstance()?.saveSenderId(senderId)
                        // Ringtone is now played by the OS via the calls_v4 channel
                        // (survives Doze / power saving). The foreground accept
                        // activities take over with MediaPlayer once they open
                        // — see B147 fix.
                        // Grab transient audio focus so YouTube / Spotify / etc.
                        // pause for the heads-up ringtone phase — fixes B150.
                        // Auto-released after 45 s by the BaseApplication helper.
                        BaseApplication.getInstance()?.requestRingtoneFocus()

                        callType?.let {
                            BaseApplication.getInstance()?.setIncomingCall(
                                senderId,
                                it, channelName, callId.toIntOrNull() ?: 0
                            )
                        }
                        // B023 — cache caller display info so MainActivity
                        // can re-launch the accept screen with avatar+name
                        // if the user opens Hima via launcher while ringing.
                        BaseApplication.getInstance()?.setIncomingCallerInfo(receiverName, receiverImg)

                        val intent = Intent(this, FemaleCallAcceptActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("CALL_TYPE", callType)
                            putExtra("SENDER_ID", senderId)
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("Caller_NAME", receiverName)
                            putExtra("Caller_Image", receiverImg)
                            putExtra("CALL_ID", callId.toIntOrNull() ?: 0)
                        }

                        val telecomExtras = Bundle().apply {
                            putString(HimaConnection.EXTRA_CALL_TYPE, callType)
                            putInt(HimaConnection.EXTRA_SENDER_ID, senderId)
                            putString(HimaConnection.EXTRA_CHANNEL_NAME, channelName)
                            putInt(HimaConnection.EXTRA_CALL_ID, callId.toIntOrNull() ?: 0)
                            putString(HimaConnection.EXTRA_CALLER_NAME, receiverName)
                            putString(HimaConnection.EXTRA_CALLER_IMAGE, receiverImg)
                            putString(HimaConnection.EXTRA_RECEIVER_GENDER, "female")
                        }
                        logIncomingCallEntry(
                            "female_incoming",
                            gender,
                            callType,
                            callId.toIntOrNull() ?: 0,
                            senderId,
                            channelName
                        )
                        Log.d(INCOMING_CALL_LOG_TAG, "female branch: before tryAddIncomingCall")
                        val telecomOkFemale = HimaTelecomManager.tryAddIncomingCall(this, telecomExtras)
                        Log.d(
                            INCOMING_CALL_LOG_TAG,
                            "female branch: after tryAddIncomingCall telecomOk=$telecomOkFemale (CallStyle still posted; self-managed has no system UI)"
                        )
                        // Compute foreground/locked once, up front. Drives BOTH the
                        // B030 skip-heads-up decision and the B020 belt-and-braces
                        // direct-launch decision below.
                        val fcmKgmFemale = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        val lockedFemale = fcmKgmFemale?.isKeyguardLocked == true
                        val foregroundFemale = !isAppInBackground(applicationContext)

                        // B030: When the app is foreground AND unlocked, the accept
                        // activity itself is the call UI. Posting the CallStyle
                        // notification on top of it shows a duplicate system heads-up
                        // banner — which is what the user sees as "call comes as
                        // system popup outside the app." Skip the notify() here; the
                        // activity provides ringtone + accept/decline. Background
                        // and locked still post (B147 channel ringtone + B020 FSI).
                        if (!foregroundFemale || lockedFemale) {
                            Log.d(INCOMING_CALL_LOG_TAG, "female branch: before notifyIncomingCallWithCallStyle (fg=$foregroundFemale locked=$lockedFemale)")
                            notifyIncomingCallWithCallStyle(
                                isMale = false,
                                callType,
                                senderId,
                                channelName,
                                callId.toIntOrNull() ?: 0,
                                receiverName,
                                receiverImg
                            )
                            Log.d(INCOMING_CALL_LOG_TAG, "female branch: after notifyIncomingCallWithCallStyle")
                        } else {
                            Log.d(INCOMING_CALL_LOG_TAG, "female branch: skipping heads-up — foreground + unlocked (B030)")
                        }

                        Log.d("callType", "$callType")
                        // Direct-launch the accept activity when EITHER the app is
                        // foreground OR the keyguard is locked. The locked case
                        // backs up the notification's full-screen intent (FSI gets
                        // blocked on some OEMs / when FSI permission is revoked);
                        // the activity is launchMode=singleTop so this is idempotent
                        // with FSI's own auto-launch. Fixes B020.
                        if (foregroundFemale || lockedFemale) {
                            Log.d(
                                "FCMService",
                                "Launching FemaleCallAcceptActivity (fg=$foregroundFemale locked=$lockedFemale)"
                            )
                            try { startActivity(intent) } catch (e: Exception) {
                                Log.e("FCMService", "startActivity FemaleAccept threw: ${e.message}")
                            }
                        }

//                        if (BaseApplication.getInstance()?.isAppInForeground() == true) {
//                            // App is in foreground, open activity directly
//                            startActivity(intent)
//                        } else {
//                            // App is in background, show notification instead
//                            showIncomingCallNotification(callType, senderId, channelName, callId.toIntOrNull() ?: 0, receiverName, receiverImg)
//                        }


                        if (currentActivity !is MainActivity &&
                            currentActivity !is EarningsActivity &&
                            currentActivity !is BankUpdateActivity) {

                            // App is NOT in these activities → Show notification
                          //  showIncomingCallNotification(callType, senderId, channelName, callId.toIntOrNull() ?: 0, receiverName, receiverImg)
                        } else {
                            Log.d("currentActivity", "User is in $currentActivity, skipping notification")

                        }


                    }

                    // ========== MALE INCOMING CALL HANDLING ==========
                    // Added for males to receive calls from females
                    if (gender == "male") {
                        // NB: Do NOT shadow these activity class names with local `val`s.
                        // A prior version did so and the shadowing made
                        // `Intent(this, MaleCallAcceptActivity::class.java)` below resolve to
                        // `java.lang.Class.class`, causing ActivityNotFoundException and an
                        // app crash when a female called a male user in foreground.
                        //
                        // Capture once so we can differentiate the source of the
                        // busy state below — needed for B204 missed-call posting.
                        val maleOnAnotherAppCall = isOnAnotherAppCall()

                        // Same split as the female branch above: separate "really
                        // busy right now" (in-call activity on top or telecom
                        // holds another call) from "boolean flags say busy"
                        // (which can stick across a killed call activity). Only
                        // auto-reject for the former; reset stale flags for the
                        // latter and proceed with this incoming call.
                        val inCallActivityShownMale =
                            currentActivity is MaleCallAcceptActivity ||
                            currentActivity is MaleCallConnectingActivity ||
                            currentActivity is MaleAudioCallingActivity ||
                            currentActivity is MaleVideoCallingActivity ||
                            currentActivity is com.gmwapp.hima.activities.IplRoomCallActivity
                        val flagsSayBusyMale =
                            BaseApplication.getInstance()?.isInActiveCall() == true ||
                            FcmUtils.isUserAvailable == 1

                        if (inCallActivityShownMale || maleOnAnotherAppCall) {

                            // B134 + B156a (symmetric, male side) — same rationale
                            // as the female branch: FcmUtils.isUserAvailable is the
                            // synchronous intent flag set at the click handler before
                            // the Connecting Activity has a chance to come up, closing
                            // the race window the currentActivity check otherwise
                            // misses.
                            Log.d("FCM", "Male user is already in a call. Ignoring incoming call notification.")

                            val receiverId = senderId
                            sendAutoRejectNotification(userData?.id, receiverId, callType, channelName)

                            // B204 + TC_014 (symmetric, male side) — leave a "Missed call
                            // from X" notification on ANY busy auto-reject, including an
                            // in-Hima-call busy state (this is TC_014's main case: the user
                            // is mid-call with creator A when creator B calls). One post per
                            // FCM; same-sender duplicates are dropped by the guard above.
                            postBusyMissedCall(callType, senderId, receiverName, receiverImg)
                            return
                        }

                        if (flagsSayBusyMale) {
                            Log.w(
                                INCOMING_CALL_LOG_TAG,
                                "Stale busy flags detected on male side " +
                                    "(no in-call activity, no telecom call). " +
                                    "Resetting isInActiveCall + isUserAvailable " +
                                    "before processing this FCM."
                            )
                            BaseApplication.getInstance()?.markCallEnded()
                            FcmUtils.isUserAvailable = 0
                        }

                        BaseApplication.getInstance()?.saveSenderId(senderId)
                        // Ringtone is now played by the OS via the calls_v4 channel
                        // (survives Doze / power saving). The foreground accept
                        // activities take over with MediaPlayer once they open
                        // — see B147 fix.
                        // Grab transient audio focus so YouTube / Spotify / etc.
                        // pause for the heads-up ringtone phase — fixes B150.
                        // Auto-released after 45 s by the BaseApplication helper.
                        BaseApplication.getInstance()?.requestRingtoneFocus()

                        callType?.let {
                            BaseApplication.getInstance()?.setIncomingCall(
                                senderId,
                                it, channelName, callId.toIntOrNull() ?: 0
                            )
                        }
                        // B023 — same caller-info cache as the female branch
                        // above; used by MainActivity to re-launch the accept
                        // screen with avatar+name if user opens Hima via
                        // launcher while ringing.
                        BaseApplication.getInstance()?.setIncomingCallerInfo(receiverName, receiverImg)

                        val intent = Intent(this, MaleCallAcceptActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("CALL_TYPE", callType)
                            putExtra("SENDER_ID", senderId)
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("Caller_NAME", receiverName)
                            putExtra("Caller_Image", receiverImg)
                            putExtra("CALL_ID", callId.toIntOrNull() ?: 0)
                        }

                        val telecomExtrasMale = Bundle().apply {
                            putString(HimaConnection.EXTRA_CALL_TYPE, callType)
                            putInt(HimaConnection.EXTRA_SENDER_ID, senderId)
                            putString(HimaConnection.EXTRA_CHANNEL_NAME, channelName)
                            putInt(HimaConnection.EXTRA_CALL_ID, callId.toIntOrNull() ?: 0)
                            putString(HimaConnection.EXTRA_CALLER_NAME, receiverName)
                            putString(HimaConnection.EXTRA_CALLER_IMAGE, receiverImg)
                            putString(HimaConnection.EXTRA_RECEIVER_GENDER, "male")
                        }
                        logIncomingCallEntry(
                            "male_incoming",
                            gender,
                            callType,
                            callId.toIntOrNull() ?: 0,
                            senderId,
                            channelName
                        )
                        Log.d(INCOMING_CALL_LOG_TAG, "male branch: before tryAddIncomingCall")
                        val telecomOkMale = HimaTelecomManager.tryAddIncomingCall(this, telecomExtrasMale)
                        Log.d(
                            INCOMING_CALL_LOG_TAG,
                            "male branch: after tryAddIncomingCall telecomOk=$telecomOkMale (CallStyle still posted; self-managed has no system UI)"
                        )
                        // Compute foreground/locked once, up front. Drives BOTH the
                        // B030 skip-heads-up decision and the B020 belt-and-braces
                        // direct-launch decision below.
                        val fcmKgmMale = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        val lockedMale = fcmKgmMale?.isKeyguardLocked == true
                        val foregroundMale = !isAppInBackground(applicationContext)

                        // B030: When the app is foreground AND unlocked, the accept
                        // activity itself is the call UI. Posting the CallStyle
                        // notification on top of it shows a duplicate system heads-up
                        // banner — which is what the user sees as "call comes as
                        // system popup outside the app." Skip the notify() here; the
                        // activity provides ringtone + accept/decline. Background
                        // and locked still post (B147 channel ringtone + B020 FSI).
                        if (!foregroundMale || lockedMale) {
                            Log.d(INCOMING_CALL_LOG_TAG, "male branch: before notifyIncomingCallWithCallStyle (fg=$foregroundMale locked=$lockedMale)")
                            notifyIncomingCallWithCallStyle(
                                isMale = true,
                                callType,
                                senderId,
                                channelName,
                                callId.toIntOrNull() ?: 0,
                                receiverName,
                                receiverImg
                            )
                            Log.d(INCOMING_CALL_LOG_TAG, "male branch: after notifyIncomingCallWithCallStyle")
                        } else {
                            Log.d(INCOMING_CALL_LOG_TAG, "male branch: skipping heads-up — foreground + unlocked (B030)")
                        }

                        Log.d("MaleCallAccept_CallType", "$callType")
                        // Direct-launch the accept activity when EITHER the app is
                        // foreground OR the keyguard is locked. Backs up the
                        // notification's full-screen intent (FSI gets blocked on
                        // some OEMs / when the FSI permission is revoked); the
                        // activity is launchMode=singleTop so this is idempotent
                        // with FSI's own auto-launch. Fixes B020.
                        if (foregroundMale || lockedMale) {
                            Log.d(
                                "FCMService_Male",
                                "Launching MaleCallAcceptActivity (fg=$foregroundMale locked=$lockedMale)"
                            )
                            try { startActivity(intent) } catch (e: Exception) {
                                Log.e("FCMService_Male", "startActivity MaleAccept threw: ${e.message}")
                            }
                        }

                        if (currentActivity !is MainActivity &&
                            currentActivity !is EarningsActivity &&
                            currentActivity !is BankUpdateActivity) {
                            // App is NOT in these activities → Show notification
                            // Already shown above
                        } else {
                            Log.d("currentActivity_Male", "User is in $currentActivity, skipping notification")
                        }
                    }
                    // ========== END MALE INCOMING CALL HANDLING ==========







                }
            }



            if (message == "accepted" || message == "rejected" && gender=="male") {
                Log.d(
                    "MaleVideoEndFlow",
                    "route=male_accepted_rejected_updateCallStatus message=$message channelName=$channelName currentActivity=${fcmCurrentActivity?.javaClass?.simpleName}"
                )
                FcmUtils.updateCallStatus(message, channelName)
            }

            // Handle call status updates for females (receiving acceptance/rejection from males)
            if ((message == "accepted" || message == "rejected") && gender == "female") {
                Log.d("FCM_Female", "Received call status: $message from male user: $senderId")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=female_accepted_rejected_updateCallStatus message=$message senderId=$senderId currentActivity=${fcmCurrentActivity?.javaClass?.simpleName}"
                )
                FcmUtils.updateCallStatus(message, senderId.toString())
            }

            // Tester report → camera-unavailable handshake. The female side
            // detected a broken camera, joined audio-only, and is in the 30s
            // grace window before disconnecting. Forward the signal so the
            // male-side calling activity can show a matching banner +
            // dialog. channelName scopes the signal to THIS call (the
            // observer ignores messages for other in-flight channels).
            if (message == "cameraUnavailable" && gender == "male") {
                Log.d("FCM", "route=cameraUnavailable_male channel=$channelName")
                if (!channelName.isNullOrEmpty()) {
                    FcmUtils.updateCameraUnavailable(channelName)
                }
            }

            if (message == "userBusy" && gender == "male") {
                Log.d(
                    "MaleVideoEndFlow",
                    "route=userBusy_male currentActivity=${fcmCurrentActivity?.javaClass?.simpleName} callType=$callType senderId=$senderId"
                )
                Log.d("FCM", "User is busy. Checking current activity.")
                
                // Get current activity
                val currentActivity = BaseApplication.getInstance()?.getCurrentActivity()
                
                when {
                    // Direct call connecting screen - show message and redirect to random call
                    currentActivity is com.gmwapp.hima.agora.male.MaleCallConnectingActivity -> {
                        Log.d("FCM", "User is on direct call connecting screen. Showing busy message.")
                        
                        // Get callType and receiver name from FCM data
                        val userName = remoteMessage.data["receiverName"] ?: "User"
                        
                        // Notify MaleCallConnectingActivity via FcmUtils
                        FcmUtils.updateUserBusyStatus(callType ?: "audio", userName)
                    }
                    
                    // Random call connecting screen - silently retry with another user
                    currentActivity is AgoraRandomCallActivity -> {
                        Log.d("FCM", "User is on random call connecting screen. Triggering retry.")
                        
                        // Get callType from FCM data
                        val userName = remoteMessage.data["receiverName"] ?: "User"
                        
                        // Notify AgoraRandomCallActivity via FcmUtils to retry
                        FcmUtils.updateUserBusyStatus(callType ?: "audio", userName)
                    }
                    
                    // User is in other activities - ignore
                    else -> {
                        Log.d("FCM", "User is not on connecting screen (current: ${currentActivity?.javaClass?.simpleName}). Ignoring userBusy message.")
                        // User might be in:
                        // - MaleAudioCallingActivity / MaleVideoCallingActivity (already in a call)
                        // - MainActivity (already went back)
                        // In all these cases, do nothing
                    }
                }
            }


            if (message == "callDeclined" && gender == "female") {
                Log.d(
                    "MaleVideoEndFlow",
                    "route=callDeclined_female senderId=$senderId currentActivity=${fcmCurrentActivity?.javaClass?.simpleName}"
                )
                Log.d("FCM", "User is busy. Redirecting to MainActivity.")


                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val isScreenLocked = keyguardManager.isKeyguardLocked
                var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                if (senderId==previousSenderId) {
                    HimaTelecomManager.endActiveCall(DisconnectCause.REMOTE)
                    com.gmwapp.hima.agora.FcmCallService.stop(this)
                    BaseApplication.getInstance()?.stopRingtone()
                    cancelIncomingCallNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()

                    val currentActivity = BaseApplication.getInstance()?.getCurrentActivity()
                    if (isScreenLocked || isAppInBackground(applicationContext)) {
                        // B126 — when the caller cancels while we're in the
                        // background (or screen-locked), don't yank Hima to
                        // the foreground. The cleanup above already cancelled
                        // the heads-up notification; the user stays in
                        // whatever app they were using. If a call-accept
                        // screen happens to be on top (full-screen intent
                        // fired then user backgrounded), finish it quietly.
                        Log.d("FCMService", "callDeclined while bg/locked — dismissing silently, no MainActivity launch")
                        if (currentActivity is FemaleCallAcceptActivity ||
                            currentActivity is MaleCallAcceptActivity) {
                            currentActivity.finish()
                        }
                    } else {
                        Log.d("FCMService", "App is in foreground (Visible)")
                        if (currentActivity !is MainActivity) {
                            val mainIntent = Intent(this, MainActivity::class.java).apply {
                                flags =
                                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(mainIntent)
                        }
                    }
                }

            }

            // ========== MALE HANDLER FOR CALL DECLINED ==========
            // Added for males to receive call cancellation from females
            if (message == "callDeclined" && gender == "male") {
                Log.d(
                    "MaleVideoEndFlow",
                    "route=callDeclined_male senderId=$senderId previousSenderId=${BaseApplication.getInstance()?.getSenderId()} currentActivity=${fcmCurrentActivity?.javaClass?.simpleName}"
                )
                Log.d("FCM_Male", "Female caller cancelled. Closing incoming call screen.")

                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val isScreenLocked = keyguardManager.isKeyguardLocked
                var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                
                if (senderId == previousSenderId) {
                    HimaTelecomManager.endActiveCall(DisconnectCause.REMOTE)
                    com.gmwapp.hima.agora.FcmCallService.stop(this)
                    BaseApplication.getInstance()?.stopRingtone()
                    cancelIncomingCallNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()

                    val currentActivity = BaseApplication.getInstance()?.getCurrentActivity()
                    if (isScreenLocked || isAppInBackground(applicationContext)) {
                        // B126 — same fix as the female branch: don't
                        // foreground Hima when the caller cancels while
                        // we're backgrounded/locked. Notification cleanup
                        // already ran above; the user stays in whatever
                        // app they were using. Finish a lingering
                        // call-accept screen if one is on top.
                        Log.d("FCMService_Male", "callDeclined while bg/locked — dismissing silently, no MainActivity launch")
                        if (currentActivity is FemaleCallAcceptActivity ||
                            currentActivity is MaleCallAcceptActivity) {
                            currentActivity.finish()
                        }
                    } else {
                        Log.d("FCMService_Male", "App is in foreground (Visible)")
                        if (currentActivity !is MainActivity) {
                            val mainIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(mainIntent)
                        }
                    }
                }
            }
            // ========== END MALE HANDLER FOR CALL DECLINED ==========

            if (message == "remainingTimeUpdated" && gender == "female") {

                var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                if (senderId==previousSenderId){

                    FcmUtils.updateRemainingTime(message)

                }

            }

            // Server-driven force-end when male's coins are exhausted mid-call.
            // Backend pushes this to BOTH parties (male + female). Forwarded to
            // FcmUtils.forceEndCall so the active-call activities can hang up
            // without relying on the client-side countdown timer (B184 follow-up).
            if (message == "callEndedNoCoins") {
                val callIdInt = remoteMessage.data["call_id"]?.toIntOrNull() ?: 0
                if (callIdInt > 0) {
                    Log.d(INCOMING_CALL_LOG_TAG, "callEndedNoCoins received callId=$callIdInt")
                    FcmUtils.forceEndCall(callIdInt, "no_coins")
                } else {
                    Log.w(INCOMING_CALL_LOG_TAG, "callEndedNoCoins ignored — missing/invalid call_id")
                }
            }

            // 2026-05-22 — instant peer-hangup propagation. When the OTHER side
            // taps the red button, their app fires POST /api/auth/notify_call_end
            // which pushes us this FCM. Without this, we'd wait ~25s for Agora's
            // onUserOffline (or our 30s watchdog) to detect they're gone. Now
            // disconnect immediately for both parties.
            if (message == "callEnded") {
                // 2026-05-23 v1069 — DISABLED for IN-CALL hangups because Agora's
                // onUserOffline is the canonical peer-end signal AFTER both sides
                // joined the channel.
                //
                // v1110 TC_MC_01 RE-ENABLED for RINGING stage only.
                //
                // BUG: when the caller cancels his outgoing call BEFORE the
                // recipient taps Accept, the recipient is still on
                // {Female,Male}CallAcceptActivity and has NOT joined the Agora
                // channel yet. onUserOffline never fires (you can't go offline
                // from a channel you never joined). The incoming-call screen
                // stays ringing forever and if the recipient eventually taps
                // Accept she joins an empty channel and sees a black video
                // screen (TC_MC_02 was just a downstream consequence).
                //
                // FIX: if we receive callEnded WHILE a CallAcceptActivity is
                // foreground AND we're not in a real Agora call yet, tear the
                // ring UI down the same way callDeclined would. The
                // isInRealCall() guard preserves v1069 behavior for genuine
                // in-call hangups (those still let Agora drive the teardown).
                val currentActivity = BaseApplication.getInstance()?.getCurrentActivity()
                val inAcceptStage = currentActivity is FemaleCallAcceptActivity ||
                                     currentActivity is MaleCallAcceptActivity
                val notInActiveCall = BaseApplication.getInstance()?.isInRealCall() != true
                if (inAcceptStage && notInActiveCall) {
                    Log.d(INCOMING_CALL_LOG_TAG, "callEnded during ring stage — dismissing accept screen (TC_MC_01)")
                    // TC_026: capture the ringing caller's details (stored when the ring
                    // started, via setIncomingCall/setIncomingCallerInfo) BEFORE
                    // clearIncomingCall() wipes them — so an unanswered incoming call
                    // leaves a "Missed call from X" notification (like a normal phone)
                    // instead of vanishing without a trace. We only reach this branch when
                    // the call was NOT answered (still on the Accept screen, not in a real
                    // call), so it can never fire for a genuinely connected/ended call.
                    val missedApp = BaseApplication.getInstance()
                    val missedSenderId = missedApp?.getSenderIdForSplashActivity() ?: -1
                    val missedCallType = missedApp?.getCallTypeForSplashActivity()
                        ?.takeIf { it.isNotBlank() && it != "null" }
                    val missedCallerName = missedApp?.getIncomingCallerName().orEmpty()
                    val missedCallerImage = missedApp?.getIncomingCallerImage()
                    try { HimaTelecomManager.endActiveCall(DisconnectCause.REMOTE) } catch (_: Throwable) {}
                    try { com.gmwapp.hima.agora.FcmCallService.stop(this) } catch (_: Throwable) {}
                    try { BaseApplication.getInstance()?.stopRingtone() } catch (_: Throwable) {}
                    try { cancelIncomingCallNotification() } catch (_: Throwable) {}
                    // TC_026: tear down the ring state FIRST — before the missed-call
                    // notification, which blocks the FCM thread up to 2s loading the
                    // caller's avatar (Glide.get(2s)). Previously that load sat between
                    // here and the teardown, so a battery-restricted device that killed
                    // the FCM process mid-load left clearIncomingCall()/finish() un-run
                    // → a ghost ring screen. The missed-call payload was already captured
                    // into locals above, so clearing the incoming-call state first is safe.
                    try { BaseApplication.getInstance()?.clearIncomingCall() } catch (_: Throwable) {}
                    currentActivity?.finish()
                    // TC_026: now (after teardown) replace the dismissed ring notification
                    // with a missed-call one. If the process is killed during the avatar
                    // load only this notification is lost — the teardown already completed.
                    if (missedSenderId > 0) {
                        try {
                            com.gmwapp.hima.utils.CallNotifications.showMissed(
                                this,
                                com.gmwapp.hima.utils.CallNotifications.MissedPayload(
                                    callType = missedCallType,
                                    senderId = missedSenderId,
                                    callerName = missedCallerName,
                                    callerImage = missedCallerImage
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(INCOMING_CALL_LOG_TAG, "TC_026 missed-call post failed: ${e.message}", e)
                        }
                    }
                } else {
                    Log.d(INCOMING_CALL_LOG_TAG, "callEnded ignored — not in ring stage (Agora onUserOffline owns in-call teardown)")
                }
            }


            if (message.startsWith("switchToVideo") && gender == "female") {
                    val parts = message.split(" ")
                    if (parts.size == 2) {
                        val callId = parts[1]  // Extract callId from the message
                        val callidInt: Int = callId.toIntOrNull() ?: 0  // Defaults to 0 if conversion fails
                        Log.d("callIdofSwitch", "$callId")


                var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                if (senderId==previousSenderId){

                    Log.d("switchToVideo","$message")
                    Log.d(
                        "MaleVideoEndFlow",
                        "route=female_switchToVideo -> UpdateCallSwitch(callidInt=$callidInt senderId=$senderId)"
                    )
                    FcmUtils.UpdateCallSwitch("switchToVideo",callidInt)

                }

            }}

            if (message.startsWith("switchToAudio") && gender == "female") {
                val parts = message.split(" ")
                if (parts.size == 2) {
                    val callId = parts[1]  // Extract callId from the message
                    val callidInt: Int = callId.toIntOrNull() ?: 0  // Defaults to 0 if conversion fails
                    Log.d("callIdofSwitch", "$callId")


                    var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                    if (senderId==previousSenderId){

                        Log.d("switchToVideo","$message")
                        Log.d(
                            "MaleVideoEndFlow",
                            "route=female_switchToAudio -> UpdateCallSwitch(callidInt=$callidInt senderId=$senderId)"
                        )
                        FcmUtils.UpdateCallSwitch("switchToAudio",callidInt)

                    }

                }}

            if (message == "VideoAccepted" && gender == "male") {

                Log.d("switchToVideo","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=male_VideoAccepted -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)



            }

            if (message == "AudioAccepted" && gender == "male") {

                Log.d("AudioAccepted","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=male_AudioAccepted -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)



            }

            if (message == "SwitchDeclined" && gender == "male") {

                Log.d("SwitchDeclined","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=male_SwitchDeclined -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)



            }


            if (message.startsWith("switchToVideo") && gender == "male") {
                val parts = message.split(" ")
                if (parts.size == 2) {
                    val callId = parts[1]  // Extract callId from the message
                    val callidInt: Int = callId.toIntOrNull() ?: 0  // Defaults to 0 if conversion fails
                    Log.d("callIdofSwitch", "$callId")
                    Log.d("switchToVideo","$message")
                    Log.d(
                        "MaleVideoEndFlow",
                        "route=male_switchToVideo -> UpdateCallSwitch(callidInt=$callidInt senderId=$senderId)"
                    )
                    FcmUtils.UpdateCallSwitch("switchToVideo",callidInt)

                }}




            if (message == "VideoAccepted" && gender == "female") {
                Log.d("switchToVideo","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=female_VideoAccepted -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)
            }

            if (message == "giftSent" && gender == "female") {
                var giftImage= callType
                FcmUtils.giftReceivedImage(giftImage.toString())
            }


            if (message.startsWith("switchToAudio") && gender == "male") {
                val parts = message.split(" ")
                if (parts.size == 2) {
                    val callId = parts[1]  // Extract callId from the message
                    val callidInt: Int = callId.toIntOrNull() ?: 0  // Defaults to 0 if conversion fails
                    Log.d("callIdofSwitch", "$callId")
                    Log.d("switchToAudio","$message")
                    Log.d(
                        "MaleVideoEndFlow",
                        "route=male_switchToAudio -> UpdateCallSwitch(callidInt=$callidInt senderId=$senderId)"
                    )
                    FcmUtils.UpdateCallSwitch("switchToAudio",callidInt)

                }}


            if (message == "AudioAccepted" && gender == "female") {

                Log.d("AudioAccepted","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=female_AudioAccepted -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)

            }

            if (message == "SwitchDeclined" && gender == "female") {

                Log.d("SwitchDeclined","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=female_SwitchDeclined -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)

            }



            if (message == "greyScreenEnable") {

                Log.d("greyScreenLog","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=greyScreenEnable -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)
                FcmUtils.greyScreenLiveData.postValue(message)

            }

            if (message == "greyScreenDisable") {

                Log.d("greyScreenLog","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=greyScreenDisable -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)
                FcmUtils.greyScreenLiveData.postValue(message)

            }


        }



    }

    private fun handleClearDataFcm() {
        try {
            Log.w("FCM_ClearData", "Received clear_data. Clearing user session.")

            // Stop any ongoing call UI/notifications/ringtone best-effort.
            HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
            com.gmwapp.hima.agora.FcmCallService.stop(this)
            BaseApplication.getInstance()?.stopRingtone()
            cancelIncomingCallNotification()
            BaseApplication.getInstance()?.clearIncomingCall()

            // T32: shared teardown — same order as logout sheet and 401 path.
            BaseApplication.getInstance()?.performGlobalSessionTeardown()
            BaseApplication.getInstance()?.getPrefs()?.clearUserData()

            // If app is visible, redirect to login; otherwise show a notification that opens login.
            if (!isAppInBackground(applicationContext)) {
                val intent = Intent(this, NewLoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            } else {
                // App is killed/backgrounded — surface a tap-to-login notification so
                // the user discovers the forced logout instead of finding it on next
                // app launch (H16).
                showSessionClearedNotification()
            }
        } catch (e: Exception) {
            Log.e("FCM_ClearData", "Failed to clear session: ${e.message}", e)
        }
    }

    private fun showSessionClearedNotification() {
        createSystemNotificationChannel()

        val intent = Intent(this, NewLoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            9901,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(this, "system_events")
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Session cleared")
            .setContentText("Please login again to continue.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(9901, notification)
    }

    private fun createSystemNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "system_events",
                "System",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }



    private fun isAppInBackground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return true

        for (process in appProcesses) {
            if (process.processName == context.packageName) {
                return process.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        }
        return true
    }

    /** Ensures channel exists, then logs device/permission state for incoming-call debugging. */
    private fun logIncomingCallEntry(
        leg: String,
        gender: String?,
        callType: String?,
        callId: Int,
        senderId: Int,
        channelName: String
    ) {
        createNotificationChannel()
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val locked = km.isKeyguardLocked
        val bg = isAppInBackground(applicationContext)
        val postNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val canUseFullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent()
        } else {
            null
        }
        val manageOwnCallsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.MANAGE_OWN_CALLS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            null
        }
        val channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(CALLS_NOTIFICATION_CHANNEL_ID)?.importance
        } else {
            null
        }
        Log.d(
            INCOMING_CALL_LOG_TAG,
            "[$leg] sdk=${Build.VERSION.SDK_INT} gender=$gender callType=$callType callId=$callId " +
                "senderId=$senderId channel=$channelName keyguardLocked=$locked appInBackground=$bg " +
                "postNotificationsGranted=$postNotificationsGranted " +
                "canUseFullScreenIntent=$canUseFullScreenIntent manageOwnCallsGranted=$manageOwnCallsGranted " +
                "calls_v4_channelImportance=$channelImportance"
        )
    }


    /**
     * Returns true when the user is currently on a non-Hima voice/video
     * call (WhatsApp / Telegram / Meet / Zoom / SIM call) — i.e. the OS
     * audio mode is engaged for a call but the engagement isn't ours.
     *
     * Without this check (B131), an FCM arriving while the user is on a
     * WhatsApp video call:
     *   - falls through to the normal-incoming path, so the channel
     *     ringtone plays,
     *   - but the full-screen-intent / heads-up is hidden behind the
     *     other app's call UI, so the user can't tap Accept/Decline,
     *   - and the caller side stays on Connecting until server timeout.
     *
     * With this check we auto-reject up front; caller-side resolves to
     * "user busy" within seconds and the recipient gets a missed-call
     * notification they can call back from when they're free.
     *
     * Doesn't require any extra permission — `AudioManager.getMode()` is
     * an unguarded read.
     */
    private fun isOnAnotherAppCall(): Boolean {
        val ourCallActive = BaseApplication.getInstance()?.isInActiveCall() == true
        if (ourCallActive) return false
        // B067: cross-check both AudioManager.mode (catches MODE_IN_COMMUNICATION
        // from other VoIP apps) AND TelephonyManager.callState (catches SIM
        // calls — some OEMs, notably Samsung, don't flip AudioManager.mode to
        // MODE_IN_CALL while a SIM call is in RINGING state, so the audio-mode
        // check alone misses the window where an incoming Hima call lands on
        // top of an incoming SIM call).
        val audioModeBusy = try {
            val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val mode = am?.mode ?: AudioManager.MODE_NORMAL
            val busy = mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_IN_CALL
            // v1110 TC_HL_04 FIX: AudioManager.mode can stay stuck at
            // MODE_IN_COMMUNICATION when our previous call activity dies without
            // running leaveChannel() (OEM kill, crash, system pressure). When that
            // happens the next incoming call goes straight to "missed" without
            // ringing because audioModeBusy=true even though no real call exists.
            // We already verified isInActiveCall()==false above, so any busy mode
            // here is STALE — clear it back to MODE_NORMAL so this push and every
            // future push rings normally.
            if (busy && am != null) {
                try {
                    am.mode = AudioManager.MODE_NORMAL
                    Log.w("FCM", "isOnAnotherAppCall: cleared STALE AudioManager.mode ($mode) -> MODE_NORMAL")
                } catch (t: Throwable) {
                    Log.w("FCM", "isOnAnotherAppCall: failed to clear stale mode: ${t.message}")
                }
                // Now that we cleared it, treat audio mode as NOT busy for this push.
                false
            } else {
                busy
            }
        } catch (e: Exception) {
            Log.w("FCM", "isOnAnotherAppCall: AudioManager.mode threw: ${e.message}")
            false
        }
        val telephonyBusy = com.gmwapp.hima.utils.CallPhoneStateHelper
            .isCellularCallBusy(applicationContext)
        // B131 — WhatsApp video doesn't reliably flip AudioManager.mode to
        // MODE_IN_COMMUNICATION on every OEM build, so the audio-mode check
        // alone lets a Hima call ring through while WhatsApp video is active.
        // TelecomManager.isInCall covers every self-managed ConnectionService
        // (WhatsApp video/voice, Signal, Telegram VoIP, Google Meet, plus SIM)
        // and closes that gap. Requires READ_PHONE_STATE, which is already
        // declared in the manifest; the catch is defensive for OEM-quirky
        // SecurityExceptions on Android Q before runtime grant.
        val telecomBusy = try {
            val tm = applicationContext.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) tm?.isInCall == true else false
        } catch (e: SecurityException) {
            Log.w("FCM", "isOnAnotherAppCall: TelecomManager.isInCall denied: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w("FCM", "isOnAnotherAppCall: TelecomManager.isInCall threw: ${e.message}")
            false
        }
        return audioModeBusy || telephonyBusy || telecomBusy
    }

    /**
     * Posts a local "Missed call" notification using the existing
     * [com.gmwapp.hima.utils.CallNotifications.showMissed] pipeline.
     *
     * Called from the FCM busy-check path when the busy state was caused
     * by ANOTHER app's voice/video call (WhatsApp, SIM, Meet, etc.) — see
     * B204. Without this the recipient had no record on their phone that
     * someone tried to reach them on Hima while they were on the other
     * call; they'd have to manually open Hima and dig into Recent to find
     * out, often long after the moment passed.
     *
     * Failures (no FCM token, render exception, no POST_NOTIFICATIONS
     * permission) are logged but never break the FCM dispatcher.
     */
    private fun postBusyMissedCall(
        callType: String?,
        senderId: Int,
        callerName: String?,
        callerImage: String?
    ) {
        try {
            com.gmwapp.hima.utils.CallNotifications.showMissed(
                this,
                com.gmwapp.hima.utils.CallNotifications.MissedPayload(
                    callType = callType,
                    senderId = senderId,
                    callerName = callerName.orEmpty(),
                    callerImage = callerImage
                )
            )
        } catch (e: Exception) {
            Log.e(INCOMING_CALL_LOG_TAG, "postBusyMissedCall failed: ${e.message}", e)
        }
    }

    private fun sendAutoRejectNotification(senderId: Int?, receiverId: Int?, callType: String?, channelName: String?) {
        if (senderId != null && receiverId != null && callType != null && channelName != null) {
            fcmNotificationRepository.sendFcmNotification(
                senderId, receiverId, callType, channelName, "userBusy",
                object : NetworkCallback<FcmNotificationResponse> {
                    override fun onResponse(call: retrofit2.Call<FcmNotificationResponse>, response: retrofit2.Response<FcmNotificationResponse>) {
                        Log.d("FCMNotification", "Auto-reject sent: ${response.body()?.message}")
                    }

                    override fun onFailure(call: retrofit2.Call<FcmNotificationResponse>, t: Throwable) {
                        Log.e("FCMNotification", "Error sending auto-reject: ${t.message}")
                    }

                    override fun onNoNetwork() {
                        Log.e("FCMNotification", "No network for auto-reject")
                    }
                }
            )
        }
    }

    private fun notifyIncomingCallWithCallStyle(
        isMale: Boolean,
        callType: String?,
        senderId: Int,
        channelName: String,
        callId: Int,
        receiverName: String,
        receiverImg: String
    ) {
        // B022: route FCM-driven incoming calls through [FcmCallService] so a
        // foreground service keeps the process warm between FCM-arrival and
        // accept-tap. The service uses [CallNotifications.buildIncomingCallNotification]
        // for its `startForeground` notification, so the visible UI is identical
        // to the legacy notify path (still used by OneSignal/BaseApplication).
        com.gmwapp.hima.agora.FcmCallService.start(
            this,
            com.gmwapp.hima.utils.CallNotifications.IncomingPayload(
                isMale = isMale,
                callType = callType,
                senderId = senderId,
                callId = callId,
                channelName = channelName,
                callerName = receiverName,
                callerImage = receiverImg
            )
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // B-v1110 #4 — createNotificationChannel has crashed in the field with
            // SecurityException on some OEM ROMs (vibration / sound / ringtone-URI
            // handling) despite VIBRATE being declared in the manifest. Wrap as a
            // safety net: if the channel can't be created the worst case is default
            // channel behaviour, which is far better than crashing the FCM service
            // (which silently kills incoming-call pushes for that user).
            try {
                val nm = getSystemService(NotificationManager::class.java) ?: return
                if (nm.getNotificationChannel(CALLS_NOTIFICATION_CHANNEL_ID) != null) return
                // Drop the silent v3 and the bypass-DND v4 so Settings doesn't show
                // multiple "Incoming Calls" rows after the v5 bump (B199).
                runCatching { nm.deleteNotificationChannel("calls_v3") }
                runCatching { nm.deleteNotificationChannel("calls_v4") }
                val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(
                    applicationContext,
                    RingtoneManager.TYPE_RINGTONE
                ) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: Settings.System.DEFAULT_RINGTONE_URI
                val ringtoneAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val channel = NotificationChannel(
                    CALLS_NOTIFICATION_CHANNEL_ID,
                    "Incoming Calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setSound(ringtoneUri, ringtoneAttrs)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                    // B199 — do NOT setBypassDnd. Hima is a social app, not a
                    // primary phone replacement; when the user enables system
                    // Do Not Disturb mode we must respect that and stay silent.
                    // The previous v4 channel set bypassDnd=true (carried over
                    // from the B147 Doze fix, but DND ≠ Doze — bypassing DND
                    // never helped with Doze and only annoyed users who'd
                    // explicitly muted their phone).
                }
                nm.createNotificationChannel(channel)
            } catch (t: Throwable) {
                Log.e(INCOMING_CALL_LOG_TAG, "createNotificationChannel failed: ${t.javaClass.simpleName}: ${t.message}", t)
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(t)
            }
        }
    }

    companion object {
        /**
         * Channel ID bump history (channels are immutable per ID on Android O+):
         *   v3 → v4: added OS-played ringtone so Doze no longer suppressed the ring (B147).
         *   v4 → v5: removed setBypassDnd(true) so system DND mode silences the
         *            ringtone like every other social app (B199).
         */
        const val CALLS_NOTIFICATION_CHANNEL_ID = "calls_v5"
        private const val INCOMING_CALL_LOG_TAG = "HimaIncomingCall"
        private const val INCOMING_CALL_NOTIFICATION_ID = 1
    }


    fun cancelIncomingCallNotification() {
        // Use the tray sweeper rather than the tag-based cancel: a ring delivered
        // via the OneSignal CallStyle path records neither a senderId nor a
        // lastIncomingCallTag, so cancelIncomingCallStyleNotification() (which
        // cancels by lastIncomingCallTag) silently missed it — the ring stayed
        // on-screen with no immediate cut when the caller cancelled. The sweeper
        // clears every incoming-call notification by channel/content, matching the
        // dismiss every *CallingActivity already uses.
        BaseApplication.getInstance()?.cancelAllIncomingCallNotifications()
        // B171 — the incoming-call notification is the foreground notification
        // of FcmCallService. NotificationManager.cancel() can't dismiss a
        // foreground-service notification — only stopping the service does,
        // which is why the heads-up used to linger up to FcmCallService's
        // 35s self-timeout after the caller had already given up. Stopping
        // the service tears down its notification immediately.
        FcmCallService.stop(applicationContext)
    }

}