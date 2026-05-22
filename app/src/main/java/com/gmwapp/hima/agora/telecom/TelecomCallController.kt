package com.gmwapp.hima.agora.telecom

import android.util.Log

/**
 * I039 — bridges Telecom hold/unhold callbacks (fired on the self-managed
 * [HimaConnection]) to the currently-foregrounded calling activity, which is
 * the only place that holds a live reference to the Agora engine.
 *
 * Lifecycle: each *CallingActivity registers a [holdHook] in onCreate and
 * clears it in onDestroy. When Telecom asks us to hold (e.g. a SIM call or
 * WhatsApp call took the user's attention via the system second-call UI),
 * [dispatchHold] forwards `true`; on unhold, `false`. The activity wires
 * those to its existing `muteForInterrupt(...)` so the audio behaviour is
 * identical to a cellular interrupt.
 *
 * Why not just rely on [com.gmwapp.hima.utils.CallPhoneStateHelper]?
 * That helper only sees `TelephonyManager.CALL_STATE_OFFHOOK`, which fires
 * for SIM cellular calls only — WhatsApp / Telegram / Signal calls go
 * through their own self-managed Telecom accounts and never flip the
 * telephony state. Telecom's onHold callback is the one signal that covers
 * both cases.
 */
object TelecomCallController {

    private const val TAG = "TelecomCallController"

    @Volatile
    private var holdHook: ((Boolean) -> Unit)? = null

    fun register(hook: (Boolean) -> Unit) {
        holdHook = hook
        Log.d(TAG, "register: hold hook attached")
    }

    fun clear() {
        if (holdHook != null) {
            holdHook = null
            Log.d(TAG, "clear: hold hook detached")
        }
    }

    fun dispatchHold(onHold: Boolean) {
        val hook = holdHook
        if (hook == null) {
            Log.d(TAG, "dispatchHold($onHold) — no hook registered, ignoring")
            return
        }
        try {
            hook.invoke(onHold)
        } catch (e: Exception) {
            Log.e(TAG, "dispatchHold($onHold) callback threw: ${e.message}", e)
        }
    }
}
