package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.fragment.app.activityViewModels
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.LoginActivity
import com.gmwapp.hima.activities.NewLoginActivity
import com.gmwapp.hima.databinding.BottomSheetLogoutBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.FcmTokenViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.messaging.FirebaseMessaging
import com.onesignal.OneSignal
import com.zoho.salesiqembed.ZohoSalesIQ

//import com.tencent.mmkv.MMKV
//import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService


class BottomSheetLogout : BottomSheetDialogFragment() {
    lateinit var binding: BottomSheetLogoutBinding
    private val fcmTokenViewModel: FcmTokenViewModel by activityViewModels()
    private var hasHandledLogoutNavigation = false
    private var isLogoutInProgress = false

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

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        observeTokenResponse()


        binding.btnLogout.setOnSingleClickListener {
            hasHandledLogoutNavigation = false
            isLogoutInProgress = true
            updateFcmToken(userData?.id)
            Log.d("LogoutBtn", "Clicked")

        }

        binding.btnCancel.setOnSingleClickListener {
            dismiss()
        }


    }


    fun updateFcmToken(userId: Int?) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            userId?.let { fcmTokenViewModel.sendToken(it, "0") }
        }
    }

    fun observeTokenResponse() {
        fcmTokenViewModel.tokenResponseLiveData.observe(viewLifecycleOwner) { response ->
            if (!isLogoutInProgress) return@observe
            Log.d("FCMToken", "$response")

            // Ignore null/intermediate emissions; only handle terminal states once.
            if (response == null) return@observe
            response?.let {
                if (it.success) {
                    Log.d("FCMToken", "Token updated successfully!")


//                    MMKV.defaultMMKV().remove("user_id");
//                    MMKV.defaultMMKV().remove("user_name");
                    OneSignal.User.removeTag("gender_language") // Clears the tag on logout
                    OneSignal.User.removeTag("gender") // Clears the tag on logout
                    OneSignal.User.removeTag("language") // Clears the tag on logout
                    OneSignal.User.removeTag("user_id") // Clears the tag on logout

                    OneSignal.logout()
                    OneSignal.User.pushSubscription.optOut()
                    // ZegoUIKitPrebuiltCallService.unInit()
                    performLogoutAndNavigate()


                } else {
                    Log.e("FCMToken", "Failed to save token")



//                    MMKV.defaultMMKV().remove("user_id");
//                    MMKV.defaultMMKV().remove("user_name");
                    OneSignal.User.removeTag("gender_language") // Clears the tag on logout
                    OneSignal.User.removeTag("gender") // Clears the tag on logout
                    OneSignal.User.removeTag("language") // Clears the tag on logout

                    OneSignal.logout()
                    OneSignal.User.pushSubscription.optOut()

                   // ZohoSalesIQ.unregisterVisitor(requireContext())


                    //ZegoUIKitPrebuiltCallService.unInit()
                    performLogoutAndNavigate()

                }
            }
        }

    }

    private fun performLogoutAndNavigate() {
        if (hasHandledLogoutNavigation) return
        hasHandledLogoutNavigation = true
        isLogoutInProgress = false

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