package com.gmwapp.hima.hdfcGateways

data class HdfcOrderStatusResponse(
    val success: Boolean,
    val data: OrderStatusData?
)

data class OrderStatusData(
    val status: String,
    val amount: Int,
    val order_id: String
)

