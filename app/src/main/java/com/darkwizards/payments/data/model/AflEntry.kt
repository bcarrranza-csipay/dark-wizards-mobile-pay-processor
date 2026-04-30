package com.darkwizards.payments.data.model

/**
 * Represents a single entry in the Application File Locator (AFL), which specifies
 * which records to read from which Short File Identifiers (SFIs).
 *
 * @param sfi         Short File Identifier (1–30)
 * @param firstRecord First record number to read (1–255)
 * @param lastRecord  Last record number to read (1–255, inclusive)
 */
data class AflEntry(
    val sfi: Int,
    val firstRecord: Int,
    val lastRecord: Int
)
