package com.darkwizards.payments.data.model

/**
 * Parsed Track 2 Equivalent Data extracted from EMV Tag 57.
 *
 * @param pan     Primary Account Number (digits only, no separators)
 * @param expiry  Expiry date in MM.YYYY format (e.g. "12.2026")
 */
data class Track2Data(
    val pan: String,
    val expiry: String
)
