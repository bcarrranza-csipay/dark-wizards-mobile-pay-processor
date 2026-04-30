package com.darkwizards.payments.data.model

data class EmvCardData(
    val pan: String,                        // Primary Account Number (digits only)
    val expiry: String,                     // MM.YYYY format for Pyxis API
    val accountType: String,                // "Visa", "Mastercard", etc. (derived from BIN)
    val track2Equivalent: ByteArray,        // Raw Tag 57 value
    val applicationCryptogram: ByteArray,   // Tag 9F26 ARQC
    val cryptogramInfoData: Byte,           // Tag 9F27
    val aip: ByteArray,                     // Tag 82 Application Interchange Profile
    val cvmResult: CvmResult,               // Determined CVM
    val cdcvmPerformed: Boolean             // True if CDCVM indicator present
)

enum class CvmResult { ONLINE_PIN, SIGNATURE, NO_CVM, CDCVM }
