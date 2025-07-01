package com.gmwapp.hima.hdfcGateways

data class HdfcPaymentLinkResponse(
    val success: Boolean,
    val data: HdfcPaymentData?
)

data class HdfcPaymentData(
    val payment_links: HdfcPaymentLinks?,
    val order_id : String
)

data class HdfcPaymentLinks(
    val web: String
)