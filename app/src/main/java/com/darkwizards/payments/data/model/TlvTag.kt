package com.darkwizards.payments.data.model

data class TlvTag(
    val tag: ByteArray,                             // 1 or 2 byte tag identifier
    val value: ByteArray,                           // raw value bytes
    val children: List<TlvTag> = emptyList()        // non-empty for constructed tags
)
