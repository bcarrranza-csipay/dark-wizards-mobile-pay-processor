package com.darkwizards.payments.data.model

/**
 * Response from pyxis_get_mode tool.
 * Contains the current server mode and pre-seeded mock transactions
 * (only populated when mode == "mock").
 */
data class ModeResponse(
    val mode: String,                                    // "simulator" | "mock" | "live"
    val seededTransactions: List<SeededTransaction> = emptyList()
)

data class SeededTransaction(
    val transactionId: String,
    val type: String,
    val transactionStatus: String,
    val totalAmount: String,
    val approvedAmount: String,
    val feeAmount: String,
    val approvalNumber: String,
    val accountType: String,
    val accountFirst6: String,
    val accountLast4: String,
    val creationTime: String,
    val isDeclined: Boolean,
    val isMockSeed: Boolean = true
)
