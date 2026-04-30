package com.darkwizards.payments.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for [Track2Parser].
 *
 * Validates: Requirements 11.6
 */
class Track2ParserTest : FunSpec({

    // -------------------------------------------------------------------------
    // Design example: 4111111111111111 D 2612 1010 0000 0000 0F
    // -------------------------------------------------------------------------
    // Bytes: 41 11 11 11 11 11 11 11 D2 61 21 01 00 00 00 00 0F
    //   PAN  = "4111111111111111"
    //   YYMM = nibbles 2,6,1,2  →  YY=26, MM=12  →  "12.2026"
    // -------------------------------------------------------------------------

    test("parse design example: 4111111111111111 D 2612 1010 0000 0000 0F") {
        // Nibbles: 4,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1, D, 2,6,1,2, 1,0,1,0, 0,0,0,0, 0,0,0,0, 0,F
        val bytes = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,  // PAN nibbles: 4111111111111111
            0xD2.toByte(),                                     // separator D + first expiry nibble 2
            0x61, 0x21,                                        // expiry nibbles: 6,1,2,1 → YYMM=2612, then service code 1
            0x01, 0x00, 0x00, 0x00, 0x00, 0x0F                // service code + discretionary data + padding
        )

        val result = Track2Parser.parse(bytes)

        result.shouldBeSuccess()
        val data = result.getOrThrow()
        data.pan shouldBe "4111111111111111"
        data.expiry shouldBe "12.2026"
    }

    // -------------------------------------------------------------------------
    // Minimal valid Track2: PAN + separator + YYMM only (no trailing data)
    // -------------------------------------------------------------------------

    test("parse minimal track2: short PAN with separator and expiry only") {
        // PAN = "4111", YYMM = "2612"
        // Nibbles: 4,1,1,1, D, 2,6,1,2
        // Total = 9 nibbles (odd) → pad with F → 10 nibbles = 5 bytes
        // Bytes: 41 11 D2 61 2F
        val bytes = byteArrayOf(
            0x41, 0x11,             // PAN: 4,1,1,1
            0xD2.toByte(),          // separator D + expiry nibble 2
            0x61,                   // expiry nibbles 6,1
            0x2F                    // expiry nibble 2 + padding F
        )

        val result = Track2Parser.parse(bytes)

        result.shouldBeSuccess()
        val data = result.getOrThrow()
        data.pan shouldBe "4111"
        data.expiry shouldBe "12.2026"
    }

    test("parse 19-digit PAN (maximum length)") {
        // PAN = "4111111111111111111" (19 digits), YYMM = "2512"
        // Nibbles: 19 PAN digits + D + 4 expiry = 24 nibbles (even) = 12 bytes
        val bytes = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, // 18 PAN nibbles
            0x1D.toByte(),  // PAN nibble 1 + separator D
            0x25, 0x12      // YYMM = 2512
        )

        val result = Track2Parser.parse(bytes)

        result.shouldBeSuccess()
        val data = result.getOrThrow()
        data.pan shouldBe "4111111111111111111"
        data.expiry shouldBe "12.2025"
    }

    test("parse Mastercard BIN example: 5500000000000004 D 2801") {
        // PAN = "5500000000000004", YYMM = "2801"
        // Nibbles: 5,5,0,0,0,0,0,0,0,0,0,0,0,0,0,4, D, 2,8,0,1
        // Total = 21 (odd) → pad F → 22 nibbles = 11 bytes
        val bytes = byteArrayOf(
            0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04, // PAN: 5500000000000004
            0xD2.toByte(),  // separator D + expiry nibble 2
            0x80.toByte(), 0x1F      // expiry nibbles 8,0,1 + padding F
        )

        val result = Track2Parser.parse(bytes)

        result.shouldBeSuccess()
        val data = result.getOrThrow()
        data.pan shouldBe "5500000000000004"
        data.expiry shouldBe "01.2028"
    }

    test("parse expiry year 2099 (YY=99)") {
        // PAN = "4111", YYMM = "9912"
        // Nibbles: 4,1,1,1, D, 9,9,1,2 → 9 nibbles (odd) → pad F
        val bytes = byteArrayOf(
            0x41, 0x11,             // PAN: 4,1,1,1
            0xD9.toByte(),          // separator D + expiry nibble 9
            0x91.toByte(),                   // expiry nibbles 9,1
            0x2F                    // expiry nibble 2 + padding F
        )

        val result = Track2Parser.parse(bytes)

        result.shouldBeSuccess()
        val data = result.getOrThrow()
        data.pan shouldBe "4111"
        data.expiry shouldBe "12.2099"
    }

    test("parse expiry month 01 (January)") {
        // PAN = "4111", YYMM = "2601"
        val bytes = byteArrayOf(
            0x41, 0x11,
            0xD2.toByte(),  // D + 2
            0x60,           // 6,0
            0x1F            // 1 + padding F
        )

        val result = Track2Parser.parse(bytes)

        result.shouldBeSuccess()
        val data = result.getOrThrow()
        data.expiry shouldBe "01.2026"
    }

    // -------------------------------------------------------------------------
    // Error: missing separator nibble
    // -------------------------------------------------------------------------

    test("parse returns failure when no separator nibble 0xD is present") {
        // All nibbles are decimal digits — no 0xD separator
        val bytes = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11  // only PAN digits, no D
        )

        val result = Track2Parser.parse(bytes)

        result.shouldBeFailure()
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    test("parse returns failure for empty byte array (no separator)") {
        val result = Track2Parser.parse(byteArrayOf())

        result.shouldBeFailure()
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    test("parse returns failure when data is all zeros (no separator nibble)") {
        val bytes = ByteArray(8) { 0x00.toByte() }

        val result = Track2Parser.parse(bytes)

        result.shouldBeFailure()
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    test("parse failure message mentions separator when separator is missing") {
        val bytes = byteArrayOf(0x41, 0x11, 0x11, 0x11)

        val result = Track2Parser.parse(bytes)

        result.shouldBeFailure()
        val message = result.exceptionOrNull()?.message?.lowercase() ?: ""
        // Message should describe the missing separator, not expose raw hex
        message.shouldContain("separator")
    }

    // -------------------------------------------------------------------------
    // Error: truncated expiry (fewer than 4 nibbles after separator)
    // -------------------------------------------------------------------------

    test("parse returns failure when separator is the last nibble (0 expiry nibbles)") {
        // Nibbles: 4,1,1,1, D  — separator at end, no expiry follows
        // Bytes: 41 11 1D
        val bytes = byteArrayOf(0x41, 0x11, 0x1D.toByte())

        val result = Track2Parser.parse(bytes)

        result.shouldBeFailure()
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    test("parse returns failure when only 1 nibble follows separator") {
        // Nibbles: 4,1,1,1, D, 2  — only 1 expiry nibble
        // Bytes: 41 11 D2
        val bytes = byteArrayOf(0x41, 0x11, 0xD2.toByte())

        val result = Track2Parser.parse(bytes)

        result.shouldBeFailure()
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    test("parse returns failure when only 2 nibbles follow separator") {
        // Nibbles: 4,1,1,1, D, 2,6  — only 2 expiry nibbles
        // Bytes: 41 11 D2 6F (F is padding, not an expiry nibble)
        // After separator D at index 4: nibbles[5]=2, nibbles[6]=6, nibbles[7]=F → only 2 real digits before padding
        // Actually the parser counts raw nibbles, so 3 nibbles follow (2,6,F) — still < 4
        val bytes = byteArrayOf(0x41, 0x11, 0xD2.toByte(), 0x6F)

        val result = Track2Parser.parse(bytes)

        result.shouldBeFailure()
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    test("parse returns failure when only 3 nibbles follow separator") {
        // Nibbles: 4,1,1,1, D, 2,6,1  — only 3 expiry nibbles
        // Bytes: 41 11 D2 61 (but 61 gives nibbles 6,1 — total after D: 2,6,1 = 3 nibbles)
        // Wait: D is at nibble index 4 (byte 2 high nibble), then:
        //   byte 2 low nibble = 2, byte 3 high = 6, byte 3 low = 1 → 3 nibbles after D
        val bytes = byteArrayOf(
            0x41, 0x11,             // nibbles: 4,1,1,1
            0xD2.toByte(),          // nibbles: D,2
            0x61                    // nibbles: 6,1
            // total after D: 2,6,1 = 3 nibbles → failure
        )

        val result = Track2Parser.parse(bytes)

        result.shouldBeFailure()
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    test("parse succeeds with exactly 4 nibbles after separator") {
        // Nibbles: 4,1,1,1, D, 2,6,1,2  — exactly 4 expiry nibbles
        // Total = 9 (odd) → pad F → bytes: 41 11 D2 61 2F
        val bytes = byteArrayOf(
            0x41, 0x11,
            0xD2.toByte(),
            0x61,
            0x2F
        )

        val result = Track2Parser.parse(bytes)

        result.shouldBeSuccess()
        val data = result.getOrThrow()
        data.pan shouldBe "4111"
        data.expiry shouldBe "12.2026"
    }

    test("parse failure message mentions expiry when expiry is truncated") {
        // Separator present but only 2 nibbles follow
        val bytes = byteArrayOf(0x41, 0x11, 0xD2.toByte(), 0x6F)

        val result = Track2Parser.parse(bytes)

        result.shouldBeFailure()
        val message = result.exceptionOrNull()?.message?.lowercase() ?: ""
        // Message should describe the truncated expiry
        message.shouldContain("nibble")
    }

    // -------------------------------------------------------------------------
    // PAN edge cases
    // -------------------------------------------------------------------------

    test("parse strips trailing 0xF padding nibbles from PAN") {
        // PAN with odd digit count (13 digits) → packed with trailing F before separator
        // PAN = "4111111111111" (13 digits), YYMM = "2612"
        // Nibbles: 4,1,1,1,1,1,1,1,1,1,1,1,1, D, 2,6,1,2
        // Total = 18 nibbles (even) = 9 bytes — no padding needed
        // Bytes: 41 11 11 11 11 11 1D 26 12
        val bytes = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, // nibbles: 4,1,1,1,1,1,1,1,1,1,1,1
            0x1D.toByte(),  // nibbles: 1 (last PAN digit) + D (separator)
            0x26, 0x12      // expiry nibbles: 2,6,1,2
        )

        val result = Track2Parser.parse(bytes)

        result.shouldBeSuccess()
        val data = result.getOrThrow()
        data.pan shouldBe "4111111111111"
        data.expiry shouldBe "12.2026"
    }

    test("parse strips trailing 0xF padding nibbles from PAN when PAN length is even but F is packed before separator") {
        // Simulate a card that packs an extra 0xF nibble before the separator
        // even though the PAN has an even number of digits.
        // PAN = "4111" (4 digits), then F padding, then separator D, then YYMM "2612"
        // Nibbles: 4,1,1,1, F, D, 2,6,1,2
        // Total = 10 nibbles (even) = 5 bytes
        // Bytes: 41 11 FD 26 12
        val bytes = byteArrayOf(
            0x41, 0x11,             // PAN nibbles: 4,1,1,1
            0xFD.toByte(),          // padding F + separator D
            0x26, 0x12              // expiry nibbles: 2,6,1,2
        )

        val result = Track2Parser.parse(bytes)

        result.shouldBeSuccess()
        val data = result.getOrThrow()
        // Trailing F padding before separator should be stripped from PAN
        data.pan shouldBe "4111"
        data.expiry shouldBe "12.2026"
    }

    test("parse PAN with all same digits") {
        // PAN = "1111111111111111" (16 ones), YYMM = "2612"
        val bytes = byteArrayOf(
            0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, // PAN: 1111111111111111
            0xD2.toByte(),  // separator D + expiry nibble 2
            0x61, 0x2F      // expiry nibbles 6,1,2 + padding F
        )

        val result = Track2Parser.parse(bytes)

        result.shouldBeSuccess()
        val data = result.getOrThrow()
        data.pan shouldBe "1111111111111111"
        data.expiry shouldBe "12.2026"
    }
})
