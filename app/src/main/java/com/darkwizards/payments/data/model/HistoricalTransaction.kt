package com.darkwizards.payments.data.model

/**
 * A transaction record returned by pyxis_get_all_transactions.
 * Used to restore transaction history from Redis on app startup.
 */
data class HistoricalTransaction(
    val transactionId: String,
    val type: String,
    val transactionStatus: String,
    val terminalId: String,
    val totalAmount: String,
    val approvedAmount: String,
    val feeAmount: String,
    val approvalNumber: String,
    val accountType: String,
    val accountFirst6: String,
    val accountLast4: String,
    val isDeclined: Boolean,
    val creationTime: String,
    val settlementDate: String? = null,
    val gatewayResponseCode: String = "",
    val gatewayResponseMessage: String = ""
)
