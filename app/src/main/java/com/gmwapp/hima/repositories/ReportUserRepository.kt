package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ReportReasonsResponse
import com.gmwapp.hima.retrofit.responses.ReportUserResponse
import javax.inject.Inject

class ReportUserRepository @Inject constructor(private val apiManager: ApiManager) {

    fun getReportReasons(userId: Int, callback: NetworkCallback<ReportReasonsResponse>) {
        apiManager.getReportReasons(userId, callback)
    }

    fun submitReport(
        userId: Int,
        reportUserId: Int,
        reasonId: Int,
        reasonText: String?,
        callback: NetworkCallback<ReportUserResponse>
    ) {
        apiManager.reportUser(userId, reportUserId, reasonId, reasonText ?: "", callback)
    }
}
