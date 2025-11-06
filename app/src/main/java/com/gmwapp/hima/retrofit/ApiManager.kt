package com.gmwapp.hima.retrofit

import com.gmwapp.hima.activities.RetrofitClient
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AddCoinsResponse
import com.gmwapp.hima.retrofit.responses.AddPointsResponse
import com.gmwapp.hima.retrofit.responses.AppUpdateResponse
import com.gmwapp.hima.retrofit.responses.AvatarsListResponse
import com.gmwapp.hima.retrofit.responses.BadgeResponse
import com.gmwapp.hima.retrofit.responses.BankUpdateResponse
import com.gmwapp.hima.retrofit.responses.CategoriesResponse
import com.gmwapp.hima.retrofit.responses.SubqueriesResponse
import com.gmwapp.hima.retrofit.responses.SubmitTicketResponse
import com.gmwapp.hima.retrofit.responses.TicketsListResponse
import com.gmwapp.hima.retrofit.responses.BlockUserResponse
import com.gmwapp.hima.retrofit.responses.CallFemaleUserResponse
import com.gmwapp.hima.retrofit.responses.CallMaleUserResponse
import com.gmwapp.hima.retrofit.responses.CallsListResponse
import com.gmwapp.hima.retrofit.responses.CoinsResponse
import com.gmwapp.hima.retrofit.responses.CouponsResponse
import com.gmwapp.hima.retrofit.responses.CreateCashfreeOrderResponse
import com.gmwapp.hima.retrofit.responses.DeleteUserResponse
import com.gmwapp.hima.retrofit.responses.EarningsResponse
import com.gmwapp.hima.retrofit.responses.ExplanationVideoResponse
import com.gmwapp.hima.retrofit.responses.FcmNotificationResponse
import com.gmwapp.hima.retrofit.responses.FcmTokenResponse
import com.gmwapp.hima.retrofit.responses.FemaleCallAttendResponse
import com.gmwapp.hima.retrofit.responses.FemaleUsersResponse
import com.gmwapp.hima.retrofit.responses.FirstCallUpdateResponse
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.retrofit.responses.GiftImageResponse
import com.gmwapp.hima.retrofit.responses.IndividualAppUpdateResponse
import com.gmwapp.hima.retrofit.responses.LoginResponse
import com.gmwapp.hima.retrofit.responses.MessageNotificationResponse
import com.gmwapp.hima.retrofit.responses.OfferResponse
import com.gmwapp.hima.retrofit.responses.PanCardResponse
import com.gmwapp.hima.retrofit.responses.RandomUsersResponse
import com.gmwapp.hima.retrofit.responses.RatingResponse
import com.gmwapp.hima.retrofit.responses.ReferralCodeResponse
import com.gmwapp.hima.retrofit.responses.RegisterResponse
import com.gmwapp.hima.retrofit.responses.ReportsResponse
import com.gmwapp.hima.retrofit.responses.FriendRequestResponse
import com.gmwapp.hima.retrofit.responses.MyFriendRequestsResponse
import com.gmwapp.hima.retrofit.responses.ReceivedFriendRequestsResponse
import com.gmwapp.hima.retrofit.responses.FriendListResponse
import com.gmwapp.hima.retrofit.responses.SendGiftResponse
import com.gmwapp.hima.retrofit.responses.SendOTPResponse
import com.gmwapp.hima.retrofit.responses.SettingsResponse
import com.gmwapp.hima.retrofit.responses.SpeechTextResponse
import com.gmwapp.hima.retrofit.responses.TransactionChargesResponse
import com.gmwapp.hima.retrofit.responses.TransactionsResponse
import com.gmwapp.hima.retrofit.responses.UpdateCallStatusResponse
import com.gmwapp.hima.retrofit.responses.UpdateConnectedCallResponse
import com.gmwapp.hima.retrofit.responses.UpdateProfileResponse
import com.gmwapp.hima.retrofit.responses.UpiPaymentResponse
import com.gmwapp.hima.retrofit.responses.UpiUpdateResponse
import com.gmwapp.hima.retrofit.responses.UpiWithdrawResponse
import com.gmwapp.hima.retrofit.responses.UserAvatarResponse
import com.gmwapp.hima.retrofit.responses.UserCallDurationResponse
import com.gmwapp.hima.retrofit.responses.UserValidationResponse
import com.gmwapp.hima.retrofit.responses.VoiceUpdateResponse
import com.gmwapp.hima.retrofit.responses.WhatsappLinkResponse
import com.gmwapp.hima.retrofit.responses.WithdrawResponse
import com.gmwapp.hima.retrofit.responses.ZohoMailResponse
import com.gmwapp.hima.retrofit.responses.DefaultCouponResponse
import com.gmwapp.hima.utils.Helper
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import javax.inject.Inject

