package com.gmwapp.hima.activities

import com.gmwapp.hima.retrofit.responses.NewRazorpayLinkResponse
import com.gmwapp.hima.retrofit.responses.PaySprintBankVerifyResponse
import com.gmwapp.hima.retrofit.responses.PaySprintPanVerifyResponse
import com.gmwapp.hima.retrofit.responses.RazorPayApiResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface ApiService {
    @FormUrlEncoded
    @POST("dwpay/add_coins_requests.php")
    fun addCoins(
        @Field("buyer_name") name: String,
        @Field("amount") amount: String,
        @Field("email") email: String,
        @Field("phone") mobile: String,
        @Field("purpose") userId: String
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("razorpay/add_coins_requests.php")
    fun addCoinsRazorPay(
        @Field("reference_id") referenceId: String,
        @Field("buyer_name") buyerName: String,
        @Field("amount") amount: String,
        @Field("email") email: String,
        @Field("phone") phone: String
    ): Call<RazorPayApiResponse>

    @FormUrlEncoded
    @POST("https://himaapp.in/api/create-upi-payment-link")
    fun callNewRazorPay(
        @Field("user_id") userId: Int,
        @Field("coin_id") coinId: String,
    ): Call<NewRazorpayLinkResponse>

    @FormUrlEncoded
    @POST("https://uat.paysprint.in/sprintverify-uat/api/v1/verification/pan_verify")
    fun callPaysprintPanVerify(
        @Header("Token") token: String,
        @Header("Authorisedkey") authorisedKey: String,
        @Field("refid") refId: String,
        @Field("pannumber") panNumber: String
    ): Call<PaySprintPanVerifyResponse>

    @FormUrlEncoded
    @POST("https://uat.paysprint.in/sprintverify-uat/api/v1/verification/bank_verify_v2")
    fun callPaysprintBankVerify(
        @Header("Token") token: String,
        @Header("Authorisedkey") authorisedKey: String,
        @Field("refid") refId: String,
        @Field("account_number") accountNumber: String,
        @Field("ifsc_code") ifscCode: String
    ): Call<PaySprintBankVerifyResponse>



}