package com.darkwizards.payments.data.model

import kotlinx.serialization.Serializable

@Serializable
data class McpError(
    val errorSource: String,
    val errorCode: String,
    val errorMsg: String
)
