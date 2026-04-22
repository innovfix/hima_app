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

            OneSignal.User.removeTag("gender_language")
            OneSignal.User.removeTag("gender")
            OneSignal.User.removeTag("language")
            OneSignal.User.removeTag("user_id")
            OneSignal.logout()
            OneSignal.User.pushSubscription.optOut()

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
