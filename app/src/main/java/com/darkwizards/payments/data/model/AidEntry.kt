package com.darkwizards.payments.data.model

/**
 * Represents a payment application entry from the PPSE (Proximity Payment System Environment)
 * directory response.
 *
 * @param aid      The Application Identifier bytes (typically 5–16 bytes)
 * @param priority The priority indicator from the FCI template (lower value = higher priority)
 */
data class AidEntry(
    val aid: ByteArray,
    val priority: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AidEntry) return false
        return priority == other.priority && aid.contentEquals(other.aid)
    }

    override fun hashCode(): Int {
        var result = aid.contentHashCode()
        result = 31 * result + priority
        return result
    }
}
