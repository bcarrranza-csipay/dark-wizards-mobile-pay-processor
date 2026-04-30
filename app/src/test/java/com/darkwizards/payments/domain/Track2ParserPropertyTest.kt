package com.darkwizards.payments.domain

// Feature: nfc-tap-to-pay, Property 14: Track2 round-trip

import com.darkwizards.payments.util.expiry
import com.darkwizards.payments.util.pan
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll

/**
 * Property-based tests for [Track2Parser].
 *
 * **Validates: Requirements 11.6**
 */
class Track2ParserPropertyTest : FunSpec({

    // -------------------------------------------------------------------------
    // Property 14: Track2 round-trip
    // -------------------------------------------------------------------------
    // For any PAN string (13–19 digits) and expiry (YYMM format), encoding them
    // as a Track2_Equivalent byte array and parsing with Track2Parser.parse SHALL
    // recover the original PAN and expiry values exactly.
    //
    // Strategy:
    //   1. Generate a PAN (13–19 decimal digits) and a YYMM expiry string.
    //   2. Encode them into a Track2_Equivalent ByteArray using nibble-packing
    //      with separator nibble 0xD between PAN and YYMM, padded with 0xF if
    //      the total nibble count is odd.
    //   3. Parse the encoded bytes with Track2Parser.parse.
    //   4. Assert the result is a success.
    //   5. Assert pan == originalPan.
    //   6. Assert expiry == expectedExpiry (YYMM "2612" → "12.2026").
    // -------------------------------------------------------------------------

    test("Property 14: Track2 round-trip — encode then parse recovers original PAN and expiry") {
        // Feature: nfc-tap-to-pay, Property 14: Track2 round-trip
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.pan(),
            Arb.expiry()
        ) { pan, yymm ->
            // Step 1: encode PAN + YYMM into Track2_Equivalent bytes
            val encoded = encodeTrack2(pan, yymm)

            // Step 2: parse the encoded bytes
            val result = Track2Parser.parse(encoded)

            // Step 3: assert success
            result.isSuccess shouldBe true
            val track2Data = result.getOrThrow()

            // Step 4: assert PAN is recovered exactly
            track2Data.pan shouldBe pan

            // Step 5: assert expiry is recovered in MM.YYYY format
            // YYMM "2612" → MM=12, YY=26 → "12.2026"
            val expectedExpiry = convertYymmToMmYyyy(yymm)
            track2Data.expiry shouldBe expectedExpiry
        }
    }
})

// ---------------------------------------------------------------------------
// Helper: encode PAN + YYMM into Track2_Equivalent ByteArray
// ---------------------------------------------------------------------------

/**
 * Encodes a PAN and YYMM expiry string into a Track2_Equivalent [ByteArray]
 * following the nibble-packing rules of EMV Tag 57.
 *
 * Encoding rules:
 * - Each byte holds two 4-bit nibbles (high nibble first).
 * - PAN digits are packed as nibbles.
 * - Separator nibble 0xD follows the PAN.
 * - YYMM digits are packed as nibbles after the separator.
 * - If the total nibble count is odd, a padding nibble 0xF is appended.
 *
 * Example: PAN="4111111111111111", YYMM="2612"
 *   Nibbles: 4,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1, D, 2,6,1,2
 *   Total = 21 (odd) → pad with F → 22 nibbles = 11 bytes
 *   Bytes: 41 11 11 11 11 11 11 11 D2 61 2F
 */
private fun encodeTrack2(pan: String, yymm: String): ByteArray {
    // Build the nibble list: PAN digits + separator + YYMM digits
    val nibbles = mutableListOf<Int>()
    for (ch in pan) {
        nibbles.add(ch.digitToInt())
    }
    nibbles.add(0xD) // separator nibble
    for (ch in yymm) {
        nibbles.add(ch.digitToInt())
    }

    // Pad with 0xF if the total nibble count is odd
    if (nibbles.size % 2 != 0) {
        nibbles.add(0xF)
    }

    // Pack pairs of nibbles into bytes
    val bytes = ByteArray(nibbles.size / 2)
    for (i in bytes.indices) {
        val high = nibbles[i * 2]
        val low = nibbles[i * 2 + 1]
        bytes[i] = ((high shl 4) or low).toByte()
    }
    return bytes
}

// ---------------------------------------------------------------------------
// Helper: convert YYMM to MM.YYYY
// ---------------------------------------------------------------------------

/**
 * Converts a YYMM expiry string (e.g., "2612") to the MM.YYYY format
 * expected by [Track2Parser] (e.g., "12.2026").
 *
 * - YY = first two characters → full year = "20" + YY
 * - MM = last two characters
 */
private fun convertYymmToMmYyyy(yymm: String): String {
    val yy = yymm.substring(0, 2)
    val mm = yymm.substring(2, 4)
    return "$mm.20$yy"
}
