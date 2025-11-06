package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CategoriesResponse
import com.gmwapp.hima.retrofit.responses.SettingsResponse
import com.gmwapp.hima.retrofit.responses.SubmitTicketResponse
import com.gmwapp.hima.retrofit.responses.SubqueriesResponse
import com.gmwapp.hima.retrofit.responses.TicketsListResponse
import com.gmwapp.hima.retrofit.responses.CallRejectCountResponse
import javax.inject.Inject

class AccountRepositories @Inject constructor(private val apiManager: ApiManager) {
  fun getSettings(callback: NetworkCallback<SettingsResponse>) {
        apiManager.getSettings(callback)
    }

    fun getCategoriesList(userId: Int, callback: NetworkCallback<CategoriesResponse>) {
        apiManager.getCategoriesList(userId, callback)
    }

    fun getSubqueriesList(userId: Int, categoryId: Int, callback: NetworkCallback<SubqueriesResponse>) {
        apiManager.getSubqueriesList(userId, categoryId, callback)
    }

    fun submitTicket(userId: Int, message: String, screenshots: List<okhttp3.MultipartBody.Part>, callback: NetworkCallback<SubmitTicketResponse>) {
        apiManager.submitTicket(userId, message, screenshots, callback)
    }

    fun getTicketsList(userId: Int, callback: NetworkCallback<TicketsListResponse>) {
        apiManager.getTicketsList(userId, callback)
    }

    fun callRejectCount(maleUserId: Int, femaleUserId: Int, callback: NetworkCallback<CallRejectCountResponse>) {
        apiManager.callRejectCount(maleUserId, femaleUserId, callback)
    }
}