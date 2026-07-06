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
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ToggleDndResponse
import com.gmwapp.hima.retrofit.responses.UserData
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
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
    private val cvDnd: View? = null,
    /**
     * Creator side only. When [requireCallsDisabledBeforeEnablingDnd] is true and audio/video
     * availability is still on, confirming the DND dialog turns them off through this view model
     * (fire-and-forget, viewModel-scoped so it survives the navigate-home below).
     */
    private val femaleUsersViewModel: FemaleUsersViewModel? = null,
    /**
     * Invoked after DND is successfully enabled (TC-DND-001 — creator is taken back to Home so
     * the "you're unavailable" state is obvious). No-op on disable.
     */
    private val onDndEnabled: (() -> Unit)? = null
) {
    // Authoritative audio/video availability from the latest server fetch (getUsers) —
    // the SAME value the Home toggles display. The DND confirm-guard must use this,
    // NOT the local-prefs copy: mergePreserveLocalIntent can leave the local
    // audio/video_status stale at 0 while the server still has her ON, which made the
    // guard skip the confirmation AND skip turning calls off (creator looked available
    // but DND silently ate her calls). null = not fetched yet.
    private var serverAudioStatus: Int? = null
    private var serverVideoStatus: Int? = null

    /**
     * Fed by the hosting fragment's getUsers observer so the DND guard reads the same
     * authoritative availability the Home screen shows.
     */
    fun updateServerAvailability(audioStatus: Int?, videoStatus: Int?) {
        serverAudioStatus = audioStatus
        serverVideoStatus = videoStatus
        Log.d(TAG, "updateServerAvailability audio=$audioStatus video=$videoStatus")
    }

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
                maybeEnableDnd(durationHours = DEFAULT_TAP_DURATION_HOURS)
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
        // DND is "on" only when GENUINELY active — the same expiry-aware truth every
        // enforcement path uses (isDndActiveStatic). Reading dnd_enabled alone left the
        // switch green on an already-past dnd_until ("DND until <past date>") while calls
        // and chats flowed through, because the flag can linger at 1 after its window ends.
        val active = BaseApplication.isDndActiveStatic(userData)
        // Self-heal a stale/expired flag locally so it can't keep re-displaying as on
        // (belt-and-braces alongside the server-side expiry normalization).
        if ((userData?.dnd_enabled ?: 0) == 1 && !active) {
            userData?.let {
                BaseApplication.getInstance()?.getPrefs()?.setUserData(
                    it.copy(dnd_enabled = 0, dnd_until = null)
                )
            }
        }
        Log.d(
            TAG,
            "refresh userId=${userData?.id} active=$active dnd_enabled=${userData?.dnd_enabled} " +
                "dnd_until=${userData?.dnd_until}"
        )
        switchDnd.isChecked = active
        tvDndStatus.text = if (active && !userData?.dnd_until.isNullOrBlank()) {
            fragment.getString(R.string.dnd_until_fmt, formatDndUntil(userData!!.dnd_until!!))
        } else {
            fragment.getString(R.string.dnd_title)
        }
    }

    /**
     * Entry point for every "enable DND" gesture (tap + each duration button).
     *
     * On the creator profile ([requireCallsDisabledBeforeEnablingDnd] = true) DND makes them
     * unavailable for calls, so if audio/video availability is still on we no longer block with a
     * toast — instead we ask for confirmation. Confirming turns those toggles off and enables DND;
     * cancelling reverts the switch. When the guard is off, or both toggles are already off, DND
     * enables straight away.
     */
    private fun maybeEnableDnd(durationHours: Int) {
        if (!requireCallsDisabledBeforeEnablingDnd) {
            callToggleDndApi(wantedEnabled = 1, durationHours = durationHours)
            return
        }
        val ud = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        // Use the AUTHORITATIVE server availability (the value the Home toggles show),
        // not the local-prefs copy which mergePreserveLocalIntent can leave stale at 0
        // while the server still has her ON. That staleness made this guard wrongly
        // take the "already off" branch — skipping the confirmation AND leaving her
        // available server-side while DND ate her calls (the reported bug). When the
        // fresh server value isn't known yet (null), assume she MIGHT be available so
        // DND can never leave her as an available black-hole that still gets calls.
        val audioOn = (serverAudioStatus ?: 1) == 1
        val videoOn = (serverVideoStatus ?: 1) == 1
        Log.d(
            TAG,
            "maybeEnable guard userId=${ud?.id} audioOn=$audioOn videoOn=$videoOn " +
                "serverAudio=$serverAudioStatus serverVideo=$serverVideoStatus " +
                "localAudio=${ud?.audio_status} localVideo=${ud?.video_status}"
        )
        if (!audioOn && !videoOn) {
            callToggleDndApi(wantedEnabled = 1, durationHours = durationHours)
            return
        }
        if (!fragment.isAdded) return
        // B16 — custom confirm CARD (branded), replacing the plain system dialog:
        // moon icon in a pink circle, title/body, a pink "no calls until DND ends"
        // warning, and Cancel / Turn-on-DND buttons. Matches B16_DND_CONFIRM_CARD.
        val view = fragment.layoutInflater.inflate(R.layout.dialog_dnd_confirm, null)
        val dialog = AlertDialog.Builder(fragment.requireContext())
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.5f)
        view.findViewById<MaterialButton>(R.id.btn_dnd_confirm_cancel).setOnClickListener {
            Log.d(TAG, "confirm cancelled — revert switch")
            dialog.dismiss()
            switchDnd.isChecked = false
        }
        view.findViewById<MaterialButton>(R.id.btn_dnd_confirm_positive).setOnClickListener {
            Log.d(TAG, "confirm accepted — disable calls + enable dnd")
            dialog.dismiss()
            disableCallsThenEnableDnd(durationHours)
        }
        dialog.setOnCancelListener {
            // dismissed via back / outside tap — treat as cancel
            switchDnd.isChecked = false
        }
        dialog.show()
    }

    /**
     * Confirm path: switch audio + video availability off (fire-and-forget, viewModel-scoped so
     * it outlives the navigate-home), mirror that into local prefs so Home repaints the toggles
     * off immediately, then enable DND.
     */
    private fun disableCallsThenEnableDnd(durationHours: Int) {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
        if (userId == null) {
            switchDnd.isChecked = false
            return
        }
        femaleUsersViewModel?.let { vm ->
            Log.d(TAG, "disableCalls userId=$userId audio->0 video->0")
            vm.updateCallStatus(userId, DConstants.AUDIO, 0)
            vm.updateCallStatus(userId, DConstants.VIDEO, 0)
        }
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.let { cur ->
            BaseApplication.getInstance()?.getPrefs()?.setUserData(
                cur.copy(audio_status = 0, video_status = 0)
            )
        }
        callToggleDndApi(wantedEnabled = 1, durationHours = durationHours)
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
            maybeEnableDnd(durationHours = 1)
        }
        view.findViewById<MaterialButton>(R.id.btn_dnd_2h).setOnClickListener {
            Log.d(TAG, "durationPicker selected=2h")
            dialog.dismiss()
            maybeEnableDnd(durationHours = 2)
        }
        view.findViewById<MaterialButton>(R.id.btn_dnd_4h).setOnClickListener {
            Log.d(TAG, "durationPicker selected=4h")
            dialog.dismiss()
            maybeEnableDnd(durationHours = 4)
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
                                    if (wantedEnabled == 1) onDndEnabled?.invoke()
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
                                if (wantedEnabled == 1) onDndEnabled?.invoke()
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
