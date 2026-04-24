package com.darkwizards.payments.data.model

import java.time.LocalDateTime

data class TransactionRecord(
    val transactionId: String,
    val amount: String,           // display format "$25.30"
    val amountCents: Int,
    val feeAmount: String,        // display format "$0.76"
    val dateTime: LocalDateTime,
    val paymentType: PaymentType,
    val status: TransactionStatus,
    val approvalNumber: String,
    val accountLast4: String,
    val accountType: String,
    val refundTransactionId: String? = null
)

enum class PaymentType {
    CARD_PRESENT,
    CARD_NOT_PRESENT
}

enum class TransactionStatus {
    APPROVED,
    DECLINED,
    VOIDED,
    REFUNDED
}
