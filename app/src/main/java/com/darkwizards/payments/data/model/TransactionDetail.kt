package com.darkwizards.payments.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TransactionDetail(
    val transactionId: String,
    val type: String,
    val transactionStatus: String,
    val terminalId: String,
    val totalAmount: String,       // cents
    val approvedAmount: String,    // cents
    val feeAmount: String,         // cents
    val approvalNumber: String,
    val accountType: String,
    val accountFirst6: String,
    val accountLast4: String,
    val isDeclined: Boolean,
    val creationTime: String,
    val settlementDate: String? = null,
    val referencedTransactionId: String? = null
)
