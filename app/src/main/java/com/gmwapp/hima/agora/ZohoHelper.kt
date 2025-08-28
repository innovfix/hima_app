package com.gmwapp.hima.agora


import android.util.Log
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.retrofit.responses.UserData
import com.gmwapp.hima.viewmodels.ZohoMailViewModel
import com.zoho.commons.LauncherModes
import com.zoho.commons.LauncherProperties
import com.zoho.salesiqembed.ZohoSalesIQ

object ZohoHelper {

    fun initZohoWithUser(userData: UserData?, zohoMailViewModel: ZohoMailViewModel) {
        val userLanguage = userData?.language ?: return

        zohoMailViewModel.fetchZohoMail(userLanguage) { email, department, appKey, accessKey ->
            if (!email.isNullOrEmpty()) {
                // Initialize Zoho
                BaseApplication.getInstance()?.initZoho(appKey, accessKey)

                val langCode = userLanguage.take(3)

                ZohoSalesIQ.registerVisitor("${userData.id}_${userData.language}")
                ZohoSalesIQ.Visitor.setName("${userData.name}($langCode)")
                ZohoSalesIQ.Visitor.setContactNumber("${userData.mobile}")
                ZohoSalesIQ.Chat.setOperatorEmail(email)

                if (!department.isNullOrEmpty()) {
                    ZohoSalesIQ.Chat.setDepartment(department)
                }

                val props = LauncherProperties(LauncherModes.FLOATING)
                props.setYFromBottom(180)
                props.setDirection(LauncherProperties.Horizontal.RIGHT)

                ZohoSalesIQ.setLauncherProperties(props)
                ZohoSalesIQ.showLauncher(true)

                Log.d("ZohoEmail", "$email, Department: $department")
            } else {
                Log.e("ZohoMailError", "Failed to fetch operator email")
            }
        }
    }
}

