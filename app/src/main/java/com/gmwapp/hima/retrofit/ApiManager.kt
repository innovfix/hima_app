package com.gmwapp.hima.retrofit

import com.gmwapp.hima.activities.RetrofitClient
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AiOnboardingCompleteResponse
import com.gmwapp.hima.retrofit.responses.AiOnboardingReplyResponse
import com.gmwapp.hima.retrofit.responses.AiOnboardingStartResponse
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
import com.gmwapp.hima.retrofit.responses.IplRoomsListResponse
import com.gmwapp.hima.retrofit.responses.IplRoomCreateResponse
import com.gmwapp.hima.retrofit.responses.IplRoomJoinResponse
import com.gmwapp.hima.retrofit.responses.IplRoomLeaveResponse
import com.gmwapp.hima.retrofit.responses.IplRoomDetailResponse
import com.gmwapp.hima.retrofit.responses.IplRoomReactionResponse
import com.gmwapp.hima.retrofit.responses.IplRoomMuteResponse
import com.gmwapp.hima.retrofit.responses.UpdateIplTeamResponse
import com.gmwapp.hima.retrofit.responses.IplMatchSuggestionsResponse
import com.gmwapp.hima.retrofit.responses.CheckBlockStatusResponse
import com.gmwapp.hima.retrofit.responses.CreatorWarningsResponse
import com.gmwapp.hima.retrofit.responses.CallFemaleUserResponse
import com.gmwapp.hima.retrofit.responses.CallMaleUserResponse
import com.gmwapp.hima.retrofit.responses.CheckCallAvailabilityResponse
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
import com.gmwapp.hima.retrofit.responses.FemaleNotificationPreferenceRequest
import com.gmwapp.hima.retrofit.responses.FemaleNotificationPreferenceResponse
import com.gmwapp.hima.retrofit.responses.FemaleDiscoveryResponse
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
import com.gmwapp.hima.retrofit.responses.ReportReasonsResponse
import com.gmwapp.hima.retrofit.responses.ReportUserResponse
import com.gmwapp.hima.retrofit.responses.RegisterResponse
import com.gmwapp.hima.retrofit.responses.ReportsResponse
import com.gmwapp.hima.retrofit.responses.FriendRequestResponse
import com.gmwapp.hima.retrofit.responses.MyFriendRequestsResponse
import com.gmwapp.hima.retrofit.responses.ReceivedFriendRequestsResponse
import com.gmwapp.hima.retrofit.responses.FriendListResponse
import com.gmwapp.hima.retrofit.responses.FriendTabsCountsResponse
import com.gmwapp.hima.retrofit.responses.SendGiftResponse
import com.gmwapp.hima.retrofit.responses.SendOTPResponse
import com.gmwapp.hima.retrofit.responses.SettingsResponse
import com.gmwapp.hima.retrofit.responses.FreeCoinsStatusResponse
import com.gmwapp.hima.retrofit.responses.ClaimFreeCoinsResponse
import com.gmwapp.hima.retrofit.responses.SpeechTextResponse
import com.gmwapp.hima.retrofit.responses.TransactionChargesResponse
import com.gmwapp.hima.retrofit.responses.TransactionsResponse
import com.gmwapp.hima.retrofit.responses.FemaleTransactionsResponse
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
import com.gmwapp.hima.retrofit.responses.CallRejectCountResponse
import com.gmwapp.hima.retrofit.responses.CallDropStatusRequest
import com.gmwapp.hima.retrofit.responses.CallDropStatusResponse
import com.gmwapp.hima.retrofit.responses.CallStatusRequest
import com.gmwapp.hima.retrofit.responses.CallStatusResponse
import com.gmwapp.hima.retrofit.responses.ChatListResponse
import com.gmwapp.hima.retrofit.responses.MessageListResponse
import com.gmwapp.hima.retrofit.responses.SendMessageResponse
import com.gmwapp.hima.retrofit.responses.SimpleAckResponse
import com.gmwapp.hima.retrofit.responses.MarkReadResponse
import com.gmwapp.hima.retrofit.responses.MarkMessagesReadResponse
import com.gmwapp.hima.retrofit.responses.ChatHistoryResponse
import com.gmwapp.hima.retrofit.responses.ChatAttachmentUploadResponse
import com.gmwapp.hima.retrofit.responses.MyChatResponse
import com.gmwapp.hima.retrofit.responses.FallbackSendMessageResponse
import com.gmwapp.hima.retrofit.responses.AddReactionResponse
import com.gmwapp.hima.retrofit.responses.AddFavoriteResponse
import com.gmwapp.hima.retrofit.responses.RemoveFavoriteResponse
import com.gmwapp.hima.retrofit.responses.CheckFavoriteResponse
import com.gmwapp.hima.retrofit.responses.CheckReferralOfferResponse
import com.gmwapp.hima.retrofit.responses.RatingPromptResponse
import com.gmwapp.hima.retrofit.responses.UpdateRatingPromptResponse
import com.gmwapp.hima.retrofit.responses.LogAppEventResponse
import com.gmwapp.hima.retrofit.responses.GetFemaleTalkDurationResponse
import com.gmwapp.hima.retrofit.responses.InstallReferrerResponse
import com.gmwapp.hima.retrofit.responses.FirstInstallResponse
import com.gmwapp.hima.retrofit.responses.TrackingInfoResponse
import com.gmwapp.hima.retrofit.responses.AgoraTokenResponse
import com.gmwapp.hima.retrofit.responses.IcebreakerQuestionsResponse
import com.gmwapp.hima.retrofit.responses.LudoFcmResponse
import com.gmwapp.hima.retrofit.responses.GetFemaleNotificationPreferenceRequest
import com.gmwapp.hima.retrofit.responses.GetFemaleNotificationPreferenceResponse
import com.gmwapp.hima.retrofit.responses.ListCreatorOnlineNotificationsRequest
import com.gmwapp.hima.retrofit.responses.ListCreatorOnlineNotificationsResponse
import com.gmwapp.hima.retrofit.responses.CheckRatingEligibilityResponse
import com.gmwapp.hima.retrofit.responses.SubmitRatingResponse
import com.gmwapp.hima.retrofit.responses.MyWarningsResponse
import com.gmwapp.hima.retrofit.responses.MissedCallCountResponse
import com.gmwapp.hima.retrofit.responses.PaywallVideoContentResponse
import com.gmwapp.hima.retrofit.responses.StarCreatorSpeechResponse
import com.gmwapp.hima.retrofit.responses.StarCreatorSubmitResponse
import com.gmwapp.hima.utils.Helper
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Body
import java.io.File
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

    fun firstInstall(
        callback: NetworkCallback<FirstInstallResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FirstInstallResponse> = getApiInterface().firstInstall()
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun trackingInfo(
        savedAddress: String,
        userId: Int,
        callback: NetworkCallback<TrackingInfoResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<TrackingInfoResponse> = getApiInterface().trackingInfo(savedAddress, userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getPaywallVideoContent(
        userId: Int,
        callback: NetworkCallback<PaywallVideoContentResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<PaywallVideoContentResponse> = getApiInterface().getPaywallVideoContent(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun trackingInfoRaw(
        savedAddress: String,
        userId: Int,
        callback: NetworkCallback<ResponseBody>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<ResponseBody> = getApiInterface().trackingInfoRaw(savedAddress, userId)
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
        userId: Int,
        gender: String,
        limit: Int,
        currentOffset: Int,
        type: String,
        search: String?,
        fav: Int? = null,
        days: Int = 0,
        callback: NetworkCallback<CallsListResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CallsListResponse> =
                getApiInterface().getCallsList(userId, gender, currentOffset, limit, type, search, fav, days)
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

    fun getFemaleTransactions(
        userId: Int, offset: Int, limit: Int, callback: NetworkCallback<FemaleTransactionsResponse>
    ) {
        android.util.Log.d("femaleTrasactionLog", "getFemaleTransactions: Called with userId=$userId, offset=$offset, limit=$limit")
        android.util.Log.d("femaleTrasactionLog", "getFemaleTransactions: API endpoint = female_transaction")
        
        if (Helper.checkNetworkConnection()) {
            android.util.Log.d("femaleTrasactionLog", "getFemaleTransactions: Network connection available")
            try {
                val apiCall: Call<FemaleTransactionsResponse> = getApiInterface().getFemaleTransactions(userId, offset, limit)
                android.util.Log.d("femaleTrasactionLog", "getFemaleTransactions: API call created, enqueueing...")
                android.util.Log.d("femaleTrasactionLog", "getFemaleTransactions: Request URL = ${apiCall.request().url}")
                apiCall.enqueue(callback)
                android.util.Log.d("femaleTrasactionLog", "getFemaleTransactions: API call enqueued successfully")
            } catch (e: Exception) {
                android.util.Log.e("femaleTrasactionLog", "getFemaleTransactions: Exception creating API call", e)
                android.util.Log.e("femaleTrasactionLog", "getFemaleTransactions: Exception message = ${e.message}")
                e.printStackTrace()
            }
        } else {
            android.util.Log.e("femaleTrasactionLog", "getFemaleTransactions: No network connection available")
            callback.onNoNetwork()
        }
    }

    fun getFemaleUsers(
        userId: Int, filter: String? = null, callback: NetworkCallback<FemaleUsersResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FemaleUsersResponse> = getApiInterface().getFemaleUsers(userId, filter)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getFemaleDiscovery(
        userId: Int,
        callback: NetworkCallback<FemaleDiscoveryResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FemaleDiscoveryResponse> = getApiInterface().getFemaleDiscovery(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getRandomUser(
        userId: Int,
        callType: String,
        filter: String? = null,
        callback: NetworkCallback<RandomUsersResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<RandomUsersResponse> = getApiInterface().getRandomUser(userId, callType, filter)
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

    fun getFemaleTalkDuration(
        userId: Int,
        callback: NetworkCallback<GetFemaleTalkDurationResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<GetFemaleTalkDurationResponse> = getApiInterface().getFemaleTalkDuration(userId)
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

    fun sendLudoFcm(
        action: String,
        fromUserId: Int? = null,
        toUserId: Int? = null,
        inviteId: String? = null,
        callId: String? = null,
        callback: NetworkCallback<LudoFcmResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<LudoFcmResponse> = getApiInterface().sendLudoFcm(
                action = action,
                fromUserId = fromUserId,
                toUserId = toUserId,
                inviteId = inviteId,
                callId = callId
            )
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

    fun getFreeCoinsStatus(
        userId: Int,
        callback: NetworkCallback<FreeCoinsStatusResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FreeCoinsStatusResponse> = getApiInterface().getFreeCoinsStatus(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun claimFreeCoins(
        userId: Int,
        callback: NetworkCallback<ClaimFreeCoinsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<ClaimFreeCoinsResponse> = getApiInterface().claimFreeCoins(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getCategoriesList(
        userId: Int,
        language: String,
        callback: NetworkCallback<CategoriesResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CategoriesResponse> =
                getApiInterface().getCategoriesList(userId, language)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getSubqueriesList(
        userId: Int,
        categoryId: Int,
        language: String,
        callback: NetworkCallback<SubqueriesResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<SubqueriesResponse> =
                getApiInterface().getSubqueriesList(userId, categoryId, language)
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

    fun checkBlockStatus(
        userId: Int,
        callUserId: Int,
        callback: NetworkCallback<CheckBlockStatusResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val call = getApiInterface().checkBlockStatus(userId, callUserId)
            call.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun unblockUser(
        userId: Int,
        callUserId: Int,
        callback: NetworkCallback<BlockUserResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val call = getApiInterface().unblockUser(userId, callUserId)
            call.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getReportReasons(userId: Int, callback: NetworkCallback<ReportReasonsResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<ReportReasonsResponse> = getApiInterface().getReportReasons(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun reportUser(
        userId: Int,
        reportUserId: Int,
        reasonId: Int,
        reasonText: String,
        callback: NetworkCallback<ReportUserResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<ReportUserResponse> =
                getApiInterface().reportUser(userId, reportUserId, reasonId, reasonText)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getCreatorWarnings(
        userId: Int,
        callback: NetworkCallback<CreatorWarningsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CreatorWarningsResponse> =
                getApiInterface().getCreatorWarnings(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getMyWarnings(
        userId: Int,
        callback: NetworkCallback<MyWarningsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<MyWarningsResponse> = getApiInterface().getMyWarnings(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getMissedCallCount(
        userId: Int,
        seen: Int,
        callback: NetworkCallback<MissedCallCountResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<MissedCallCountResponse> = getApiInterface().getMissedCallCount(userId, seen)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun setFemaleNotificationPreference(
        maleUserId: Int,
        femaleUserId: Int,
        status: Int,
        callback: NetworkCallback<FemaleNotificationPreferenceResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FemaleNotificationPreferenceResponse> =
                getApiInterface().setFemaleNotificationPreference(
                    FemaleNotificationPreferenceRequest(
                        maleUserId = maleUserId,
                        femaleUserId = femaleUserId,
                        status = status
                    )
                )
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getFemaleNotificationPreference(
        maleUserId: Int,
        femaleUserId: Int,
        callback: NetworkCallback<GetFemaleNotificationPreferenceResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<GetFemaleNotificationPreferenceResponse> =
                getApiInterface().getFemaleNotificationPreference(
                    GetFemaleNotificationPreferenceRequest(
                        maleUserId = maleUserId,
                        femaleUserId = femaleUserId
                    )
                )
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun listCreatorOnlineNotifications(
        userId: Int,
        onlyOnline: Int? = null,
        search: String? = null,
        callback: NetworkCallback<ListCreatorOnlineNotificationsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<ListCreatorOnlineNotificationsResponse> =
                getApiInterface().listCreatorOnlineNotifications(
                    ListCreatorOnlineNotificationsRequest(
                        userId = userId,
                        onlyOnline = onlyOnline,
                        search = search
                    )
                )
            apiCall.enqueue(callback)
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

    fun getChats(
        userId: Int,
        page: Int = 1,
        limit: Int = 20,
        callback: NetworkCallback<ChatListResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<ChatListResponse> = getApiInterface().getChats(userId, page, limit)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getMessages(
        userId: Int,
        chatId: String,
        page: Int = 1,
        limit: Int = 50,
        callback: NetworkCallback<MessageListResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<MessageListResponse> = getApiInterface().getMessages(userId, chatId, page, limit)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getChatHistory(
        userId: Int,
        receiverId: Int,
        limit: Int = 10,
        offset: Int = 0,
        callback: NetworkCallback<ChatHistoryResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<ChatHistoryResponse> = getApiInterface().getChatHistory(userId, receiverId, limit, offset)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    /**
     * Same as [getChatHistory] but returns the underlying [Call] so the caller can
     * [Call.cancel] when the screen is closed or a newer request supersedes this one.
     */
    fun getChatHistoryCancellable(
        userId: Int,
        receiverId: Int,
        limit: Int = 10,
        offset: Int = 0,
        callback: NetworkCallback<ChatHistoryResponse>
    ): Call<ChatHistoryResponse>? {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<ChatHistoryResponse> =
                getApiInterface().getChatHistory(userId, receiverId, limit, offset)
            apiCall.enqueue(callback)
            return apiCall
        }
        callback.onNoNetwork()
        return null
    }

    fun sendMessage(
        userId: Int,
        toUserId: Int,
        message: String,
        messageType: String = "text",
        callback: NetworkCallback<SendMessageResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<SendMessageResponse> = getApiInterface().sendMessage(userId, toUserId, message, messageType)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    // TODO: Backend must implement `chats/upload_attachment` and return a
    // public URL in `data.url`. Until then this call will fail server-side.
    fun uploadChatAttachment(
        userId: Int,
        toUserId: Int,
        messageType: String,
        file: File,
        callback: NetworkCallback<ChatAttachmentUploadResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val plain = "text/plain".toMediaTypeOrNull()
            val mimeType = when (messageType.lowercase()) {
                "image" -> "image/jpeg"
                "audio" -> "audio/mp4"
                else -> "application/octet-stream"
            }.toMediaTypeOrNull()

            val filePart = MultipartBody.Part.createFormData(
                "file",
                file.name,
                file.asRequestBody(mimeType)
            )

            val apiCall: Call<ChatAttachmentUploadResponse> = getApiInterface().uploadChatAttachment(
                userId.toString().toRequestBody(plain),
                toUserId.toString().toRequestBody(plain),
                messageType.toRequestBody(plain),
                filePart
            )
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun fallbackSendMessage(
        fromUserId: Int,
        toUserId: Int,
        message: String,
        messageType: String? = null,
        attachmentUrl: String? = null,
        callback: NetworkCallback<FallbackSendMessageResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FallbackSendMessageResponse> = getApiInterface().fallbackSendMessage(
                fromUserId,
                toUserId,
                message,
                messageType,
                attachmentUrl
            )
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun addMessageReaction(
        userId: Int,
        messageId: Int,
        reactionEmoji: String?,
        callback: NetworkCallback<AddReactionResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<AddReactionResponse> = getApiInterface().addMessageReaction(userId, messageId, reactionEmoji)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun markRead(
        userId: Int,
        chatId: String,
        callback: NetworkCallback<MarkReadResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<MarkReadResponse> = getApiInterface().markRead(userId, chatId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun markMessagesRead(
        userId: Int,
        receiverId: Int,
        lastMessageId: Int,
        callback: NetworkCallback<MarkMessagesReadResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<MarkMessagesReadResponse> = getApiInterface().markMessagesRead(userId, receiverId, lastMessageId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    /**
     * Delete-for-everyone fallback used when Socket.IO isn't connected at tap time.
     * The sender must own the message; backend flips `is_deleted=1` and broadcasts
     * `message_deleted` on its own, so on-success there's nothing else to do here.
     */
    fun deleteChatMessage(
        fromUserId: Int,
        toUserId: Int,
        messageId: String,
        callback: NetworkCallback<SimpleAckResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<SimpleAckResponse> =
                getApiInterface().deleteChatMessage(fromUserId, toUserId, messageId)
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
        search: String?,
        callback: NetworkCallback<MyFriendRequestsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<MyFriendRequestsResponse> =
                getApiInterface().getMyFriendRequests(senderId, search)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getReceivedFriendRequests(
        receiverId: Int,
        search: String?,
        callback: NetworkCallback<ReceivedFriendRequestsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<ReceivedFriendRequestsResponse> =
                getApiInterface().getReceivedFriendRequests(receiverId, search)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getFriendsList(
        senderId: Int,
        search: String?,
        callback: NetworkCallback<FriendListResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FriendListResponse> =
                getApiInterface().getFriendsList(senderId, search)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getMyChat(
        userId: Int,
        search: String?,
        limit: Int,
        offset: Int,
        callback: NetworkCallback<MyChatResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<MyChatResponse> = getApiInterface().getMyChat(userId, search, limit, offset)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getMyChatFriends(
        userId: Int,
        search: String?,
        limit: Int,
        offset: Int,
        callback: NetworkCallback<MyChatResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<MyChatResponse> =
                getApiInterface().getMyChatFriends(userId, search, limit, offset)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getMyChatGeneral(
        userId: Int,
        search: String?,
        limit: Int,
        offset: Int,
        callback: NetworkCallback<MyChatResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<MyChatResponse> =
                getApiInterface().getMyChatGeneral(userId, search, limit, offset)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun blockChatUser(
        userId: Int,
        blockedUserId: Int,
        callback: NetworkCallback<BlockUserResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<BlockUserResponse> = getApiInterface().blockChatUser(userId, blockedUserId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun unblockChatUser(
        userId: Int,
        blockedUserId: Int,
        callback: NetworkCallback<BlockUserResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<BlockUserResponse> = getApiInterface().unblockChatUser(userId, blockedUserId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun addFavorite(
        userId: Int,
        favoriteId: Int,
        callback: NetworkCallback<AddFavoriteResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<AddFavoriteResponse> = getApiInterface().addFavorite(userId, favoriteId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun removeFavorite(
        userId: Int,
        favoriteId: Int,
        callback: NetworkCallback<RemoveFavoriteResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<RemoveFavoriteResponse> = getApiInterface().removeFavorite(userId, favoriteId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun checkFavorite(
        userId: Int,
        favoriteId: Int,
        callback: NetworkCallback<CheckFavoriteResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CheckFavoriteResponse> = getApiInterface().checkFavorite(userId, favoriteId)
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

    fun getFriendTabsCounts(
        userId: Int,
        callback: NetworkCallback<FriendTabsCountsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<FriendTabsCountsResponse> =
                getApiInterface().getFriendTabsCounts(userId)
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

    fun getStarCreatorSpeechText(
        userId: Int, callback: NetworkCallback<StarCreatorSpeechResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<StarCreatorSpeechResponse> =
                getApiInterface().getStarCreatorSpeechText(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun submitStarCreatorApplication(
        userId: Int,
        experienceAnswer: String,
        callPreference: String,
        audioRecording: MultipartBody.Part?,
        videoRecording: MultipartBody.Part?,
        callback: NetworkCallback<StarCreatorSubmitResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val plain = "text/plain".toMediaTypeOrNull()
            val apiCall: Call<StarCreatorSubmitResponse> =
                getApiInterface().submitStarCreatorApplication(
                    userId.toString().toRequestBody(plain),
                    experienceAnswer.toRequestBody(plain),
                    callPreference.toRequestBody(plain),
                    audioRecording,
                    videoRecording
                )
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

    fun toggleDnd(
        userId: Int,
        enabled: Int,
        durationHours: Int,
        callback: NetworkCallback<com.gmwapp.hima.retrofit.responses.ToggleDndResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().toggleDnd(userId, enabled, durationHours)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun trackRandomCallFailure(userId: Int, callType: String) {
        if (!Helper.checkNetworkConnection()) return
        val apiCall = getApiInterface().trackRandomCallFailure(userId, callType)
        apiCall.enqueue(object : retrofit2.Callback<okhttp3.ResponseBody> {
            override fun onResponse(call: Call<okhttp3.ResponseBody>, response: retrofit2.Response<okhttp3.ResponseBody>) {
                android.util.Log.d("RandomCallFailure", "Tracked: code=${response}")
            }
            override fun onFailure(call: Call<okhttp3.ResponseBody>, t: Throwable) {
                android.util.Log.e("RandomCallFailure", "Track failed: ${t.message}")
            }
        })
    }

    fun getIcebreakerQuestions(
        userId: Int,
        callback: NetworkCallback<IcebreakerQuestionsResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<IcebreakerQuestionsResponse> = getApiInterface().getIcebreakerQuestions(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun callRejectCount(
        maleUserId: Int,
        femaleUserId: Int,
        callback: NetworkCallback<CallRejectCountResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CallRejectCountResponse> = getApiInterface().callRejectCount(maleUserId, femaleUserId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun checkCallAvailability(
        maleUserId: Int,
        femaleUserId: Int,
        callback: NetworkCallback<CheckCallAvailabilityResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CheckCallAvailabilityResponse> = getApiInterface().checkCallAvailability(maleUserId, femaleUserId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun checkReferralOffer(
        userId: Int,
        callback: NetworkCallback<CheckReferralOfferResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CheckReferralOfferResponse> = getApiInterface().checkReferralOffer(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun checkRatingPrompt(
        userId: Int,
        callback: NetworkCallback<RatingPromptResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<RatingPromptResponse> = getApiInterface().checkRatingPrompt(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun updateRatingPromptShown(
        userId: Int,
        ratingSubmitted: Int = 0,
        callback: NetworkCallback<UpdateRatingPromptResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<UpdateRatingPromptResponse> = getApiInterface().updateRatingPromptShown(userId, ratingSubmitted)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun logAppEvent(
        eventName: String,
        userId: Int?,
        platform: String,
        eventParams: String?,
        eventValue: Double?,
        deviceInfo: String?,
        appVersion: String?,
        osVersion: String?,
        callback: NetworkCallback<LogAppEventResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<LogAppEventResponse> = getApiInterface().logAppEvent(
                eventName,
                userId,
                platform,
                eventParams,
                eventValue,
                deviceInfo,
                appVersion,
                osVersion
            )
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun logInstallReferrer(
        responseData: String,
        deviceInfo: String?,
        appVersion: String?,
        osVersion: String?,
        userId: Int,
        callback: NetworkCallback<InstallReferrerResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<InstallReferrerResponse> = getApiInterface().logInstallReferrer(
                responseData,
                deviceInfo,
                appVersion,
                osVersion,
                userId
            )
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun logUserInstallReferrer(
        userId: Int,
        responseData: String,
        callback: NetworkCallback<InstallReferrerResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<InstallReferrerResponse> = getApiInterface().logUserInstallReferrer(
                userId,
                responseData
            )
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getAgoraToken(
        channelName: String,
        uid: Int,
        role: String = "publisher",
        expire: Int = 3600,
        callback: NetworkCallback<AgoraTokenResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<AgoraTokenResponse> = getApiInterface().getAgoraToken(
                channelName,
                uid,
                role,
                expire
            )
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun callDropStatus(
        request: CallDropStatusRequest,
        callback: NetworkCallback<CallDropStatusResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CallDropStatusResponse> = getApiInterface().callDropStatus(request)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun callStatus(
        request: CallStatusRequest,
        callback: NetworkCallback<CallStatusResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CallStatusResponse> = getApiInterface().callStatus(request)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun checkRatingEligibility(
        userId: Int,
        callback: NetworkCallback<CheckRatingEligibilityResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<CheckRatingEligibilityResponse> = getApiInterface().checkRatingEligibility(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun submitRating(
        userId: Int,
        starCount: Int,
        description: String?,
        callback: NetworkCallback<SubmitRatingResponse>
    ) {
        if (Helper.checkNetworkConnection()) {
            val apiCall: Call<SubmitRatingResponse> = getApiInterface().submitRating(userId, starCount, description)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    // ===== IPL Rooms =====

    fun getIplRooms(userId: Int, language: String? = null, limit: Int = 10, offset: Int = 0, callback: NetworkCallback<IplRoomsListResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().getIplRooms(userId, language, limit, offset)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun createIplRoom(userId: Int, roomName: String, teamA: String, teamB: String, creatorTeam: String, callback: NetworkCallback<IplRoomCreateResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().createIplRoom(userId, roomName, teamA, teamB, creatorTeam)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun joinIplRoom(userId: Int, roomId: Int, callback: NetworkCallback<IplRoomJoinResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().joinIplRoom(userId, roomId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun leaveIplRoom(userId: Int, roomId: Int, callback: NetworkCallback<IplRoomLeaveResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().leaveIplRoom(userId, roomId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getIplRoomDetails(roomId: Int, callback: NetworkCallback<IplRoomDetailResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().getIplRoomDetails(roomId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun sendIplReaction(userId: Int, roomId: Int, reactionType: String, callback: NetworkCallback<IplRoomReactionResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().sendIplReaction(userId, roomId, reactionType)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun toggleIplMute(userId: Int, roomId: Int, isMuted: Int, callback: NetworkCallback<IplRoomMuteResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().toggleIplMute(userId, roomId, isMuted)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun getIplMatchSuggestions(callback: NetworkCallback<IplMatchSuggestionsResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().getIplMatchSuggestions()
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun joinIplRoomByCode(userId: Int, inviteCode: String, callback: NetworkCallback<IplRoomJoinResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().joinIplRoomByCode(userId, inviteCode)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun joinIplRoomRandom(userId: Int, callback: NetworkCallback<IplRoomJoinResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().joinIplRoomRandom(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun listenerJoin(userId: Int, roomId: Int, callback: NetworkCallback<IplRoomLeaveResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().listenerJoin(userId, roomId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun listenerLeave(userId: Int, roomId: Int, callback: NetworkCallback<IplRoomLeaveResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().listenerLeave(userId, roomId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun closeIplRoom(userId: Int, roomId: Int, callback: NetworkCallback<IplRoomLeaveResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().closeIplRoom(userId, roomId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun updateIplTeam(userId: Int, iplTeam: String, callback: NetworkCallback<UpdateIplTeamResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().updateIplTeam(userId, iplTeam)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun lookupRoomByCode(inviteCode: String, callback: NetworkCallback<IplRoomJoinResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().lookupRoomByCode(inviteCode)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun lookupRoomRandom(userId: Int, callback: NetworkCallback<IplRoomJoinResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().lookupRoomRandom(userId)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    // AI Onboarding
    fun aiOnboardingStart(userId: Int, concern: String, callback: NetworkCallback<AiOnboardingStartResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().aiOnboardingStart(userId, concern)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun aiOnboardingReply(sessionId: Int, userMessage: String, callback: NetworkCallback<AiOnboardingReplyResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().aiOnboardingReply(sessionId, userMessage)
            apiCall.enqueue(callback)
        } else {
            callback.onNoNetwork()
        }
    }

    fun aiOnboardingComplete(sessionId: Int, callback: NetworkCallback<AiOnboardingCompleteResponse>) {
        if (Helper.checkNetworkConnection()) {
            val apiCall = getApiInterface().aiOnboardingComplete(sessionId)
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

    @POST("first_install")
    fun firstInstall(): Call<FirstInstallResponse>

    @FormUrlEncoded
    @POST("tracking_info")
    fun trackingInfo(
        @Field("saved_address") savedAddress: String,
        @Field("user_id") userId: Int
    ): Call<TrackingInfoResponse>

    @FormUrlEncoded
    @POST("paywall_video_content")
    fun getPaywallVideoContent(
        @Field("user_id") userId: Int
    ): Call<PaywallVideoContentResponse>

    @FormUrlEncoded
    @POST("tracking_info")
    fun trackingInfoRaw(
        @Field("saved_address") savedAddress: String,
        @Field("user_id") userId: Int
    ): Call<ResponseBody>

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
        @Field("type") type: String,
        @Field("search") search: String?,
        @Field("fav") fav: Int? = null,
        @Field("days") days: Int = 0
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
    @POST("female_transaction")
    fun getFemaleTransactions(
        @Field("user_id") userId: Int,
        @Field("offset") offset: Int,
        @Field("limit") limit: Int,
    ): Call<FemaleTransactionsResponse>

    @FormUrlEncoded
    @POST("female_users_list")
    fun getFemaleUsers(
        @Field("user_id") userId: Int,
        @Field("filter") filter: String?
    ): Call<FemaleUsersResponse>

    @FormUrlEncoded
    @POST("female_discovery")
    fun getFemaleDiscovery(
        @Field("user_id") userId: Int
    ): Call<FemaleDiscoveryResponse>

    @FormUrlEncoded
    @POST("random_user")
    fun getRandomUser(
        @Field("user_id") userId: Int,
        @Field("call_type") callType: String,
        @Field("filter") filter: String?
    ): Call<RandomUsersResponse>


    @FormUrlEncoded
    @POST("reports")
    fun getReports(@Field("user_id") userId: Int): Call<ReportsResponse>

    @FormUrlEncoded
    @POST("get-female-talk-duration")
    fun getFemaleTalkDuration(@Field("user_id") userId: Int): Call<GetFemaleTalkDurationResponse>

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

    @FormUrlEncoded
    @POST("star_creator_speech_text")
    fun getStarCreatorSpeechText(
        @Field("user_id") userId: Int
    ): Call<StarCreatorSpeechResponse>

    @Multipart
    @POST("submit_star_creator_application")
    fun submitStarCreatorApplication(
        @Part("user_id") userId: RequestBody,
        @Part("experience_answer") experienceAnswer: RequestBody,
        @Part("call_preference") callPreference: RequestBody,
        @Part audioRecording: MultipartBody.Part?,
        @Part videoRecording: MultipartBody.Part?
    ): Call<StarCreatorSubmitResponse>

    @FormUrlEncoded
    @POST("call_reject_count")
    fun callRejectCount(
        @Field("male_user_id") maleUserId: Int,
        @Field("female_user_id") femaleUserId: Int
    ): Call<CallRejectCountResponse>

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

    @FormUrlEncoded
    @POST("track_random_call_failure")
    fun trackRandomCallFailure(
        @Field("user_id") userId: Int,
        @Field("call_type") callType: String
    ): Call<okhttp3.ResponseBody>

    @FormUrlEncoded
    @POST("toggle_dnd")
    fun toggleDnd(
        @Field("user_id") userId: Int,
        @Field("enabled") enabled: Int,
        @Field("duration_hours") durationHours: Int
    ): Call<com.gmwapp.hima.retrofit.responses.ToggleDndResponse>

    @FormUrlEncoded
    @POST("icebreaker_questions")
    fun getIcebreakerQuestions(
        @Field("user_id") userId: Int
    ): Call<IcebreakerQuestionsResponse>

    @POST("settings_list")
    fun getSettings(): Call<SettingsResponse>

    @FormUrlEncoded
    @POST("free_coins_status")
    fun getFreeCoinsStatus(@Field("user_id") userId: Int): Call<FreeCoinsStatusResponse>

    @FormUrlEncoded
    @POST("claim_free_coins")
    fun claimFreeCoins(@Field("user_id") userId: Int): Call<ClaimFreeCoinsResponse>

    @FormUrlEncoded
    @POST("categories_list")
    fun getCategoriesList(
        @Field("user_id") userId: Int,
        @Field("language") language: String
    ): Call<CategoriesResponse>

    @FormUrlEncoded
    @POST("subqueries_list")
    fun getSubqueriesList(
        @Field("user_id") userId: Int,
        @Field("category_id") categoryId: Int,
        @Field("language") language: String
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
    @POST("check_block_status")
    fun checkBlockStatus(
        @Field("user_id") userId: Int,
        @Field("call_user_id") callUserId: Int
    ): Call<CheckBlockStatusResponse>

    @FormUrlEncoded
    @POST("unblock_user")
    fun unblockUser(
        @Field("user_id") userId: Int,
        @Field("call_user_id") callUserId: Int
    ): Call<BlockUserResponse>

    @FormUrlEncoded
    @POST("report_reasons")
    fun getReportReasons(
        @Field("user_id") userId: Int
    ): Call<ReportReasonsResponse>

    @FormUrlEncoded
    @POST("report_user")
    fun reportUser(
        @Field("user_id") userId: Int,
        @Field("report_user_id") reportUserId: Int,
        @Field("reason_id") reasonId: Int,
        @Field("reason_text") reasonText: String
    ): Call<ReportUserResponse>

    @FormUrlEncoded
    @POST("creator_warnings")
    fun getCreatorWarnings(
        @Field("user_id") userId: Int
    ): Call<CreatorWarningsResponse>

    @FormUrlEncoded
    @POST("my_warnings")
    fun getMyWarnings(
        @Field("user_id") userId: Int
    ): Call<MyWarningsResponse>

    @FormUrlEncoded
    @POST("missed_call_count")
    fun getMissedCallCount(
        @Field("user_id") userId: Int,
        @Field("seen") seen: Int
    ): Call<MissedCallCountResponse>

    @POST("set_female_notification_preference")
    fun setFemaleNotificationPreference(
        @Body request: FemaleNotificationPreferenceRequest
    ): Call<FemaleNotificationPreferenceResponse>

    @POST("get_female_notification_preference")
    fun getFemaleNotificationPreference(
        @Body request: GetFemaleNotificationPreferenceRequest
    ): Call<GetFemaleNotificationPreferenceResponse>

    @POST("list_creator_online_notifications")
    fun listCreatorOnlineNotifications(
        @Body request: ListCreatorOnlineNotificationsRequest
    ): Call<ListCreatorOnlineNotificationsResponse>

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

    @FormUrlEncoded
    @POST("send_ludo_fcm")
    fun sendLudoFcm(
        @Field("action") action: String,
        @Field("from_user_id") fromUserId: Int? = null,
        @Field("to_user_id") toUserId: Int? = null,
        @Field("invite_id") inviteId: String? = null,
        @Field("call_id") callId: String? = null
    ): Call<LudoFcmResponse>

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
        @Field("sender_id") senderId: Int,
        @Field("search") search: String?
    ): Call<MyFriendRequestsResponse>

    @FormUrlEncoded
    @POST("received_requests")
    fun getReceivedFriendRequests(
        @Field("receiver_id") receiverId: Int,
        @Field("search") search: String?
    ): Call<ReceivedFriendRequestsResponse>

    @FormUrlEncoded
    @POST("friend_list")
    fun getFriendsList(
        @Field("sender_id") senderId: Int,
        @Field("search") search: String?
    ): Call<FriendListResponse>

    @FormUrlEncoded
    @POST("check_friend_request")
    fun checkFriendRequest(
        @Field("sender_id") senderId: Int,
        @Field("receiver_id") receiverId: Int,
        @Field("user_id") userId: Int
    ): Call<FriendRequestResponse>

    @FormUrlEncoded
    @POST("friend_tabs_counts")
    fun getFriendTabsCounts(
        @Field("user_id") userId: Int
    ): Call<FriendTabsCountsResponse>

    @GET("chats")
    fun getChats(
        @Query("user_id") userId: Int,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Call<ChatListResponse>

    @GET("chats/messages")
    fun getMessages(
        @Query("user_id") userId: Int,
        @Query("chat_id") chatId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Call<MessageListResponse>

    @FormUrlEncoded
    @POST("add_favorite")
    fun addFavorite(
        @Field("user_id") userId: Int,
        @Field("favorite_id") favoriteId: Int
    ): Call<AddFavoriteResponse>

    @FormUrlEncoded
    @POST("remove_favorite")
    fun removeFavorite(
        @Field("user_id") userId: Int,
        @Field("favorite_id") favoriteId: Int
    ): Call<RemoveFavoriteResponse>

    @FormUrlEncoded
    @POST("check_favorite")
    fun checkFavorite(
        @Field("user_id") userId: Int,
        @Field("favorite_id") favoriteId: Int
    ): Call<CheckFavoriteResponse>

    @FormUrlEncoded
    @POST("chats/send-message")
    fun sendMessage(
        @Field("user_id") userId: Int,
        @Field("to_user_id") toUserId: Int,
        @Field("message") message: String,
        @Field("message_type") messageType: String = "text"
    ): Call<SendMessageResponse>

    @Multipart
    @POST("chats/upload_attachment")
    fun uploadChatAttachment(
        @Part("user_id") userId: RequestBody,
        @Part("to_user_id") toUserId: RequestBody,
        @Part("message_type") messageType: RequestBody,
        @Part file: MultipartBody.Part
    ): Call<ChatAttachmentUploadResponse>

    @FormUrlEncoded
    @POST("chats/mark-read")
    fun markRead(
        @Field("user_id") userId: Int,
        @Field("chat_id") chatId: String
    ): Call<MarkReadResponse>

    @FormUrlEncoded
    @POST("mark_messages_read")
    fun markMessagesRead(
        @Field("user_id") userId: Int,
        @Field("receiver_id") receiverId: Int,
        @Field("last_message_id") lastMessageId: Int
    ): Call<MarkMessagesReadResponse>

    @FormUrlEncoded
    @POST("delete_chat_message")
    fun deleteChatMessage(
        @Field("from_user_id") fromUserId: Int,
        @Field("to_user_id") toUserId: Int,
        @Field("message_id") messageId: String
    ): Call<SimpleAckResponse>

    @FormUrlEncoded
    @POST("chat_history")
    fun getChatHistory(
        @Field("user_id") userId: Int,
        @Field("receiver_id") receiverId: Int,
        @Field("limit") limit: Int = 10,
        @Field("offset") offset: Int = 0
    ): Call<ChatHistoryResponse>

    @FormUrlEncoded
    @POST("my_chat")
    fun getMyChat(
        @Field("user_id") userId: Int,
        @Field("search") search: String?,
        @Field("limit") limit: Int,
        @Field("offset") offset: Int
    ): Call<MyChatResponse>

    @FormUrlEncoded
    @POST("my_chat/friends")
    fun getMyChatFriends(
        @Field("user_id") userId: Int,
        @Field("search") search: String?,
        @Field("limit") limit: Int,
        @Field("offset") offset: Int
    ): Call<MyChatResponse>

    @FormUrlEncoded
    @POST("my_chat/general")
    fun getMyChatGeneral(
        @Field("user_id") userId: Int,
        @Field("search") search: String?,
        @Field("limit") limit: Int,
        @Field("offset") offset: Int
    ): Call<MyChatResponse>

    @FormUrlEncoded
    @POST("chat_block_user")
    fun blockChatUser(
        @Field("user_id") userId: Int,
        @Field("blocked_user_id") blockedUserId: Int
    ): Call<BlockUserResponse>

    @FormUrlEncoded
    @POST("chat_unblock_user")
    fun unblockChatUser(
        @Field("user_id") userId: Int,
        @Field("blocked_user_id") blockedUserId: Int
    ): Call<BlockUserResponse>

    @FormUrlEncoded
    @POST("fallback_send_message")
    fun fallbackSendMessage(
        @Field("from_user_id") fromUserId: Int,
        @Field("to_user_id") toUserId: Int,
        @Field("message") message: String,
        @Field("message_type") messageType: String? = null,
        @Field("attachment_url") attachmentUrl: String? = null
    ): Call<FallbackSendMessageResponse>

    @FormUrlEncoded
    @POST("check_call_availability")
    fun checkCallAvailability(
        @Field("male_user_id") maleUserId: Int,
        @Field("female_user_id") femaleUserId: Int
    ): Call<CheckCallAvailabilityResponse>

    @FormUrlEncoded
    @POST("add_message_reaction")
    fun addMessageReaction(
        @Field("user_id") userId: Int,
        @Field("message_id") messageId: Int,
        @Field("reaction_emoji") reactionEmoji: String?
    ): Call<AddReactionResponse>

    @FormUrlEncoded
    @POST("check_referral_offer")
    fun checkReferralOffer(
        @Field("user_id") userId: Int
    ): Call<CheckReferralOfferResponse>

    @FormUrlEncoded
    @POST("check_rating_prompt")
    fun checkRatingPrompt(
        @Field("user_id") userId: Int
    ): Call<RatingPromptResponse>

    @FormUrlEncoded
    @POST("update_rating_prompt_shown")
    fun updateRatingPromptShown(
        @Field("user_id") userId: Int,
        @Field("rating_submitted") ratingSubmitted: Int = 0
    ): Call<UpdateRatingPromptResponse>

    @FormUrlEncoded
    @POST("log-app-event")
    fun logAppEvent(
        @Field("event_name") eventName: String,
        @Field("user_id") userId: Int?,
        @Field("platform") platform: String,
        @Field("event_params") eventParams: String?,
        @Field("event_value") eventValue: Double?,
        @Field("device_info") deviceInfo: String?,
        @Field("app_version") appVersion: String?,
        @Field("os_version") osVersion: String?
    ): Call<LogAppEventResponse>

    @FormUrlEncoded
    @POST("install-referrer")
    fun logInstallReferrer(
        @Field("response_data") responseData: String,
        @Field("device_info") deviceInfo: String?,
        @Field("app_version") appVersion: String?,
        @Field("os_version") osVersion: String?,
        @Field("user_id") userId: Int
    ): Call<InstallReferrerResponse>

    @FormUrlEncoded
    @POST("user-install-referrer")
    fun logUserInstallReferrer(
        @Field("user_id") userId: Int,
        @Field("response_data") responseData: String
    ): Call<InstallReferrerResponse>

    @FormUrlEncoded
    @POST("agora/token")
    fun getAgoraToken(
        @Field("channel_name") channelName: String,
        @Field("uid") uid: Int,
        @Field("role") role: String = "publisher",
        @Field("expire") expire: Int = 3600
    ): Call<AgoraTokenResponse>

    @FormUrlEncoded
    @POST("check_rating_eligibility")
    fun checkRatingEligibility(
        @Field("user_id") userId: Int
    ): Call<CheckRatingEligibilityResponse>

    @POST("call_drop_status")
    fun callDropStatus(
        @Body request: CallDropStatusRequest
    ): Call<CallDropStatusResponse>

    @POST("call_status")
    fun callStatus(
        @Body request: CallStatusRequest
    ): Call<CallStatusResponse>

    @FormUrlEncoded
    @POST("submit_rating")
    fun submitRating(
        @Field("user_id") userId: Int,
        @Field("star_count") starCount: Int,
        @Field("description") description: String?
    ): Call<SubmitRatingResponse>

    // ===== IPL Rooms =====

    @FormUrlEncoded
    @POST("ipl-rooms-list")
    fun getIplRooms(
        @Field("user_id") userId: Int,
        @Field("language") language: String? = null,
        @Field("limit") limit: Int = 10,
        @Field("offset") offset: Int = 0
    ): Call<IplRoomsListResponse>

    @FormUrlEncoded
    @POST("ipl-rooms-create")
    fun createIplRoom(
        @Field("user_id") userId: Int,
        @Field("room_name") roomName: String,
        @Field("team_a") teamA: String,
        @Field("team_b") teamB: String,
        @Field("creator_team") creatorTeam: String
    ): Call<IplRoomCreateResponse>

    @FormUrlEncoded
    @POST("ipl-rooms-join")
    fun joinIplRoom(
        @Field("user_id") userId: Int,
        @Field("room_id") roomId: Int
    ): Call<IplRoomJoinResponse>

    @FormUrlEncoded
    @POST("ipl-rooms-leave")
    fun leaveIplRoom(
        @Field("user_id") userId: Int,
        @Field("room_id") roomId: Int
    ): Call<IplRoomLeaveResponse>

    @FormUrlEncoded
    @POST("ipl-rooms-details")
    fun getIplRoomDetails(
        @Field("room_id") roomId: Int
    ): Call<IplRoomDetailResponse>

    @FormUrlEncoded
    @POST("ipl-rooms-reaction")
    fun sendIplReaction(
        @Field("user_id") userId: Int,
        @Field("room_id") roomId: Int,
        @Field("reaction_type") reactionType: String
    ): Call<IplRoomReactionResponse>

    @FormUrlEncoded
    @POST("ipl-rooms-mute")
    fun toggleIplMute(
        @Field("user_id") userId: Int,
        @Field("room_id") roomId: Int,
        @Field("is_muted") isMuted: Int
    ): Call<IplRoomMuteResponse>

    @POST("ipl-rooms-matches")
    fun getIplMatchSuggestions(): Call<IplMatchSuggestionsResponse>

    @FormUrlEncoded
    @POST("ipl-rooms-join-by-code")
    fun joinIplRoomByCode(
        @Field("user_id") userId: Int,
        @Field("invite_code") inviteCode: String
    ): Call<IplRoomJoinResponse>

    @FormUrlEncoded
    @POST("ipl-rooms-join-random")
    fun joinIplRoomRandom(
        @Field("user_id") userId: Int
    ): Call<IplRoomJoinResponse>

    @FormUrlEncoded
    @POST("ipl-rooms-close")
    fun closeIplRoom(
        @Field("user_id") userId: Int,
        @Field("room_id") roomId: Int
    ): Call<IplRoomLeaveResponse>

    @FormUrlEncoded
    @POST("ipl-rooms-listener-join")
    fun listenerJoin(
        @Field("user_id") userId: Int,
        @Field("room_id") roomId: Int
    ): Call<IplRoomLeaveResponse>

    @FormUrlEncoded
    @POST("ipl-rooms-listener-leave")
    fun listenerLeave(
        @Field("user_id") userId: Int,
        @Field("room_id") roomId: Int
    ): Call<IplRoomLeaveResponse>

    @FormUrlEncoded
    @POST("ipl-rooms-lookup-code")
    fun lookupRoomByCode(
        @Field("invite_code") inviteCode: String
    ): Call<IplRoomJoinResponse>

    @FormUrlEncoded
    @POST("ipl-rooms-lookup-random")
    fun lookupRoomRandom(
        @Field("user_id") userId: Int
    ): Call<IplRoomJoinResponse>

    @FormUrlEncoded
    @POST("update_ipl_team")
    fun updateIplTeam(
        @Field("user_id") userId: Int,
        @Field("ipl_team") iplTeam: String
    ): Call<UpdateIplTeamResponse>

    // AI Onboarding
    @FormUrlEncoded
    @POST("ai_onboarding_start")
    fun aiOnboardingStart(
        @Field("user_id") userId: Int,
        @Field("concern") concern: String
    ): Call<AiOnboardingStartResponse>

    @FormUrlEncoded
    @POST("ai_onboarding_reply")
    fun aiOnboardingReply(
        @Field("session_id") sessionId: Int,
        @Field("user_message") userMessage: String
    ): Call<AiOnboardingReplyResponse>

    @FormUrlEncoded
    @POST("ai_onboarding_complete")
    fun aiOnboardingComplete(
        @Field("session_id") sessionId: Int
    ): Call<AiOnboardingCompleteResponse>

}