class ApiManager @Inject constructor(private val retrofit: Retrofit) {
    private fun getApiInterface(): ApiInterface {
        return retrofit.create(ApiInterface::class.java)
    }


    fun login(
        mobile: String,  code: String,  code_verifier: String, callback: NetworkCallback<LoginResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<LoginResponse> = getApiInterface().login(mobile,code, code_verifier)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun appUpdate(
         callback: NetworkCallback<AppUpdateResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<AppUpdateResponse> = getApiInterface().appUpdate(1)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun individualAppUpdate(
        userId: Int,
        currentVersion: String,
        callback: NetworkCallback<IndividualAppUpdateResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<IndividualAppUpdateResponse> = getApiInterface().individualAppUpdate(userId, currentVersion)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }



    fun sendOTP(
        mobile: String, countryCode: Int, otp: Int, callback: NetworkCallback<SendOTPResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<SendOTPResponse> = getApiInterface().sendOTP(mobile, countryCode, otp)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getAvatarsList(
        gender: String, callback: NetworkCallback<AvatarsListResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<AvatarsListResponse> = getApiInterface().getAvatarsList(gender)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getCallsList(
        userId: Int, gender: String, limit: Int, currentOffset: Int, type: String, callback: NetworkCallback<CallsListResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CallsListResponse> = getApiInterface().getCallsList(userId, gender, currentOffset, limit, type)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun register(
        mobile: String,
        language: String,
        avatarId: Int,
        gender: String,
        savedReferCode: String,
        callback: NetworkCallback<RegisterResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<RegisterResponse> =
                getApiInterface().register(mobile, language, avatarId, gender,savedReferCode)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getUser(
        userId: Int, callback: NetworkCallback<RegisterResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<RegisterResponse> = getApiInterface().getUser(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getUserStatus(
        userId: String, callback: NetworkCallback<RegisterResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<RegisterResponse> = getApiInterface().getUserStatus(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getUserSync(
        userId: Int
    ): Response<RegisterResponse> {
            val apiCall: Call<RegisterResponse> = getApiInterface().getUserSync(userId)
            return apiCall.execute()
    }

    fun registerFemale(
        mobile: String,
        language: String,
        avatarId: Int,
        gender: String,
        age: String,
        interests: String,
        describe_yourself: String,
        savedReferCode:String,
        callback: NetworkCallback<RegisterResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<RegisterResponse> = getApiInterface().registerFemale(
                mobile, language, avatarId, gender, age, interests, describe_yourself, savedReferCode
            )
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getTransactions(
        userId: Int, offset: Int,limit: Int, callback: NetworkCallback<TransactionsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<TransactionsResponse> = getApiInterface().getTransactions(userId,offset,limit)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getFemaleUsers(
        userId: Int, callback: NetworkCallback<FemaleUsersResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FemaleUsersResponse> = getApiInterface().getFemaleUsers(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getRandomUser(
        userId: Int,callType: String, callback: NetworkCallback<RandomUsersResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<RandomUsersResponse> = getApiInterface().getRandomUser(userId, callType)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }


    fun getCalldetails(
        userId: Int, callback: NetworkCallback<ReportsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<ReportsResponse> = getApiInterface().getReports(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun updateCallStatus(
        userId: Int,
        callType: String,
        status: Int,
        callback: NetworkCallback<UpdateCallStatusResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<UpdateCallStatusResponse> =
                getApiInterface().updateCallStatus(userId, callType, status)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun femaleCallAttend(
        userId: Int,
        callId: Int,
        startedTime: String,
        callback: NetworkCallback<FemaleCallAttendResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FemaleCallAttendResponse> =
                getApiInterface().femaleCallAttend(userId, callId, startedTime)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun callFemaleUser(
        userId: Int,
        callUserId: Int,
        callType: String,
        call_switch: Int,
        callback: NetworkCallback<CallFemaleUserResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CallFemaleUserResponse> =
                getApiInterface().callFemaleUser(userId, callUserId, callType,call_switch)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun callMaleUser(
        userId: Int,
        callUserId: Int,
        callType: String,
        call_switch: Int,
        callback: NetworkCallback<CallMaleUserResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CallMaleUserResponse> =
                getApiInterface().callMaleUser(userId, callUserId, callType, call_switch)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    suspend fun updateConnectedCall(
        userId: Int,
        callId: Int,
        startedTime: String,
        endedTime: String,
    ) : Response<UpdateConnectedCallResponse>{
            return getApiInterface().updateConnectedCall(userId, callId, startedTime, endedTime)
    }

    suspend fun individualUpdateConnectedCall(
        userId: Int,
        callId: Int,
        startedTime: String,
        endedTime: String,
    ) : Response<UpdateConnectedCallResponse>{
            return getApiInterface().individualUpdateConnectedCall(userId, callId, startedTime, endedTime)
    }

    fun getEarnings(
        userId: Int, callback: NetworkCallback<EarningsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<EarningsResponse> = getApiInterface().getEarnings(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun updateBank(
        userId: Int,
        bank: String,
        accountNum: String,
        branch: String,
        ifsc: String,
        holderName: String,
        callback: NetworkCallback<BankUpdateResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<BankUpdateResponse> = getApiInterface().updateBank(userId, bank, accountNum, branch, ifsc, holderName)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }


    fun updateRating(
        userId: Int,
        call_user_id: Int,
        ratings: String,
        title: String,
        description: String,
        callback: NetworkCallback<RatingResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<RatingResponse> = getApiInterface().updateRatings(userId,call_user_id,ratings,title,description)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getUserCallDuration(
        callId: Int,
        userId: Int,
        callback: NetworkCallback<UserCallDurationResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<UserCallDurationResponse> = getApiInterface().getUserCallDuration(callId, userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun updateUpi(
        userId: Int,
        upiId: String,
        callback: NetworkCallback<UpiUpdateResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<UpiUpdateResponse> = getApiInterface().updateUpi(userId,upiId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun addPoints(
        buyerName: String,
        amount: String, email: String, phone: String,
        purpose: String,
        callback: NetworkCallback<AddPointsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<AddPointsResponse> = getApiInterface().addPoints(buyerName, amount, email, phone, purpose)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }


    fun addWithdrawal(
        userId: Int,
        amount: Int,
        paymentMethod: String,
        callback: NetworkCallback<WithdrawResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<WithdrawResponse> = getApiInterface().addWithdrawal(userId,amount,paymentMethod)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun addUpiWithdrawal(
        userId: Int,
        amount: Int,
        paymentMethod: String,
        callback: NetworkCallback<UpiWithdrawResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<UpiWithdrawResponse> =
                getApiInterface().addUpiWithdrawal(userId, amount, paymentMethod)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getTransactionCharges(callback: NetworkCallback<TransactionChargesResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<TransactionChargesResponse> =
                getApiInterface().getTransactionCharges()
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }



    fun getOffer(
        userId: Int,
        callback: NetworkCallback<OfferResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<OfferResponse> = getApiInterface().getOffer(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getExplanationvideos(
        language: String,
        callback: NetworkCallback<ExplanationVideoResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<ExplanationVideoResponse> = getApiInterface().getExplanationVideos(language)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getWhatsappLink(
        language: String,
        callback: NetworkCallback<WhatsappLinkResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<WhatsappLinkResponse> = getApiInterface().getWhatsappLink(language)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getGiftImages(callback: NetworkCallback<GiftImageResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<GiftImageResponse> = getApiInterface().getGiftImages()
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun sendGift(
        userId: Int,
        receiverId: Int,
        giftId: Int,
        callback: NetworkCallback<SendGiftResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<SendGiftResponse> = getApiInterface().sendGift(userId, receiverId, giftId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }





    fun updateProfile(
        userId: Int,
        avatarId: Int,
        name: String,
        interests: String?,
        callback: NetworkCallback<UpdateProfileResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<UpdateProfileResponse> =
                getApiInterface().updateProfile(userId, avatarId, name, interests)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getCoins(
        userId: Int, callback: NetworkCallback<CoinsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CoinsResponse> = getApiInterface().getCoins(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun addCoins(
        userId: Int,
        coinsId: String,
        status: Int,
        orderId: String,
        massage: String,
        callback: NetworkCallback<AddCoinsResponse>
    ) {

        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<AddCoinsResponse> = getApiInterface().addCoins(userId, coinsId, status, orderId, massage)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun add_coins_cashfree(
        userId: Int,
        coinsId: String,
        status: Int,
        orderId: String,
        massage: String,
        callback: NetworkCallback<AddCoinsResponse>
    ) {

        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<AddCoinsResponse> = getApiInterface().add_coins_cashfree(userId, coinsId, status, orderId, massage)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun addCoinsGpay(
        userId: Int,
        coinsId: String,
        status: Int,
        orderId: String,
        massage: String,
        callback: NetworkCallback<AddCoinsResponse>
    ) {

        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<AddCoinsResponse> = getApiInterface().addCoinsGpay(userId, coinsId, status, orderId, massage)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun tryCoins(
        userId: Int,
        coinsId: Int,
        status: Int,
        orderId: Int,
        massage: String,
        callback: NetworkCallback<AddCoinsResponse>
    ) {

        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<AddCoinsResponse> = getApiInterface().tryCoins(userId, coinsId, status, orderId, massage,)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun deleteUsers(
        userId: Int, deleteReason: String, callback: NetworkCallback<DeleteUserResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<DeleteUserResponse> =
                getApiInterface().deleteUsers(userId, deleteReason)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun userValidation(
        userId: Int, name: String, callback: NetworkCallback<UserValidationResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<UserValidationResponse> =
                getApiInterface().userValidation(userId, name)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getSettings(
        callback: NetworkCallback<SettingsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<SettingsResponse> = getApiInterface().getSettings()
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getCategoriesList(
        userId: Int,
        callback: NetworkCallback<CategoriesResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CategoriesResponse> = getApiInterface().getCategoriesList(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getSubqueriesList(
        userId: Int,
        categoryId: Int,
        callback: NetworkCallback<SubqueriesResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<SubqueriesResponse> = getApiInterface().getSubqueriesList(userId, categoryId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun submitTicket(
        userId: Int,
        message: String,
        screenshots: List<MultipartBody.Part>,
        callback: NetworkCallback<SubmitTicketResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            // Convert List to varargs array for Retrofit
            val screenshotsArray = screenshots.toTypedArray()
            val apiCall: Call<SubmitTicketResponse> = getApiInterface().submitTicket(
                userId, 
                message, 
                *screenshotsArray
            )
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getTicketsList(
        userId: Int,
        callback: NetworkCallback<TicketsListResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<TicketsListResponse> = getApiInterface().getTicketsList(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getBadgesInformation(id: Int,callback: NetworkCallback<BadgeResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<BadgeResponse> = getApiInterface().getBadgesInformation(id)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun blockUser(
        userId: Int,
        callUserId: Int,
        blocked: Int,
        callback: NetworkCallback<BlockUserResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val call = getApiInterface().blockUser(userId, callUserId, blocked)
            call.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }


    fun createUpiPayment(
        userId: Int,
        clientTxnId: String,
        amount: String,
        callback: NetworkCallback<UpiPaymentResponse>
    ) {

        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<UpiPaymentResponse> = getApiInterface().createUpiPayment(userId, clientTxnId, amount)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun sendFcmToken(
        userId: Int,
        token: String,
        callback: NetworkCallback<FcmTokenResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FcmTokenResponse> = getApiInterface().sendFcmToken(userId, token)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun checkReferCode(
        number: String,
        refer_code: String,
        callback: NetworkCallback<ReferralCodeResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<ReferralCodeResponse> = getApiInterface().checkReferCode(number, refer_code)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun updatePanCard(
        userId: Int,
        pancardName: String,
        pancardNumber: String,
        callback: NetworkCallback<PanCardResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().updatePanCard(userId, pancardName, pancardNumber)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getCoupons(
        coinID: String?, userId: Int, callback: NetworkCallback<CouponsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CouponsResponse> = getApiInterface().getCoupons(coinID, userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getDefaultCoupon(
        userId: Int, coinsId: String, callback: NetworkCallback<DefaultCouponResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<DefaultCouponResponse> = getApiInterface().getDefaultCoupon(userId, coinsId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getZohoMailList(language: String, callback: NetworkCallback<ZohoMailResponse>) {
        if (Helper.checkNetworkConnection()) {
            val call = getApiInterface().getZohoMailList(language)
            call.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun createCashfreeOrder(
        userId: Int,
        coinsId: Int,
        callback: NetworkCallback<CreateCashfreeOrderResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CreateCashfreeOrderResponse> =
                getApiInterface().createCashfreeOrder(userId, coinsId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }


    fun updateFirstCallStatus(
        userId: Int,
        callStatus: Int,
        callback: NetworkCallback<FirstCallUpdateResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val call = getApiInterface().updateFirstCallStatus(userId, callStatus)
            call.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }






    fun sendFcmNotification(
        senderId: Int,
        receiverId: Int,
        callType: String,
        channelName: String,
        message: String,
        callback: NetworkCallback<FcmNotificationResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FcmNotificationResponse> =
                getApiInterface().sendFcmNotification(senderId, receiverId, callType, channelName, message)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun sendMessageNotification(
        senderId: Int,
        receiverId: Int,
        message: String,
        callback: NetworkCallback<MessageNotificationResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<MessageNotificationResponse> =
                getApiInterface().sendMessageNotification(senderId, receiverId, message)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun sendFriendRequest(
        senderId: Int,
        receiverId: Int,
        status: Int,
        callback: NetworkCallback<FriendRequestResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FriendRequestResponse> =
                getApiInterface().sendFriendRequest(senderId, receiverId, status)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getMyFriendRequests(
        senderId: Int,
        callback: NetworkCallback<MyFriendRequestsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<MyFriendRequestsResponse> =
                getApiInterface().getMyFriendRequests(senderId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getReceivedFriendRequests(
        receiverId: Int,
        callback: NetworkCallback<ReceivedFriendRequestsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<ReceivedFriendRequestsResponse> =
                getApiInterface().getReceivedFriendRequests(receiverId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getFriendsList(
        senderId: Int,
        callback: NetworkCallback<FriendListResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FriendListResponse> =
                getApiInterface().getFriendsList(senderId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun checkFriendRequest(
        senderId: Int,
        receiverId: Int,
        userId: Int,
        callback: NetworkCallback<FriendRequestResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FriendRequestResponse> =
                getApiInterface().checkFriendRequest(senderId, receiverId, userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getUserAvatar(
        userId: Int,
        callback: NetworkCallback<UserAvatarResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<UserAvatarResponse> = getApiInterface().getUserAvatar(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }



    fun getSpeechText(
        userId: Int, language: String, callback: NetworkCallback<SpeechTextResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<SpeechTextResponse> =
                getApiInterface().getSpeechText(userId, language)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun updateVoice(
        userId: Int, voice: MultipartBody.Part, callback: NetworkCallback<VoiceUpdateResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<VoiceUpdateResponse> = getApiInterface().updateVoice(userId, voice)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getRemainingTime(
        userId: Int, callType: String, callback: NetworkCallback<GetRemainingTimeResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<GetRemainingTimeResponse> = getApiInterface().getRemainingTime(userId, callType)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }
}

interface ApiInterface {
    @FormUrlEncoded
    @POST("login")
    fun login(
        @Field("mobile") mobile: String,
        @Field("code") code: String,
        @Field("code_verifier") code_verifier: String

    ): Call<LoginResponse>
    @FormUrlEncoded
    @POST("send_otp")
    fun sendOTP(
        @Field("mobile") mobile: String,
        @Field("country_code") countryCode: Int,
        @Field("otp") otp: Int
    ): Call<SendOTPResponse>


    @FormUrlEncoded
    @POST("appsettings_list")
    fun appUpdate(@Field("user_id") userId: Int):Call<AppUpdateResponse>

    @FormUrlEncoded
    @POST("individual_app_update")
    fun individualAppUpdate(
        @Field("user_id") userId: Int,
        @Field("current_version") currentVersion: String
    ): Call<IndividualAppUpdateResponse>


    @FormUrlEncoded
    @POST("avatar_list")
    fun getAvatarsList(@Field("gender") gender: String): Call<AvatarsListResponse>

    @FormUrlEncoded
    @POST("calls_list")
    fun getCallsList(
        @Field("user_id") userId: Int,
        @Field("gender") gender: String,
        @Field("offset") offset: Int,
        @Field("limit") limit: Int,
        @Field("type") type: String
    ): Call<CallsListResponse>

    @FormUrlEncoded
    @POST("register")
    fun register(
        @Field("mobile") mobile: String,
        @Field("language") language: String,
        @Field("avatar_id") avatarId: Int,
        @Field("gender") gender: String,
        @Field("referred_by") referred_by: String,
    ): Call<RegisterResponse>

    @FormUrlEncoded
    @POST("userdetails")
    fun getUser(
        @Field("user_id") user_id: Int,
    ): Call<RegisterResponse>

    @FormUrlEncoded
    @POST("female_status_list")
    fun getUserStatus(
        @Field("user_id") user_id: String,
    ): Call<RegisterResponse>

    @FormUrlEncoded
    @POST("userdetails")
    fun getUserSync(
        @Field("user_id") user_id: Int,
    ): Call<RegisterResponse>

    @FormUrlEncoded
    @POST("register")
    fun registerFemale(
        @Field("mobile") mobile: String,
        @Field("language") language: String,
        @Field("avatar_id") avatarId: Int,
        @Field("gender") gender: String,
        @Field("age") age: String,
        @Field("interests") interests: String,
        @Field("describe_yourself") describe_yourself: String,
        @Field("referred_by") referred_by: String,
        ): Call<RegisterResponse>

    @FormUrlEncoded
    @POST("transaction_list")
    fun getTransactions(

        @Field("user_id") userId: Int,
        @Field("offset") offset: Int,
        @Field("limit") limit: Int,



    ): Call<TransactionsResponse>

    @FormUrlEncoded
    @POST("female_users_list")
    fun getFemaleUsers(@Field("user_id") userId: Int): Call<FemaleUsersResponse>

    @FormUrlEncoded
    @POST("random_user")
    fun getRandomUser(@Field("user_id") userId: Int,@Field("call_type") callType: String): Call<RandomUsersResponse>


    @FormUrlEncoded
    @POST("reports")
    fun getReports(@Field("user_id") userId: Int): Call<ReportsResponse>

    @FormUrlEncoded
    @POST("calls_status_update")
    fun updateCallStatus(
        @Field("user_id") userId: Int,
        @Field("call_type") call_type: String,
        @Field("status") status: Int
    ): Call<UpdateCallStatusResponse>

    @FormUrlEncoded
    @POST("female_call_attend")
    fun femaleCallAttend(
        @Field("user_id") userId: Int,
        @Field("call_id") callId: Int,
        @Field("started_time") startedTime: String
    ): Call<FemaleCallAttendResponse>

    @FormUrlEncoded
    @POST("call_female_user")
    fun callFemaleUser(
        @Field("user_id") userId: Int,
        @Field("call_user_id") callUserId: Int,
        @Field("call_type") callType: String,
        @Field("call_switch") call_switch: Int
    ): Call<CallFemaleUserResponse>

    @FormUrlEncoded
    @POST("call_male_user")
    fun callMaleUser(
        @Field("user_id") userId: Int,
        @Field("call_user_id") callUserId: Int,
        @Field("call_type") callType: String,
        @Field("call_switch") call_switch: Int
    ): Call<CallMaleUserResponse>

    @FormUrlEncoded
    @POST("update_connected_call")
    suspend fun updateConnectedCall(
        @Field("user_id") userId: Int,
        @Field("call_id") callId: Int,
        @Field("started_time") startedTime: String,
        @Field("ended_time") endedTime: String,
    ): Response<UpdateConnectedCallResponse>

    @FormUrlEncoded
    @POST("individual_update_connected_call")
    suspend fun individualUpdateConnectedCall(
        @Field("user_id") userId: Int,
        @Field("call_id") callId: Int,
        @Field("started_time") startedTime: String,
        @Field("ended_time") endedTime: String,
    ): Response<UpdateConnectedCallResponse>

    @FormUrlEncoded
    @POST("withdrawals_list")
    fun getEarnings(@Field("user_id") userId: Int): Call<EarningsResponse>


    @FormUrlEncoded
    @POST("update_bank")
    fun updateBank(
        @Field("user_id") userId: Int,
        @Field("bank") bank: String,
        @Field("account_num") accountNum: String,
        @Field("branch") branch: String,
        @Field("ifsc") ifsc: String,
        @Field("holder_name") holderName: String
    ): Call<BankUpdateResponse>

    @FormUrlEncoded
    @POST("ratings")
    fun updateRatings(
        @Field("user_id") userId: Int,
        @Field("call_user_id") call_user_id: Int,
        @Field("ratings") ratings: String,
        @Field("title") title: String,
        @Field("description") description: String
    ): Call<RatingResponse>

    @FormUrlEncoded
    @POST("update_upi")
    fun updateUpi(
        @Field("user_id") userId: Int,
        @Field("upi_id") upiId: String,
        ): Call<UpiUpdateResponse>

    @FormUrlEncoded
    @POST("dwpay/add_coins_requests.php")
     fun addPoints(
        @Field("buyer_name") buyer_name: String,
        @Field("amount") amount: String,
        @Field("email") email: String,
        @Field("phone") phone: String,
        @Field("purpose") purpose: String,
    ): Call<AddPointsResponse>

    @FormUrlEncoded
    @POST("withdrawals")
    fun addWithdrawal(
        @Field("user_id") userId: Int,
        @Field("amount") amount: Int,
        @Field("type") paymentMethod: String
    ): Call<WithdrawResponse>


    @FormUrlEncoded
    @POST("best_offers")
    fun getOffer(
        @Field("user_id") userId: Int,
    ): Call<OfferResponse>


    @FormUrlEncoded
    @POST("update_profile")
    fun updateProfile(
        @Field("user_id") userId: Int,
        @Field("avatar_id") avatarId: Int,
        @Field("name") name: String,
        @Field("interests") interests: String?
    ): Call<UpdateProfileResponse>

    @FormUrlEncoded
    @POST("all_coins_list")
    fun getCoins(@Field("user_id") userId: Int): Call<CoinsResponse>


    @POST("add_coins_phonepe")
    @FormUrlEncoded
    fun addCoins(
        @Field("user_id") userId: Int,
        @Field("coins_id") coinsId: String,
        @Field("status") status: Int,
        @Field("order_id") orderId: String,
        @Field("message") massage: String,
    ): Call<AddCoinsResponse>

    @POST("add_coins_cashfree")
    @FormUrlEncoded
    fun add_coins_cashfree(
        @Field("user_id") userId: Int,
        @Field("coins_id") coinsId: String,
        @Field("status") status: Int,
        @Field("order_id") orderId: String,
        @Field("message") massage: String,
    ): Call<AddCoinsResponse>

    @POST("add_coins")
    @FormUrlEncoded
    fun addCoinsGpay(
        @Field("user_id") userId: Int,
        @Field("coins_id") coinsId: String,
        @Field("status") status: Int,
        @Field("order_id") orderId: String,
        @Field("message") massage: String,
    ): Call<AddCoinsResponse>

    @FormUrlEncoded
    @POST("upi_withdrawals")
    fun addUpiWithdrawal(
        @Field("user_id") userId: Int,
        @Field("amount") amount: Int,
        @Field("type") paymentMethod: String
    ): Call<UpiWithdrawResponse>

    @POST("transaction_charges_list")
    fun getTransactionCharges(): Call<TransactionChargesResponse>


    @POST("try_coins")
    @FormUrlEncoded
    fun tryCoins(
        @Field("user_id") userId: Int,
        @Field("coins_id") coinsId: Int,
        @Field("status") status: Int,
        @Field("order_id") orderId: Int,
        @Field("message") message: String,
    ): Call<AddCoinsResponse>

    @FormUrlEncoded
    @POST("delete_users")
    fun deleteUsers(
        @Field("user_id") userId: Int, @Field("delete_reason") deleteReason: String
    ): Call<DeleteUserResponse>

    @FormUrlEncoded
    @POST("user_validations")
    fun userValidation(
        @Field("user_id") userId: Int, @Field("name") name: String
    ): Call<UserValidationResponse>

    @FormUrlEncoded
    @POST("speech_text")
    fun getSpeechText(
        @Field("user_id") userId: Int, @Field("language") language: String
    ): Call<SpeechTextResponse>

    @Multipart
    @POST("update_voice")
    fun updateVoice(
        @Part("user_id") userId: Int, @Part voice: MultipartBody.Part
    ): Call<VoiceUpdateResponse>

    @FormUrlEncoded
    @POST("get_remaining_time")
    fun getRemainingTime(
        @Field("user_id") userId: Int, @Field("call_type") callType:String
    ): Call<GetRemainingTimeResponse>

    @POST("settings_list")
    fun getSettings(): Call<SettingsResponse>

    @FormUrlEncoded
    @POST("categories_list")
    fun getCategoriesList(
        @Field("user_id") userId: Int
    ): Call<CategoriesResponse>

    @FormUrlEncoded
    @POST("subqueries_list")
    fun getSubqueriesList(
        @Field("user_id") userId: Int,
        @Field("category_id") categoryId: Int
    ): Call<SubqueriesResponse>

    @Multipart
    @POST("create_ticket")
    fun submitTicket(
        @Part("user_id") userId: Int,
        @Part("message") message: String,
        @Part vararg screenshots: MultipartBody.Part
    ): Call<SubmitTicketResponse>

    @FormUrlEncoded
    @POST("tickets_list")
    fun getTicketsList(
        @Field("user_id") userId: Int
    ): Call<TicketsListResponse>

    @FormUrlEncoded
    @POST("badges_information_list")
    fun getBadgesInformation(
        @Field("user_id") id: Int
    ): Call<BadgeResponse>



    @FormUrlEncoded
    @POST("blocked_user")
    fun blockUser(
        @Field("user_id") userId: Int,
        @Field("call_user_id") callUserId: Int,
        @Field("blocked") blocked: Int
    ): Call<BlockUserResponse>


    @FormUrlEncoded
    @POST("explaination_video_list")
    fun getExplanationVideos(
        @Field("language") language: String
    ): Call<ExplanationVideoResponse>

    @FormUrlEncoded
    @POST("whatsapplink_list")
    fun getWhatsappLink(
        @Field("language") language: String
    ): Call<WhatsappLinkResponse>


    @POST("gifts_list")
    fun getGiftImages(): Call<GiftImageResponse>

    @FormUrlEncoded
    @POST("send_gifts")
    fun sendGift(
        @Field("user_id") userId: Int,
        @Field("receiver_id") receiverId: Int,
        @Field("gift_id") giftId: Int
    ): Call<SendGiftResponse>

    @POST("createUpigateway")
    @FormUrlEncoded
    fun createUpiPayment(
        @Field("user_id") userId: Int,
        @Field("client_txn_id") clientTxnId: String,
        @Field("amount") amount: String
    ): Call<UpiPaymentResponse>

    @FormUrlEncoded
    @POST("send_fcm_token")
    fun sendFcmToken(
        @Field("user_id") userId: Int,
        @Field("token") token: String
    ): Call<FcmTokenResponse>

    @FormUrlEncoded
    @POST("send-fcm-notification")
    fun sendFcmNotification(
        @Field("senderId") senderId: Int,
        @Field("receiverId") receiverId: Int,
        @Field("callType") callType: String,
        @Field("channelName") channelName: String,
        @Field("message") message: String
    ): Call<FcmNotificationResponse>


    @FormUrlEncoded
    @POST("user_avatar_image")
    fun getUserAvatar(
        @Field("user_id") userId: Int,
    ): Call<UserAvatarResponse>

    @FormUrlEncoded
    @POST("check_refer_code")
    fun checkReferCode(
        @Field("mobile") number: String,
        @Field("referred_by") refer_code: String
    ): Call<ReferralCodeResponse>

    @FormUrlEncoded
    @POST("update_pancard")
    fun updatePanCard(
        @Field("user_id") userId: Int,
        @Field("pancard_name") panName: String,
        @Field("pancard_number") panNumber: String
    ): Call<PanCardResponse>

    @FormUrlEncoded
    @POST("coupons_list")
    fun getCoupons(
        @Field("coins_id") coinID: String?,
        @Field("user_id") userId: Int
    ): Call<CouponsResponse>

    @FormUrlEncoded
    @POST("default_coupon_applied")
    fun getDefaultCoupon(
        @Field("user_id") userId: Int,
        @Field("coins_id") coinsId: String
    ): Call<DefaultCouponResponse>

    @FormUrlEncoded
    @POST("zohomail_list")
    fun getZohoMailList(
        @Field("language") language: String
    ): Call<ZohoMailResponse>



    @FormUrlEncoded
    @POST("cashfree/create-order")
    fun createCashfreeOrder(
        @Field("user_id") userId: Int,
        @Field("coins_id") coinsId: Int
    ): Call<CreateCashfreeOrderResponse>

    @FormUrlEncoded
    @POST("first_call_update")
    fun updateFirstCallStatus(
        @Field("user_id") userId: Int,
        @Field("call_status") callStatus: Int
    ): Call<FirstCallUpdateResponse>

    @FormUrlEncoded
    @POST("send_message_notification")
    fun sendMessageNotification(
        @Field("sender_id") senderId: Int,
        @Field("receiver_id") receiverId: Int,
        @Field("message") message: String
    ): Call<MessageNotificationResponse>

    @FormUrlEncoded
    @POST("user_call_duration")
    fun getUserCallDuration(
        @Field("call_id") callId: Int,
        @Field("user_id") userId: Int
    ): Call<UserCallDurationResponse>

    @FormUrlEncoded
    @POST("request_friends")
    fun sendFriendRequest(
        @Field("sender_id") senderId: Int,
        @Field("receiver_id") receiverId: Int,
        @Field("status") status: Int
    ): Call<FriendRequestResponse>

    @FormUrlEncoded
    @POST("my_requests")
    fun getMyFriendRequests(
        @Field("sender_id") senderId: Int
    ): Call<MyFriendRequestsResponse>

    @FormUrlEncoded
    @POST("received_requests")
    fun getReceivedFriendRequests(
        @Field("receiver_id") receiverId: Int
    ): Call<ReceivedFriendRequestsResponse>

    @FormUrlEncoded
    @POST("friend_list")
    fun getFriendsList(
        @Field("sender_id") senderId: Int
    ): Call<FriendListResponse>

    @FormUrlEncoded
    @POST("check_friend_request")
    fun checkFriendRequest(
        @Field("sender_id") senderId: Int,
        @Field("receiver_id") receiverId: Int,
        @Field("user_id") userId: Int
    ): Call<FriendRequestResponse>

}