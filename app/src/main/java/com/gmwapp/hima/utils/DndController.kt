package com.gmwapp.hima.utils

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ToggleDndResponse
import com.gmwapp.hima.retrofit.responses.UserData
import com.gmwapp.hima.viewmodels.ProfileViewModel
import retrofit2.Call
import retrofit2.Response

/**
 * Do Not Disturb toggle + duration picker shared by [com.gmwapp.hima.fragments.ProfileFragment]
 * and [com.gmwapp.hima.fragments.ProfileFemaleFragment].
 *
 * Tap on the switch toggles DND on for [DEFAULT_TAP_DURATION_HOURS] (or off).
 * Long-press on the switch — or on the optional [cvDnd] card row — opens the duration picker.
 */
class DndController(
    private val fragment: Fragment,
    private val switchDnd: SwitchCompat,
    private val tvDndStatus: TextView,
    private val profileViewModel: ProfileViewModel,
    /**
     * When true (creator / female profile), enabling DND is blocked while audio or video call
     * availability is still on — user must turn those off on the home screen first.
     */
    private val requireCallsDisabledBeforeEnablingDnd: Boolean = false,
    /**
     * Optional whole-row container; when supplied, long-press on the row also opens the
     * duration picker so the gesture isn't limited to the small switch hit area.
     */
    private val cvDnd: View? = null
) {
    fun attach() {
        Log.d(
            TAG,
            "attach fragment=${fragment.javaClass.simpleName} " +
                "requireCallsDisabled=$requireCallsDisabledBeforeEnablingDnd hasCard=${cvDnd != null}"
        )
        switchDnd.setOnClickListener {
            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            Log.d(
                TAG,
                "click checked=${switchDnd.isChecked} userId=${userData?.id} " +
                    "gender=${userData?.gender} audio=${userData?.audio_status} video=${userData?.video_status}"
            )
            if (switchDnd.isChecked) {
                if (!canEnableDndOrToast()) {
                    Log.d(TAG, "click enable blocked by canEnableDndOrToast")
                    switchDnd.isChecked = false
                    return@setOnClickListener
                }
                callToggleDndApi(wantedEnabled = 1, durationHours = DEFAULT_TAP_DURATION_HOURS)
            } else {
                callToggleDndApi(wantedEnabled = 0, durationHours = 0)
            }
        }

        val longPressHandler = View.OnLongClickListener {
            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            Log.d(
                TAG,
                "longPress userId=${userData?.id} currentEnabled=${userData?.dnd_enabled} " +
                    "currentUntil=${userData?.dnd_until}"
            )
            if (!canEnableDndOrToast()) return@OnLongClickListener true
            showDndDurationPicker()
            true
        }
        switchDnd.setOnLongClickListener(longPressHandler)
        cvDnd?.setOnLongClickListener(longPressHandler)

        refresh()
    }

    fun refresh() {
        if (!fragment.isAdded) return
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val enabled = (userData?.dnd_enabled ?: 0) == 1
        Log.d(
            TAG,
            "refresh userId=${userData?.id} enabled=$enabled dnd_enabled=${userData?.dnd_enabled} " +
                "dnd_until=${userData?.dnd_until}"
        )
        switchDnd.isChecked = enabled
        tvDndStatus.text = if (enabled && !userData?.dnd_until.isNullOrBlank()) {
            fragment.getString(R.string.dnd_until_fmt, formatDndUntil(userData!!.dnd_until!!))
        } else {
            fragment.getString(R.string.dnd_title)
        }
    }

    /**
     * Female/creator profiles must turn off audio + video availability before enabling DND.
     * Returns true when DND can be enabled; otherwise toasts the explainer and returns false.
     * Tap and long-press both go through this so the rule stays consistent.
     */
    private fun canEnableDndOrToast(): Boolean {
        if (!requireCallsDisabledBeforeEnablingDnd) {
            Log.d(TAG, "canEnable allowed: no call-availability guard")
            return true
        }
        val ud = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val audioOn = (ud?.audio_status ?: 0) == 1
        val videoOn = (ud?.video_status ?: 0) == 1
        Log.d(
            TAG,
            "canEnable guard userId=${ud?.id} audioOn=$audioOn videoOn=$videoOn " +
                "audio=${ud?.audio_status} video=${ud?.video_status}"
        )
        if (!audioOn && !videoOn) return true
        Toast.makeText(
            fragment.requireContext(),
            fragment.getString(R.string.dnd_requires_calls_off),
            Toast.LENGTH_LONG
        ).show()
        Log.d(TAG, "canEnable blocked: audio/video availability still enabled")
        return false
    }

    private fun formatDndUntil(iso: String): String {
        return try {
            val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            val date = sdfIn.parse(iso) ?: return iso
            val today = java.util.Calendar.getInstance()
            val then = java.util.Calendar.getInstance().apply { time = date }
            val sameDay =
                today.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
                    today.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
            val pattern = if (sameDay) "h:mm a" else "MMM d, h:mm a"
            java.text.SimpleDateFormat(pattern, java.util.Locale.US).format(date)
        } catch (e: Exception) {
            iso
        }
    }

    private fun showDndDurationPicker() {
        Log.d(TAG, "showDurationPicker")
        val view = fragment.layoutInflater.inflate(R.layout.dialog_dnd_duration, null)
        val dialog = AlertDialog.Builder(fragment.requireContext(), R.style.AlertDialogTransparent)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<MaterialButton>(R.id.btn_dnd_1h).setOnClickListener {
            Log.d(TAG, "durationPicker selected=1h")
            dialog.dismiss()
            callToggleDndApi(wantedEnabled = 1, durationHours = 1)
        }
        view.findViewById<MaterialButton>(R.id.btn_dnd_2h).setOnClickListener {
            Log.d(TAG, "durationPicker selected=2h")
            dialog.dismiss()
            callToggleDndApi(wantedEnabled = 1, durationHours = 2)
        }
        view.findViewById<MaterialButton>(R.id.btn_dnd_4h).setOnClickListener {
            Log.d(TAG, "durationPicker selected=4h")
            dialog.dismiss()
            callToggleDndApi(wantedEnabled = 1, durationHours = 4)
        }
        view.findViewById<MaterialButton>(R.id.btn_dnd_cancel).setOnClickListener {
            Log.d(TAG, "durationPicker cancelled")
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun callToggleDndApi(wantedEnabled: Int, durationHours: Int) {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        Log.d(TAG, "req.start userId=$userId wantedEnabled=$wantedEnabled durationHours=$durationHours")
        switchDnd.isEnabled = false

        profileViewModel.toggleDnd(
            userId,
            wantedEnabled,
            durationHours,
            object : NetworkCallback<ToggleDndResponse> {
                override fun onResponse(
                    call: Call<ToggleDndResponse>,
                    response: Response<ToggleDndResponse>
                ) {
                    val body = response.body()
                    val err = runCatching { response.errorBody()?.string() }.getOrNull()
                    Log.d(
                        TAG,
                        "resp url=${call.request().url} http=${response.code()} isSuccessful=${response.isSuccessful} " +
                            "success=${body?.success} message=${body?.message} data=${body?.data} err=$err"
                    )
                    if (!fragment.isAdded || fragment.activity == null) return
                    fragment.requireActivity().runOnUiThread {
                        when {
                            !response.isSuccessful -> {
                                // Backend route `toggle_dnd` is not deployed on some hosts (HTTP 404).
                                // Apply matching DND schedule locally so FCM/OneSignal suppression still works.
                                if (response.code() == 404 && applyLocalDndFallback(wantedEnabled, durationHours)) {
                                    Log.d(TAG, "resp.404 applied local fallback")
                                    refresh()
                                    profileViewModel.getUsers(userId)
                                    Toast.makeText(
                                        fragment.requireContext(),
                                        fragment.getString(R.string.dnd_updated),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    toastAndRollback(
                                        fragment.getString(R.string.dnd_err_http, response.code())
                                    )
                                }
                            }
                            body == null -> {
                                toastAndRollback(fragment.getString(R.string.dnd_err_empty_body))
                            }
                            body.success != true -> {
                                toastAndRollback(
                                    body.message ?: fragment.getString(R.string.dnd_err_server)
                                )
                            }
                            body.data == null -> {
                                toastAndRollback(fragment.getString(R.string.dnd_err_missing_data))
                            }
                            wantedEnabled == 1 && (body.data!!.dnd_enabled ?: 0) != 1 -> {
                                Toast.makeText(
                                    fragment.requireContext(),
                                    fragment.getString(R.string.dnd_err_not_activated),
                                    Toast.LENGTH_LONG
                                ).show()
                                refresh()
                            }
                            else -> {
                                val current = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                                if (current != null) {
                                    val d = body.data!!
                                    Log.d(
                                        TAG,
                                        "persist server data userId=$userId dnd_enabled=${d.dnd_enabled} dnd_until=${d.dnd_until}"
                                    )
                                    BaseApplication.getInstance()?.getPrefs()?.setUserData(
                                        current.copy(
                                            dnd_enabled = d.dnd_enabled,
                                            dnd_until = d.dnd_until
                                        )
                                    )
                                }
                                refresh()
                                profileViewModel.getUsers(userId)
                                Toast.makeText(
                                    fragment.requireContext(),
                                    body.message ?: fragment.getString(R.string.dnd_updated),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        switchDnd.isEnabled = true
                    }
                }

                override fun onFailure(call: Call<ToggleDndResponse>, t: Throwable) {
                    Log.e(TAG, "failure url=${call.request().url} message=${t.message}", t)
                    if (!fragment.isAdded || fragment.activity == null) return
                    fragment.requireActivity().runOnUiThread {
                        toastAndRollback(
                            fragment.getString(R.string.dnd_err_generic, t.message ?: "")
                        )
                        switchDnd.isEnabled = true
                    }
                }

                override fun onNoNetwork() {
                    Log.w(TAG, "noNetwork userId=$userId wantedEnabled=$wantedEnabled durationHours=$durationHours")
                    if (!fragment.isAdded || fragment.activity == null) return
                    fragment.requireActivity().runOnUiThread {
                        toastAndRollback(fragment.getString(R.string.dnd_err_no_network))
                        switchDnd.isEnabled = true
                    }
                }
            }
        )
    }

    private fun toastAndRollback(message: String) {
        Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_LONG).show()
        refresh()
    }

    /**
     * Writes [UserData.dnd_enabled] / [UserData.dnd_until] locally using the same ISO format as the server
     * so [BaseApplication.isDndActiveStatic] keeps working when the API returns 404.
     */
    private fun applyLocalDndFallback(wantedEnabled: Int, durationHours: Int): Boolean {
        val current = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return false
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getDefault()
        val untilStr: String? = if (wantedEnabled == 1) {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.HOUR_OF_DAY, durationHours)
            sdf.format(cal.time)
        } else {
            null
        }
        val updated: UserData = current.copy(
            dnd_enabled = if (wantedEnabled == 1) 1 else 0,
            dnd_until = untilStr
        )
        BaseApplication.getInstance()?.getPrefs()?.setUserData(updated)
        Log.d(
            TAG,
            "fallback.persist userId=${current.id} wantedEnabled=$wantedEnabled " +
                "durationHours=$durationHours until=$untilStr"
        )
        return true
    }

    companion object {
        private const val TAG = "DND_TOGGLE"
        private const val DEFAULT_TAP_DURATION_HOURS = 1
    }
}
