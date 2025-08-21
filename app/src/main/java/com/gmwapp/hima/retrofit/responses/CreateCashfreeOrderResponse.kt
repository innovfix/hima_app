package com.gmwapp.hima.retrofit.responses

data class CreateCashfreeOrderResponse(
    val success: Boolean,
    val order_id: String,
    val payment_session_id: String,
    val amount: String,
    val order_response: OrderResponse
) {
    data class OrderResponse(
        val cf_order_id: Long,
        val created_at: String,
        val customer_details: CustomerDetails
    )

    data class CustomerDetails(
        val customer_id: String,
        val customer_name: String,
        val customer_email: String,
        val customer_phone: String,
        val customer_uid: String?
    )
}
