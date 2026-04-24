package com.darkwizards.payments.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SaleResponse(
    val transactionId: String,
    val approvedAmount: String,   // cents
    val feeAmount: String,        // cents
    val approvalNumber: String,
    val accountType: String,
    val accountFirst6: String,
    val accountLast4: String
)
