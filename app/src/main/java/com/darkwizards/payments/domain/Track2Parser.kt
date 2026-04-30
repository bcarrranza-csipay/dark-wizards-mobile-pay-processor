package com.darkwizards.payments.domain

import com.darkwizards.payments.data.model.Track2Data

/**
 * Pure Kotlin parser for EMV Track 2 Equivalent Data (Tag 57).
 * No Android dependencies.
 *
 * Track 2 encoding:
 *   PAN  D  YYMM  ServiceCode  DiscretionaryData  [0xF padding]
 *
 * Each byte contains two 4-bit nibbles:
 *   high nibble = byte ushr 4
 *   low  nibble = byte and 0x0F
 *
 * The separator nibble 0xD marks the boundary between PAN and expiry.
 * Trailing 0xF nibbles in the PAN are padding and are ignored.
 *
 * Example:
 *   Bytes: 41 11 11 11 11 11 11 11 D2 61 21 01 00 00 00 00 0F
 *   PAN  = "4111111111111111"
 *   YYMM = nibbles 2,6,1,2  →  YY=26, MM=12  →  "12.2026"
 */
object Track2Parser {

    private const val SEPARATOR_NIBBLE = 0xD
    private const val PADDING_NIBBLE = 0xF

    /**
     * Parses [track2Bytes] (raw value of EMV Tag 57) into a [Track2Data].
     *
     * @return [Result.success] with the parsed [Track2Data], or
     *         [Result.failure] with an [IllegalArgumentException] if:
     *         - no separator nibble 0xD is found, or
     *         - fewer than 4 nibbles follow the separator.
     */
    fun parse(track2Bytes: ByteArray): Result<Track2Data> {
        // Expand bytes into nibbles
        val nibbles = mutableListOf<Int>()
        for (byte in track2Bytes) {
            nibbles.add((byte.toInt() and 0xFF) ushr 4)   // high nibble
            nibbles.add(byte.toInt() and 0x0F)             // low nibble
        }

        // Find the separator nibble 0xD
        val sepIndex = nibbles.indexOf(SEPARATOR_NIBBLE)
        if (sepIndex < 0) {
            return Result.failure(
                IllegalArgumentException("Track 2 data contains no field separator nibble (0xD)")
            )
        }

        // PAN = nibbles before separator, stripping trailing 0xF padding
        val panNibbles = nibbles.subList(0, sepIndex).dropLastWhile { it == PADDING_NIBBLE }
        val pan = panNibbles.joinToString("") { it.toString() }

        // Expiry = next 4 nibbles after separator: YYMM
        val expiryStart = sepIndex + 1
        if (nibbles.size < expiryStart + 4) {
            return Result.failure(
                IllegalArgumentException(
                    "Track 2 data has fewer than 4 nibbles after the field separator"
                )
            )
        }

        val yy = nibbles[expiryStart].toString() + nibbles[expiryStart + 1].toString()
        val mm = nibbles[expiryStart + 2].toString() + nibbles[expiryStart + 3].toString()
        val expiry = "$mm.20$yy"

        return Result.success(Track2Data(pan = pan, expiry = expiry))
    }
}
