package com.darkwizards.payments.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RefundResponse(
    val transactionId: String,
    val referencedTransactionId: String,
    val approvedAmount: String // cents
)
