package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.NewLoginActivity
import com.gmwapp.hima.databinding.BottomSheetLogoutBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.workers.FcmTokenInvalidationWorker
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.onesignal.OneSignal
import java.util.concurrent.TimeUnit


class BottomSheetLogout : BottomSheetDialogFragment() {
    lateinit var binding: BottomSheetLogoutBinding
    private var hasHandledLogoutNavigation = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetLogoutBinding.inflate(layoutInflater)

        initUI()
        return binding.root
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = BottomSheetDialog(requireContext(), theme)

    private fun initUI() {
        binding.btnLogout.setOnSingleClickListener {
            if (hasHandledLogoutNavigation) return@setOnSingleClickListener
            hasHandledLogoutNavigation = true
            Log.d("LogoutBtn", "Clicked")

            val prefs = BaseApplication.getInstance()?.getPrefs()
            val userId = prefs?.getUserData()?.id
            val authToken = prefs?.getAuthenticationToken().orEmpty()

            // Capture userId + token BEFORE clearUserData wipes them, then hand off to
            // WorkManager so the server-side FCM invalidation completes even if the user is
            // offline / kills the app / never reopens it.
            userId?.let { scheduleFcmTokenInvalidation(requireContext().applicationContext, it, authToken) }

            // T32: route through the shared teardown so the order matches the 401 /
            // clear_data paths exactly. Local diagnostic dump still happens here.
            com.gmwapp.hima.utils.OneSignalDiag.dump(requireContext(), "before_user_logout")
            BaseApplication.getInstance()?.performGlobalSessionTeardown()
            // Do NOT optOut() — that persists enabled=false server-side for the whole
            // device and blocks the next user on this phone from receiving pushes.

            performLogoutAndNavigate()
        }

        binding.btnCancel.setOnSingleClickListener {
            dismiss()
        }
    }

    private fun scheduleFcmTokenInvalidation(context: Context, userId: Int, authToken: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<FcmTokenInvalidationWorker>()
            .setInputData(
                workDataOf(
                    FcmTokenInvalidationWorker.KEY_USER_ID to userId,
                    FcmTokenInvalidationWorker.KEY_AUTH_TOKEN to authToken
                )
            )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${FcmTokenInvalidationWorker.WORK_NAME_PREFIX}$userId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun performLogoutAndNavigate() {
        val prefs = BaseApplication.getInstance()?.getPrefs()
        prefs?.clearUserData()
        // Wipe user-scoped chat caches/prefs so the next account on this device
        // does not inherit the previous user's history, pins, or stacked
        // notification content via shared peer ids.
        runCatching {
            BaseApplication.getInstance()?.chatHistoryMemoryCache?.clearAll()
            val ctx = requireContext().applicationContext
            com.gmwapp.hima.utils.PinnedChatsPrefsHelper.clearAll(ctx)
            com.gmwapp.hima.utils.BlockedPeersPrefsHelper.clearAll(ctx)
            com.gmwapp.hima.utils.ChatNotificationStore.clearAll(ctx)
            // CHAT-108: clear persisted composer drafts on logout too.
            com.gmwapp.hima.utils.ChatDraftStore.clearAll(ctx)
        }
        val hostActivity = activity ?: return
        val intent = Intent(hostActivity, NewLoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        dismissAllowingStateLoss()
        startActivity(intent)
        hostActivity.overridePendingTransition(0, 0)
    }
}
