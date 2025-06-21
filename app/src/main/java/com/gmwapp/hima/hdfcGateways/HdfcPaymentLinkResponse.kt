package com.gmwapp.hima.hdfcGateways

data class HdfcPaymentLinkResponse(
    val success: Boolean,
    val data: HdfcPaymentData?
)

data class HdfcPaymentData(
    val payment_links: HdfcPaymentLinks?
)

data class HdfcPaymentLinks(
    val web: String
)