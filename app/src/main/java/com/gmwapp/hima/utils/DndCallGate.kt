package com.gmwapp.hima.utils

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ToggleDndResponse
import retrofit2.Call
import retrofit2.Response

/**
 * Pre-flight gate any call-placing entry point can route through: if the local
 * user has DND active, show a blocking dialog that lets them turn DND off in
 * one tap and then continue with the call; otherwise pass straight through.
 *
 * DND is purely client-side suppression of incoming calls / chats — there is
 * no backend check, so [com.gmwapp.hima.BaseApplication.isDndActiveStatic] is
 * the only source of truth.
 */
object DndCallGate {

    private const val TAG = "DndCallGate"

    /**
     * @param activity     hosting activity; used to inflate + show the dialog
     *                     and to runOnUiThread once the toggle_dnd response
     *                     returns from Retrofit.
     * @param apiManager   already-injected ApiManager so we don't have to spin
     *                     up our own DI.
     * @param onProceed    called when DND is off (immediately) or after the
     *                     user disables it from the dialog (and the server
     *                     confirms — or the legacy 404 local fallback kicks in).
     * @param onCancel     called when the user explicitly taps Cancel.
     */
    fun gate(
        activity: Activity,
        apiManager: ApiManager,
        onProceed: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        if (!BaseApplication.isDndActiveStatic(userData)) {
            Log.d(TAG, "gate: DND not active — proceeding")
            onProceed()
            return
        }
        Log.d(TAG, "gate: DND active for userId=${userData?.id} — showing block dialog")
        showDialog(activity, apiManager, onProceed, onCancel)
    }

    private fun showDialog(
        activity: Activity,
        apiManager: ApiManager,
        onProceed: () -> Unit,
        onCancel: () -> Unit
    ) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_dnd_blocked, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.5f)

        val cta = view.findViewById<MaterialButton>(R.id.btn_dnd_blocked_cta)
        val cancel = view.findViewById<MaterialButton>(R.id.btn_dnd_blocked_cancel)
        val progress = view.findViewById<ProgressBar>(R.id.pb_dnd_blocked)

        cancel.setOnClickListener {
            Log.d(TAG, "dialog: cancel tapped")
            dialog.dismiss()
            onCancel()
        }

        cta.setOnClickListener {
            val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
            if (userId == null) {
                Log.w(TAG, "dialog: no userId — dismissing and cancelling")
                dialog.dismiss()
                onCancel()
                return@setOnClickListener
            }
            Log.d(TAG, "dialog: cta tapped — disabling DND for userId=$userId")
            cta.text = ""
            cta.isEnabled = false
            cancel.isEnabled = false
            progress.visibility = View.VISIBLE

            apiManager.toggleDnd(userId, 0, 0, object : NetworkCallback<ToggleDndResponse> {
                override fun onResponse(
                    call: Call<ToggleDndResponse>,
                    response: Response<ToggleDndResponse>
                ) {
                    if (activity.isFinishing || activity.isDestroyed) return
                    activity.runOnUiThread {
                        val body = response.body()
                        val ok = response.isSuccessful && body?.success == true
                        val httpNotFoundFallback = response.code() == 404
                        Log.d(
                            TAG,
                            "toggle_dnd resp http=${response.code()} success=${body?.success} fallback=$httpNotFoundFallback"
                        )
                        if (ok || httpNotFoundFallback) {
                            persistLocalOff(body)
                            dialog.dismiss()
                            onProceed()
                        } else {
                            restoreCta(cta, cancel, progress, activity)
                            Toast.makeText(
                                activity,
                                body?.message ?: activity.getString(R.string.dnd_blocked_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                override fun onFailure(call: Call<ToggleDndResponse>, t: Throwable) {
                    if (activity.isFinishing || activity.isDestroyed) return
                    Log.e(TAG, "toggle_dnd failure: ${t.message}", t)
                    activity.runOnUiThread {
                        restoreCta(cta, cancel, progress, activity)
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.dnd_blocked_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onNoNetwork() {
                    if (activity.isFinishing || activity.isDestroyed) return
                    Log.w(TAG, "toggle_dnd: no network")
                    activity.runOnUiThread {
                        restoreCta(cta, cancel, progress, activity)
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.dnd_err_no_network),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
        }

        dialog.show()
    }

    /**
     * Reflect the server's "DND off" state in local prefs so
     * [BaseApplication.isDndActiveStatic] stops blocking incoming pushes.
     * Honors the 404 fallback path used by [DndController]: even if the
     * server didn't echo data, we clear the local flag.
     */
    private fun persistLocalOff(body: ToggleDndResponse?) {
        val prefs = BaseApplication.getInstance()?.getPrefs() ?: return
        val current = prefs.getUserData() ?: return
        val newEnabled = body?.data?.dnd_enabled ?: 0
        val newUntil = body?.data?.dnd_until
        prefs.setUserData(
            current.copy(
                dnd_enabled = newEnabled,
                dnd_until = newUntil
            )
        )
        Log.d(TAG, "persistLocalOff: dnd_enabled=$newEnabled dnd_until=$newUntil")
    }

    private fun restoreCta(
        cta: MaterialButton,
        cancel: MaterialButton,
        progress: ProgressBar,
        activity: Activity
    ) {
        cta.text = activity.getString(R.string.dnd_blocked_cta)
        cta.isEnabled = true
        cancel.isEnabled = true
        progress.visibility = View.GONE
    }
}
