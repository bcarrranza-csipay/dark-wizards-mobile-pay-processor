package com.darkwizards.payments.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SaleRequest(
    val bearerToken: String,
    val terminalId: String,
    val accountInfo: AccountInfo,
    val totalAmount: String // cents as string
)

@Serializable
data class AccountInfo(
    val accountNumber: String,
    val accountType: String,
    val accountAccessory: String // expiry MM.YYYY
)
