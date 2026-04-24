package com.darkwizards.payments.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SettleResponse(
    val message: String,
    val settled: Int
)
