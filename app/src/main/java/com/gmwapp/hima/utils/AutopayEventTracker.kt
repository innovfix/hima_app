package com.gmwapp.hima.utils

import android.util.Log
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.NotificationClickResponse
import retrofit2.Call
import retrofit2.Response

/**
 * Fire-and-forget funnel-event sink for the autopay journey.
 * Marketing uses /autopay-events admin to see conversion drop-off
 * between event_type buckets. Backend treats event_type as free-form
 * string (max 64 chars) so new events ship without server changes.
 *
 * Convention: snake_case verbs, e.g. start_trial, sheet_shown,
 * session_returned_success, mandate_authorized. Always optional
 * metadata map for context (plan_type, language, coin_id, etc.).
 *
 * Never throws to the caller. Analytics must not gate user UX.
 */
object AutopayEventTracker {

    private const val TAG = "AutopayEvent"

    /**
     * Marketing's primary "start of funnel" event — user has actively
     * decided to begin the autopay trial. Fired the moment the user
     * taps the trial-offer CTA, BEFORE we call the Cashfree session
     * endpoint, so we capture intent even when session creation fails.
     */
    fun trackStartTrial(apiManager: ApiManager, planType: String?, language: String?) {
        track(apiManager, "start_trial", mapOf(
            "plan_type" to (planType ?: ""),
            "language" to (language ?: ""),
        ))
    }

    fun track(apiManager: ApiManager, eventType: String, metadata: Map<String, Any?> = emptyMap()) {
        val metaJson = try {
            org.json.JSONObject(metadata.filterValues { it != null }).toString()
        } catch (t: Throwable) {
            ""
        }
        apiManager.autopayEvent(eventType, metaJson, object : NetworkCallback<NotificationClickResponse> {
            override fun onResponse(
                call: Call<NotificationClickResponse>,
                response: Response<NotificationClickResponse>
            ) {
                Log.d(TAG, "logged $eventType: success=${response.body()?.success}")
            }
            override fun onFailure(call: Call<NotificationClickResponse>, t: Throwable) { /* silent */ }
            override fun onNoNetwork() { /* silent */ }
        })
    }
}
