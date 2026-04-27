package com.gmwapp.hima.utils

import android.util.Log
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.UpdateActiveStatusResponse
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bumps `users.datetime` via the `update_active_status` endpoint. The server
 * enforces a 5-minute window; we throttle to ~4 minutes on the client so that
 * back-to-back app opens / resumes don't waste a network round-trip.
 *
 * Call [reset] on logout / clear_data so the next login pings immediately.
 */
@Singleton
class ActiveStatusReporter @Inject constructor(private val apiManager: ApiManager) {

    private companion object {
        const val TAG = "ActiveStatus"
        const val MIN_INTERVAL_MS = 4 * 60 * 1000L
    }

    @Volatile
    private var lastSentAt: Long = 0L

    @Volatile
    private var inFlight: Boolean = false

    /**
     * Fire the heartbeat for the currently logged-in user. No-op if the user
     * isn't logged in, the throttle hasn't elapsed, or a request is already
     * in flight.
     *
     * @param force ignore the client-side throttle (e.g. just after login).
     */
    fun reportActive(force: Boolean = false) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id ?: 0
        if (userId <= 0) return

        val now = System.currentTimeMillis()
        if (!force && (inFlight || now - lastSentAt < MIN_INTERVAL_MS)) return
        inFlight = true

        apiManager.updateActiveStatus(userId, object : NetworkCallback<UpdateActiveStatusResponse> {
            override fun onResponse(
                call: Call<UpdateActiveStatusResponse>,
                response: Response<UpdateActiveStatusResponse>
            ) {
                inFlight = false
                if (response.isSuccessful && response.body()?.success == true) {
                    lastSentAt = now
                    Log.d(TAG, "OK userId=$userId updated=${response.body()?.data?.updated}")
                } else {
                    Log.w(
                        TAG,
                        "Failed userId=$userId code=${response.code()} body=${response.body()?.message}"
                    )
                }
            }

            override fun onFailure(call: Call<UpdateActiveStatusResponse>, t: Throwable) {
                inFlight = false
                Log.w(TAG, "Network failure userId=$userId msg=${t.message}")
            }

            override fun onNoNetwork() {
                inFlight = false
                Log.w(TAG, "No network userId=$userId")
            }
        })
    }

    /** Call on logout / clear_data so the next login fires the heartbeat immediately. */
    fun reset() {
        lastSentAt = 0L
        inFlight = false
    }
}
