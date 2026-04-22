package com.gmwapp.hima.utils

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
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
 */
class DndController(
    private val fragment: Fragment,
    private val switchDnd: SwitchCompat,
    private val tvDndStatus: TextView,
    private val profileViewModel: ProfileViewModel
) {
    fun attach() {
        switchDnd.setOnClickListener {
            if (switchDnd.isChecked) {
                switchDnd.isChecked = false
                showDndDurationPicker()
            } else {
                callToggleDndApi(wantedEnabled = 0, durationHours = 0)
            }
        }
        refresh()
    }

    fun refresh() {
        if (!fragment.isAdded) return
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val enabled = (userData?.dnd_enabled ?: 0) == 1
        switchDnd.isChecked = enabled
        tvDndStatus.text = if (enabled && !userData?.dnd_until.isNullOrBlank()) {
            fragment.getString(R.string.dnd_until_fmt, formatDndUntil(userData!!.dnd_until!!))
        } else {
            fragment.getString(R.string.dnd_title)
        }
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
        val view = fragment.layoutInflater.inflate(R.layout.dialog_dnd_duration, null)
        val dialog = AlertDialog.Builder(fragment.requireContext(), R.style.AlertDialogTransparent)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<MaterialButton>(R.id.btn_dnd_1h).setOnClickListener {
            dialog.dismiss()
            callToggleDndApi(wantedEnabled = 1, durationHours = 1)
        }
        view.findViewById<MaterialButton>(R.id.btn_dnd_3h).setOnClickListener {
            dialog.dismiss()
            callToggleDndApi(wantedEnabled = 1, durationHours = 3)
        }
        view.findViewById<MaterialButton>(R.id.btn_dnd_24h).setOnClickListener {
            dialog.dismiss()
            callToggleDndApi(wantedEnabled = 1, durationHours = 24)
        }
        view.findViewById<MaterialButton>(R.id.btn_dnd_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun callToggleDndApi(wantedEnabled: Int, durationHours: Int) {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        Log.d(TAG, "req userId=$userId wantedEnabled=$wantedEnabled durationHours=$durationHours")
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
                        "http=${response.code()} isSuccessful=${response.isSuccessful} " +
                            "success=${body?.success} message=${body?.message} data=${body?.data} err=$err"
                    )
                    if (!fragment.isAdded || fragment.activity == null) return
                    fragment.requireActivity().runOnUiThread {
                        when {
                            !response.isSuccessful -> {
                                // Backend route `toggle_dnd` is not deployed on some hosts (HTTP 404).
                                // Apply matching DND schedule locally so FCM/OneSignal suppression still works.
                                if (response.code() == 404 && applyLocalDndFallback(wantedEnabled, durationHours)) {
                                    refresh()
                                    profileViewModel.getUsers(userId)
                                    Toast.makeText(
                                        fragment.requireContext(),
                                        fragment.getString(R.string.dnd_fallback_local),
                                        Toast.LENGTH_LONG
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
                    Log.e(TAG, "onFailure: ${t.message}", t)
                    if (!fragment.isAdded || fragment.activity == null) return
                    fragment.requireActivity().runOnUiThread {
                        toastAndRollback(
                            fragment.getString(R.string.dnd_err_generic, t.message ?: "")
                        )
                        switchDnd.isEnabled = true
                    }
                }

                override fun onNoNetwork() {
                    Log.w(TAG, "onNoNetwork")
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
        return true
    }

    companion object {
        private const val TAG = "DND_TOGGLE"
    }
}
