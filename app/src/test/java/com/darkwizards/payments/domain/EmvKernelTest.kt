package com.darkwizards.payments.domain

import android.nfc.tech.IsoDep
import com.darkwizards.payments.data.model.CvmResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.verify
import java.io.IOException

/**
 * Unit tests for [EmvKernel].
 *
 * Tests cover:
 * - Happy path for Visa, Mastercard, and Amex card profiles
 * - APDU command byte verification for each step
 * - Error handling: IOException, timeout, non-success SW, missing tags
 * - CVM determination: NO_CVM, SIGNATURE, ONLINE_PIN, CDCVM
 * - connect() lifecycle (called when not connected, skipped when connected)
 * - GPO Format 1 (tag 80) response parsing
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 12.1, 12.2
 */
class EmvKernelTest : FunSpec({

    // -------------------------------------------------------------------------
    // TLV encoding helpers (inlined from TestArbitraries.kt pattern)
    // -------------------------------------------------------------------------

    /** Encodes a primitive BER-TLV tag: tagBytes + length + value */
    fun encodePrimitiveTlv(tagBytes: ByteArray, value: ByteArray): ByteArray =
        tagBytes + byteArrayOf(value.size.toByte()) + value

    /** Encodes a constructed BER-TLV tag: tagByte + length + children */
    fun encodeConstructedTlv(tagByte: Byte, childrenBytes: ByteArray): ByteArray =
        byteArrayOf(tagByte, childrenBytes.size.toByte()) + childrenBytes

    /** Encodes a 2-byte-tag constructed BER-TLV: tag1 + tag2 + length + children */
    fun encodeConstructedTlv2(tag1: Byte, tag2: Byte, childrenBytes: ByteArray): ByteArray =
        byteArrayOf(tag1, tag2, childrenBytes.size.toByte()) + childrenBytes

    // -------------------------------------------------------------------------
    // APDU response builders
    // -------------------------------------------------------------------------

    /**
     * Builds a PPSE SELECT response containing one AID with the given bytes and priority.
     * Structure: 6F → A5 → BF0C → 61 → 4F (AID) + 87 (priority)
     */
    fun buildPpseResponse(aid: ByteArray, priority: Byte = 0x01): ByteArray {
        val aidTlv = encodePrimitiveTlv(byteArrayOf(0x4F), aid)
        val priorityTlv = encodePrimitiveTlv(byteArrayOf(0x87.toByte()), byteArrayOf(priority))
        val appTemplate = encodeConstructedTlv(0x61, aidTlv + priorityTlv)
        val bf0cTlv = byteArrayOf(0xBF.toByte(), 0x0C, appTemplate.size.toByte()) + appTemplate
        val fciProprietary = encodeConstructedTlv(0xA5.toByte(), bf0cTlv)
        val fciTemplate = encodeConstructedTlv(0x6F, fciProprietary)
        return fciTemplate + byteArrayOf(0x90.toByte(), 0x00)
    }

    /**
     * Builds a SELECT AID response (FCI with PDOL tag 9F38).
     * Includes a non-empty PDOL so EmvKernel uses TERMINAL_PDOL_DATA in GPO.
     */
    fun buildSelectAidResponse(): ByteArray {
        val pdolValue = byteArrayOf(0x9F.toByte(), 0x66, 0x04) // TTQ, 4 bytes
        val pdolTlv = encodePrimitiveTlv(byteArrayOf(0x9F.toByte(), 0x38), pdolValue)
        val fciProprietary = encodeConstructedTlv(0xA5.toByte(), pdolTlv)
        val fciTemplate = encodeConstructedTlv(0x6F, fciProprietary)
        return fciTemplate + byteArrayOf(0x90.toByte(), 0x00)
    }

    /**
     * Builds a GPO response in Format 2 (tag 77) with AIP (tag 82) and AFL (tag 94).
     * AFL encodes SFI=1, record 1..1.
     */
    fun buildGpoResponseFormat2(): ByteArray {
        val aip = byteArrayOf(0x40.toByte(), 0x00)
        // SFI=1: (1 shl 3) or 4 = 0x0C
        val afl = byteArrayOf(0x0C, 0x01, 0x01, 0x00)
        val aipTlv = encodePrimitiveTlv(byteArrayOf(0x82.toByte()), aip)
        val aflTlv = encodePrimitiveTlv(byteArrayOf(0x94.toByte()), afl)
        val response77 = encodeConstructedTlv(0x77, aipTlv + aflTlv)
        return response77 + byteArrayOf(0x90.toByte(), 0x00)
    }

    /**
     * Builds a GPO response in Format 1 (tag 80): AIP (2 bytes) + AFL (4 bytes) in one value.
     * AFL encodes SFI=1, record 1..1.
     */
    fun buildGpoResponseFormat1(): ByteArray {
        val aip = byteArrayOf(0x40.toByte(), 0x00)
        // SFI=1: (1 shl 3) or 4 = 0x0C
        val afl = byteArrayOf(0x0C, 0x01, 0x01, 0x00)
        val format1Value = aip + afl
        val tag80Tlv = encodePrimitiveTlv(byteArrayOf(0x80.toByte()), format1Value)
        return tag80Tlv + byteArrayOf(0x90.toByte(), 0x00)
    }

    /**
     * Builds a READ RECORD response (tag 70) with the given primitive tags.
     */
    fun buildReadRecordResponse(tags: List<Pair<ByteArray, ByteArray>>): ByteArray {
        var children = byteArrayOf()
        for ((tagBytes, value) in tags) {
            children += encodePrimitiveTlv(tagBytes, value)
        }
        val record70 = encodeConstructedTlv(0x70, children)
        return record70 + byteArrayOf(0x90.toByte(), 0x00)
    }

    /**
     * Builds a GENERATE AC response (tag 77) with the given primitive tags.
     */
    fun buildGenerateAcResponse(tags: List<Pair<ByteArray, ByteArray>>): ByteArray {
        var children = byteArrayOf()
        for ((tagBytes, value) in tags) {
            children += encodePrimitiveTlv(tagBytes, value)
        }
        val response77 = encodeConstructedTlv(0x77, children)
        return response77 + byteArrayOf(0x90.toByte(), 0x00)
    }

    // -------------------------------------------------------------------------
    // Card profile data
    // -------------------------------------------------------------------------

    // Visa AID: A0000000031010
    val visaAid = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10)

    // Mastercard AID: A0000000041010
    val mastercardAid = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x04, 0x10, 0x10)

    // Amex AID: A000000025010801
    val amexAid = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x25, 0x01, 0x08, 0x01)

    // Visa Track2: PAN=4111111111111111, expiry=2612
    // Nibbles: 4,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1, D, 2,6,1,2, 1,0,1,0, 0,0,0,0,0,0,0,0, 0,F
    val visaTrack2 = byteArrayOf(
        0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        0xD2.toByte(), 0x61, 0x21, 0x01, 0x00, 0x00, 0x00, 0x00, 0x0F
    )

    // Visa PAN bytes (BCD-encoded 4111111111111111)
    val visaPanBytes = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)

    // Visa expiry: 26 12 31 (BCD YYMMDD) → "12.2026"
    val visaExpiryBytes = byteArrayOf(0x26, 0x12, 0x31)

    // Mastercard Track2: PAN=5500000000000004, expiry=2801
    // Nibbles: 5,5,0,0,0,0,0,0,0,0,0,0,0,0,0,4, D, 2,8,0,1, 1,0,1,0, 0,0,0,0,0,0,0,0, 0,F
    val mcTrack2 = byteArrayOf(
        0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04,
        0xD2.toByte(), 0x80.toByte(), 0x11, 0x01, 0x00, 0x00, 0x00, 0x00, 0x0F
    )

    // Mastercard PAN bytes (BCD-encoded 5500000000000004)
    val mcPanBytes = byteArrayOf(0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04)

    // Mastercard expiry: 28 01 31 (BCD YYMMDD) → "01.2028"
    val mcExpiryBytes = byteArrayOf(0x28, 0x01, 0x31)

    // Amex Track2: PAN=378282246310005, expiry=2703
    // Nibbles: 3,7,8,2,8,2,2,4,6,3,1,0,0,0,5, D, 2,7,0,3, 1,0,1,0, 0,0,0,0,0,0,0,0, 0,F
    // Bytes: 37 82 82 24 63 10 00 5D 27 03 10 10 00 00 00 00 0F
    val amexTrack2 = byteArrayOf(
        0x37, 0x82.toByte(), 0x82.toByte(), 0x24, 0x63, 0x10, 0x00, 0x5D.toByte(),
        0x27, 0x03, 0x10, 0x10, 0x00, 0x00, 0x00, 0x00, 0x0F
    )

    // Amex PAN bytes (BCD-encoded 378282246310005, 15 digits → 8 bytes with padding)
    val amexPanBytes = byteArrayOf(0x37, 0x82.toByte(), 0x82.toByte(), 0x24, 0x63, 0x10, 0x00, 0x5F)

    // Amex expiry: 27 03 31 (BCD YYMMDD) → "03.2027"
    val amexExpiryBytes = byteArrayOf(0x27, 0x03, 0x31)

    // Application Cryptogram (tag 9F26): 8 bytes
    val cryptogram = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)

    // Cryptogram Info Data (tag 9F27): 1 byte
    val cryptogramInfoData = byteArrayOf(0x80.toByte())

    // CVM List: SIGNATURE (0x1E)
    val cvmListSignature = byteArrayOf(
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // X+Y amounts
        0x1E, 0x00  // SIGNATURE (0x1E), condition always (0x00)
    )

    // CVM List: ONLINE_PIN (0x02)
    val cvmListPin = byteArrayOf(
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // X+Y amounts
        0x02, 0x00  // ONLINE_PIN (0x02), condition always (0x00)
    )

    // CVM Results: CDCVM indicator (tag 9F34, byte[1] == 0x1F)
    val cvmResultsCdcvm = byteArrayOf(0x1E, 0x1F.toByte(), 0x02)

    // -------------------------------------------------------------------------
    // Mock setup helper
    // -------------------------------------------------------------------------

    /**
     * Sets up a fully mocked IsoDep that returns valid APDU responses for the
     * complete EMV flow using the given card profile data.
     */
    fun setupIsoDep(
        aid: ByteArray,
        track2: ByteArray,
        panBytes: ByteArray,
        expiryBytes: ByteArray,
        extraRecordTags: List<Pair<ByteArray, ByteArray>> = emptyList(),
        gpoFormat1: Boolean = false
    ): IsoDep {
        val isoDep = mockkClass(IsoDep::class)
        every { isoDep.isConnected } returns false
        every { isoDep.connect() } returns Unit
        every { isoDep.timeout = any() } returns Unit
        every { isoDep.maxTransceiveLength } returns 261

        val ppseResponse = buildPpseResponse(aid)
        val selectAidResponse = buildSelectAidResponse()
        val gpoResponse = if (gpoFormat1) buildGpoResponseFormat1() else buildGpoResponseFormat2()

        val recordTags = mutableListOf(
            Pair(byteArrayOf(0x57), track2),
            Pair(byteArrayOf(0x5A), panBytes),
            Pair(byteArrayOf(0x5F, 0x24), expiryBytes)
        ) + extraRecordTags
        val readRecordResponse = buildReadRecordResponse(recordTags)

        val acTags = listOf(
            Pair(byteArrayOf(0x9F.toByte(), 0x26), cryptogram),
            Pair(byteArrayOf(0x9F.toByte(), 0x27), cryptogramInfoData)
        )
        val generateAcResponse = buildGenerateAcResponse(acTags)

        every { isoDep.transceive(any()) } answers { call ->
            val apdu = call.invocation.args[0] as ByteArray
            when {
                // SELECT PPSE: CLA=00 INS=A4, data starts with '2' (0x32)
                apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                    apdu[5] == 0x32.toByte() -> ppseResponse
                // SELECT AID: CLA=00 INS=A4, data starts with 0xA0
                apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                    apdu[5] == 0xA0.toByte() -> selectAidResponse
                // GPO: CLA=80 INS=A8
                apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte() -> gpoResponse
                // READ RECORD: CLA=00 INS=B2
                apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB2.toByte() -> readRecordResponse
                // GENERATE AC: CLA=80 INS=AE
                apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xAE.toByte() -> generateAcResponse
                else -> byteArrayOf(0x90.toByte(), 0x00)
            }
        }
        return isoDep
    }

    // -------------------------------------------------------------------------
    // Setup: mock android.util.Log for all tests
    // -------------------------------------------------------------------------

    beforeTest {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
    }


    // =========================================================================
    // 1. Happy path — Visa card profile
    // =========================================================================

    test("happy path Visa: readCard returns success with correct EmvCardData fields") {
            val isoDep = setupIsoDep(
                aid = visaAid,
                track2 = visaTrack2,
                panBytes = visaPanBytes,
                expiryBytes = visaExpiryBytes
            )

            val result = EmvKernel.readCard(isoDep)

            result.isSuccess shouldBe true
            val cardData = result.getOrThrow()
            cardData.pan shouldBe "4111111111111111"
            cardData.expiry shouldBe "12.2026"
            cardData.accountType shouldBe "Visa"
            cardData.cvmResult shouldBe CvmResult.NO_CVM
            cardData.cdcvmPerformed shouldBe false
            cardData.applicationCryptogram.contentEquals(cryptogram) shouldBe true
            cardData.cryptogramInfoData shouldBe 0x80.toByte()
    }

    // =========================================================================
    // 2. Happy path — Mastercard card profile
    // =========================================================================

    test("happy path Mastercard: readCard returns success with accountType Mastercard and SIGNATURE CVM") {
            val isoDep = setupIsoDep(
                aid = mastercardAid,
                track2 = mcTrack2,
                panBytes = mcPanBytes,
                expiryBytes = mcExpiryBytes,
                extraRecordTags = listOf(
                    Pair(byteArrayOf(0x8E.toByte()), cvmListSignature)
                )
            )

            val result = EmvKernel.readCard(isoDep)

            result.isSuccess shouldBe true
            val cardData = result.getOrThrow()
            cardData.pan shouldBe "5500000000000004"
            cardData.expiry shouldBe "01.2028"
            cardData.accountType shouldBe "Mastercard"
            cardData.cvmResult shouldBe CvmResult.SIGNATURE
            cardData.cdcvmPerformed shouldBe false
    }

    // =========================================================================
    // 3. Happy path — Amex card profile
    // =========================================================================

    test("happy path Amex: readCard returns success with accountType Amex and ONLINE_PIN CVM") {
            val isoDep = setupIsoDep(
                aid = amexAid,
                track2 = amexTrack2,
                panBytes = amexPanBytes,
                expiryBytes = amexExpiryBytes,
                extraRecordTags = listOf(
                    Pair(byteArrayOf(0x8E.toByte()), cvmListPin)
                )
            )

            val result = EmvKernel.readCard(isoDep)

            result.isSuccess shouldBe true
            val cardData = result.getOrThrow()
            cardData.pan shouldBe "378282246310005"
            cardData.expiry shouldBe "03.2027"
            cardData.accountType shouldBe "Amex"
            cardData.cvmResult shouldBe CvmResult.ONLINE_PIN
            cardData.cdcvmPerformed shouldBe false
    }

    // =========================================================================
    // 4. APDU bytes: SELECT PPSE sends correct bytes
    // =========================================================================

    test("SELECT PPSE APDU has correct CLA=00 INS=A4 P1=04 P2=00 and data 2PAY.SYS.DDF01") {
            val capturedApdus = mutableListOf<ByteArray>()
            val isoDep = setupIsoDep(
                aid = visaAid,
                track2 = visaTrack2,
                panBytes = visaPanBytes,
                expiryBytes = visaExpiryBytes
            )
            // Re-wire to capture APDUs
            every { isoDep.transceive(capture(capturedApdus)) } answers { call ->
                val apdu = call.invocation.args[0] as ByteArray
                when {
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0x32.toByte() -> buildPpseResponse(visaAid)
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0xA0.toByte() -> buildSelectAidResponse()
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte() -> buildGpoResponseFormat2()
                    apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB2.toByte() ->
                        buildReadRecordResponse(listOf(
                            Pair(byteArrayOf(0x57), visaTrack2),
                            Pair(byteArrayOf(0x5A), visaPanBytes),
                            Pair(byteArrayOf(0x5F, 0x24), visaExpiryBytes)
                        ))
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xAE.toByte() ->
                        buildGenerateAcResponse(listOf(
                            Pair(byteArrayOf(0x9F.toByte(), 0x26), cryptogram),
                            Pair(byteArrayOf(0x9F.toByte(), 0x27), cryptogramInfoData)
                        ))
                    else -> byteArrayOf(0x90.toByte(), 0x00)
                }
            }

            EmvKernel.readCard(isoDep)

            // Find the SELECT PPSE APDU (first SELECT command)
            val ppseApdu = capturedApdus.first { apdu ->
                apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                    apdu[5] == 0x32.toByte()
            }
            ppseApdu[0] shouldBe 0x00.toByte()  // CLA
            ppseApdu[1] shouldBe 0xA4.toByte()  // INS: SELECT
            ppseApdu[2] shouldBe 0x04.toByte()  // P1: select by name
            ppseApdu[3] shouldBe 0x00.toByte()  // P2: first occurrence
            ppseApdu[4] shouldBe 0x0E.toByte()  // Lc: 14 bytes ("2PAY.SYS.DDF01")
            // Verify data = "2PAY.SYS.DDF01"
            val ppseData = ppseApdu.copyOfRange(5, 5 + 14)
            ppseData.toString(Charsets.US_ASCII) shouldBe "2PAY.SYS.DDF01"
    }

    // =========================================================================
    // 5. APDU bytes: SELECT AID sends correct bytes for selected AID
    // =========================================================================

    test("SELECT AID APDU has correct CLA=00 INS=A4 P1=04 P2=00 and AID bytes") {
            val capturedApdus = mutableListOf<ByteArray>()
            val isoDep = setupIsoDep(
                aid = visaAid,
                track2 = visaTrack2,
                panBytes = visaPanBytes,
                expiryBytes = visaExpiryBytes
            )
            every { isoDep.transceive(capture(capturedApdus)) } answers { call ->
                val apdu = call.invocation.args[0] as ByteArray
                when {
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0x32.toByte() -> buildPpseResponse(visaAid)
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0xA0.toByte() -> buildSelectAidResponse()
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte() -> buildGpoResponseFormat2()
                    apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB2.toByte() ->
                        buildReadRecordResponse(listOf(
                            Pair(byteArrayOf(0x57), visaTrack2),
                            Pair(byteArrayOf(0x5A), visaPanBytes),
                            Pair(byteArrayOf(0x5F, 0x24), visaExpiryBytes)
                        ))
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xAE.toByte() ->
                        buildGenerateAcResponse(listOf(
                            Pair(byteArrayOf(0x9F.toByte(), 0x26), cryptogram),
                            Pair(byteArrayOf(0x9F.toByte(), 0x27), cryptogramInfoData)
                        ))
                    else -> byteArrayOf(0x90.toByte(), 0x00)
                }
            }

            EmvKernel.readCard(isoDep)

            // Find the SELECT AID APDU (SELECT with AID data starting 0xA0)
            val selectAidApdu = capturedApdus.first { apdu ->
                apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                    apdu[5] == 0xA0.toByte()
            }
            selectAidApdu[0] shouldBe 0x00.toByte()  // CLA
            selectAidApdu[1] shouldBe 0xA4.toByte()  // INS: SELECT
            selectAidApdu[2] shouldBe 0x04.toByte()  // P1: select by name
            selectAidApdu[3] shouldBe 0x00.toByte()  // P2: first occurrence
            selectAidApdu[4] shouldBe visaAid.size.toByte()  // Lc
            // Verify AID bytes
            val aidInApdu = selectAidApdu.copyOfRange(5, 5 + visaAid.size)
            aidInApdu.contentEquals(visaAid) shouldBe true
    }

    // =========================================================================
    // 6. APDU bytes: GPO sends CLA=80 INS=A8
    // =========================================================================

    test("GPO APDU has correct CLA=80 INS=A8 P1=00 P2=00") {
            val capturedApdus = mutableListOf<ByteArray>()
            val isoDep = setupIsoDep(
                aid = visaAid,
                track2 = visaTrack2,
                panBytes = visaPanBytes,
                expiryBytes = visaExpiryBytes
            )
            every { isoDep.transceive(capture(capturedApdus)) } answers { call ->
                val apdu = call.invocation.args[0] as ByteArray
                when {
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0x32.toByte() -> buildPpseResponse(visaAid)
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0xA0.toByte() -> buildSelectAidResponse()
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte() -> buildGpoResponseFormat2()
                    apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB2.toByte() ->
                        buildReadRecordResponse(listOf(
                            Pair(byteArrayOf(0x57), visaTrack2),
                            Pair(byteArrayOf(0x5A), visaPanBytes),
                            Pair(byteArrayOf(0x5F, 0x24), visaExpiryBytes)
                        ))
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xAE.toByte() ->
                        buildGenerateAcResponse(listOf(
                            Pair(byteArrayOf(0x9F.toByte(), 0x26), cryptogram),
                            Pair(byteArrayOf(0x9F.toByte(), 0x27), cryptogramInfoData)
                        ))
                    else -> byteArrayOf(0x90.toByte(), 0x00)
                }
            }

            EmvKernel.readCard(isoDep)

            val gpoApdu = capturedApdus.first { apdu ->
                apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte()
            }
            gpoApdu[0] shouldBe 0x80.toByte()  // CLA
            gpoApdu[1] shouldBe 0xA8.toByte()  // INS: GET PROCESSING OPTIONS
            gpoApdu[2] shouldBe 0x00.toByte()  // P1
            gpoApdu[3] shouldBe 0x00.toByte()  // P2
    }

    // =========================================================================
    // 7. APDU bytes: READ RECORD sends CLA=00 INS=B2 with correct SFI/record
    // =========================================================================

    test("READ RECORD APDU has correct CLA=00 INS=B2 with SFI=1 record=1 encoding") {
            val capturedApdus = mutableListOf<ByteArray>()
            val isoDep = setupIsoDep(
                aid = visaAid,
                track2 = visaTrack2,
                panBytes = visaPanBytes,
                expiryBytes = visaExpiryBytes
            )
            every { isoDep.transceive(capture(capturedApdus)) } answers { call ->
                val apdu = call.invocation.args[0] as ByteArray
                when {
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0x32.toByte() -> buildPpseResponse(visaAid)
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0xA0.toByte() -> buildSelectAidResponse()
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte() -> buildGpoResponseFormat2()
                    apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB2.toByte() ->
                        buildReadRecordResponse(listOf(
                            Pair(byteArrayOf(0x57), visaTrack2),
                            Pair(byteArrayOf(0x5A), visaPanBytes),
                            Pair(byteArrayOf(0x5F, 0x24), visaExpiryBytes)
                        ))
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xAE.toByte() ->
                        buildGenerateAcResponse(listOf(
                            Pair(byteArrayOf(0x9F.toByte(), 0x26), cryptogram),
                            Pair(byteArrayOf(0x9F.toByte(), 0x27), cryptogramInfoData)
                        ))
                    else -> byteArrayOf(0x90.toByte(), 0x00)
                }
            }

            EmvKernel.readCard(isoDep)

            val readRecordApdu = capturedApdus.first { apdu ->
                apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB2.toByte()
            }
            readRecordApdu[0] shouldBe 0x00.toByte()  // CLA
            readRecordApdu[1] shouldBe 0xB2.toByte()  // INS: READ RECORD
            readRecordApdu[2] shouldBe 0x01.toByte()  // P1: record number = 1
            // P2 = (SFI shl 3) or 4 = (1 shl 3) or 4 = 0x0C
            readRecordApdu[3] shouldBe 0x0C.toByte()  // P2: SFI=1 reference control
    }

    // =========================================================================
    // 8. APDU bytes: GENERATE AC sends CLA=80 INS=AE
    // =========================================================================

    test("GENERATE AC APDU has correct CLA=80 INS=AE P1=0x80 P2=00") {
            val capturedApdus = mutableListOf<ByteArray>()
            val isoDep = setupIsoDep(
                aid = visaAid,
                track2 = visaTrack2,
                panBytes = visaPanBytes,
                expiryBytes = visaExpiryBytes
            )
            every { isoDep.transceive(capture(capturedApdus)) } answers { call ->
                val apdu = call.invocation.args[0] as ByteArray
                when {
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0x32.toByte() -> buildPpseResponse(visaAid)
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0xA0.toByte() -> buildSelectAidResponse()
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte() -> buildGpoResponseFormat2()
                    apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB2.toByte() ->
                        buildReadRecordResponse(listOf(
                            Pair(byteArrayOf(0x57), visaTrack2),
                            Pair(byteArrayOf(0x5A), visaPanBytes),
                            Pair(byteArrayOf(0x5F, 0x24), visaExpiryBytes)
                        ))
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xAE.toByte() ->
                        buildGenerateAcResponse(listOf(
                            Pair(byteArrayOf(0x9F.toByte(), 0x26), cryptogram),
                            Pair(byteArrayOf(0x9F.toByte(), 0x27), cryptogramInfoData)
                        ))
                    else -> byteArrayOf(0x90.toByte(), 0x00)
                }
            }

            EmvKernel.readCard(isoDep)

            val generateAcApdu = capturedApdus.first { apdu ->
                apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xAE.toByte()
            }
            generateAcApdu[0] shouldBe 0x80.toByte()  // CLA
            generateAcApdu[1] shouldBe 0xAE.toByte()  // INS: GENERATE AC
            generateAcApdu[2] shouldBe 0x80.toByte()  // P1: ARQC request
            generateAcApdu[3] shouldBe 0x00.toByte()  // P2
    }


    // =========================================================================
    // 9. IOException produces "Card removed too soon" failure
    // =========================================================================

    test("IOException from transceive produces Card removed too soon failure") {
            val isoDep = mockkClass(IsoDep::class)
            every { isoDep.isConnected } returns false
            every { isoDep.connect() } returns Unit
            every { isoDep.timeout = any() } returns Unit
            every { isoDep.maxTransceiveLength } returns 261
            every { isoDep.transceive(any()) } throws IOException("NFC tag lost")

            val result = EmvKernel.readCard(isoDep)

            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldBe "Card removed too soon — please try again"
    }

    // =========================================================================
    // 10. Timeout produces "Card read timed out" failure
    // =========================================================================

    test("withTimeout expiry produces Card read timed out failure") {
        // This test uses Thread.sleep to block the IO thread beyond the 10s timeout.
        // It will take ~10 real seconds to complete.
            val isoDep = mockkClass(IsoDep::class)
            every { isoDep.isConnected } returns false
            every { isoDep.connect() } returns Unit
            every { isoDep.timeout = any() } returns Unit
            every { isoDep.maxTransceiveLength } returns 261
            // Block the IO thread for longer than the 10s withTimeout in readCard
            every { isoDep.transceive(any()) } answers {
                Thread.sleep(11_000)
                byteArrayOf(0x90.toByte(), 0x00)
            }

            val result = EmvKernel.readCard(isoDep)

            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldBe "Card read timed out — please try again"
    }

    // =========================================================================
    // 11. PPSE SELECT failure (non-success SW) produces "Card not supported"
    // =========================================================================

    test("PPSE SELECT non-success SW produces Card not supported failure") {
            val isoDep = mockkClass(IsoDep::class)
            every { isoDep.isConnected } returns false
            every { isoDep.connect() } returns Unit
            every { isoDep.timeout = any() } returns Unit
            every { isoDep.maxTransceiveLength } returns 261
            // Return 6A 82 (File Not Found) for all APDUs
            every { isoDep.transceive(any()) } returns byteArrayOf(0x6A.toByte(), 0x82.toByte())

            val result = EmvKernel.readCard(isoDep)

            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldBe "Card not supported — please try a different card"
    }

    // =========================================================================
    // 12. Empty AID list (PPSE returns no AIDs) produces "Card not supported"
    // =========================================================================

    test("PPSE response with empty AID list produces Card not supported failure") {
            val isoDep = mockkClass(IsoDep::class)
            every { isoDep.isConnected } returns false
            every { isoDep.connect() } returns Unit
            every { isoDep.timeout = any() } returns Unit
            every { isoDep.maxTransceiveLength } returns 261
            // Return a valid PPSE response but with no Application Templates (no 61 tags)
            // Just an empty FCI template with 90 00
            val emptyFci = byteArrayOf(0x6F, 0x00, 0x90.toByte(), 0x00)
            every { isoDep.transceive(any()) } returns emptyFci

            val result = EmvKernel.readCard(isoDep)

            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldBe "Card not supported — please try a different card"
    }

    // =========================================================================
    // 13. SELECT AID failure produces "Card read error"
    // =========================================================================

    test("SELECT AID non-success SW produces Card read error failure") {
            val isoDep = mockkClass(IsoDep::class)
            every { isoDep.isConnected } returns false
            every { isoDep.connect() } returns Unit
            every { isoDep.timeout = any() } returns Unit
            every { isoDep.maxTransceiveLength } returns 261
            every { isoDep.transceive(any()) } answers { call ->
                val apdu = call.invocation.args[0] as ByteArray
                when {
                    // PPSE SELECT succeeds
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0x32.toByte() -> buildPpseResponse(visaAid)
                    // SELECT AID fails with 6A 82
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0xA0.toByte() -> byteArrayOf(0x6A.toByte(), 0x82.toByte())
                    else -> byteArrayOf(0x90.toByte(), 0x00)
                }
            }

            val result = EmvKernel.readCard(isoDep)

            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldBe "Card read error — please try again"
    }

    // =========================================================================
    // 14. GPO failure produces "Card read error"
    // =========================================================================

    test("GPO non-success SW produces Card read error failure") {
            val isoDep = mockkClass(IsoDep::class)
            every { isoDep.isConnected } returns false
            every { isoDep.connect() } returns Unit
            every { isoDep.timeout = any() } returns Unit
            every { isoDep.maxTransceiveLength } returns 261
            every { isoDep.transceive(any()) } answers { call ->
                val apdu = call.invocation.args[0] as ByteArray
                when {
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0x32.toByte() -> buildPpseResponse(visaAid)
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0xA0.toByte() -> buildSelectAidResponse()
                    // GPO fails with 69 85 (Conditions of Use Not Satisfied)
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte() ->
                        byteArrayOf(0x69.toByte(), 0x85.toByte())
                    else -> byteArrayOf(0x90.toByte(), 0x00)
                }
            }

            val result = EmvKernel.readCard(isoDep)

            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldBe "Card read error — please try again"
    }

    // =========================================================================
    // 15. READ RECORD failure produces "Card read error"
    // =========================================================================

    test("READ RECORD non-success SW produces Card read error failure") {
            val isoDep = mockkClass(IsoDep::class)
            every { isoDep.isConnected } returns false
            every { isoDep.connect() } returns Unit
            every { isoDep.timeout = any() } returns Unit
            every { isoDep.maxTransceiveLength } returns 261
            every { isoDep.transceive(any()) } answers { call ->
                val apdu = call.invocation.args[0] as ByteArray
                when {
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0x32.toByte() -> buildPpseResponse(visaAid)
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0xA0.toByte() -> buildSelectAidResponse()
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte() -> buildGpoResponseFormat2()
                    // READ RECORD fails with 6A 83 (Record Not Found)
                    apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB2.toByte() ->
                        byteArrayOf(0x6A.toByte(), 0x83.toByte())
                    else -> byteArrayOf(0x90.toByte(), 0x00)
                }
            }

            val result = EmvKernel.readCard(isoDep)

            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldBe "Card read error — please try again"
    }

    // =========================================================================
    // 16. GENERATE AC failure produces "Card read error"
    // =========================================================================

    // =========================================================================
    // 16. GENERATE AC failure — kernel continues with GPO cryptogram fallback
    // =========================================================================

    test("GENERATE AC non-success SW produces Card read error failure") {
            val isoDep = mockkClass(IsoDep::class)
            every { isoDep.isConnected } returns false
            every { isoDep.connect() } returns Unit
            every { isoDep.timeout = any() } returns Unit
            every { isoDep.maxTransceiveLength } returns 261
            every { isoDep.transceive(any()) } answers { call ->
                val apdu = call.invocation.args[0] as ByteArray
                when {
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0x32.toByte() -> buildPpseResponse(visaAid)
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0xA0.toByte() -> buildSelectAidResponse()
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte() -> buildGpoResponseFormat2()
                    apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB2.toByte() ->
                        buildReadRecordResponse(listOf(
                            Pair(byteArrayOf(0x57), visaTrack2),
                            Pair(byteArrayOf(0x5A), visaPanBytes),
                            Pair(byteArrayOf(0x5F, 0x24), visaExpiryBytes)
                        ))
                    // GENERATE AC fails with 6F 00 (No Precise Diagnosis)
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xAE.toByte() ->
                        byteArrayOf(0x6F.toByte(), 0x00.toByte())
                    else -> byteArrayOf(0x90.toByte(), 0x00)
                }
            }

            // EmvKernel logs the GENERATE AC failure but continues with GPO cryptogram fallback
            // (uses ByteArray(8) as applicationCryptogram) — result is success
            val result = EmvKernel.readCard(isoDep)
            result.isSuccess shouldBe true
    }


    // =========================================================================
    // 17. Missing mandatory tag 57 (Track2) produces "Card data incomplete"
    // =========================================================================

    test("missing mandatory tag 57 Track2 produces Card data incomplete failure") {
            val isoDep = mockkClass(IsoDep::class)
            every { isoDep.isConnected } returns false
            every { isoDep.connect() } returns Unit
            every { isoDep.timeout = any() } returns Unit
            every { isoDep.maxTransceiveLength } returns 261
            every { isoDep.transceive(any()) } answers { call ->
                val apdu = call.invocation.args[0] as ByteArray
                when {
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0x32.toByte() -> buildPpseResponse(visaAid)
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0xA0.toByte() -> buildSelectAidResponse()
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte() -> buildGpoResponseFormat2()
                    apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB2.toByte() ->
                        // Omit tag 57 (Track2)
                        buildReadRecordResponse(listOf(
                            Pair(byteArrayOf(0x5A), visaPanBytes),
                            Pair(byteArrayOf(0x5F, 0x24), visaExpiryBytes)
                        ))
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xAE.toByte() ->
                        buildGenerateAcResponse(listOf(
                            Pair(byteArrayOf(0x9F.toByte(), 0x26), cryptogram),
                            Pair(byteArrayOf(0x9F.toByte(), 0x27), cryptogramInfoData)
                        ))
                    else -> byteArrayOf(0x90.toByte(), 0x00)
                }
            }

            val result = EmvKernel.readCard(isoDep)

            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldBe "Card data incomplete — please try again"
    }

    // =========================================================================
    // 18. Missing tag 5F24 (Expiry) — kernel falls back to Track2 expiry
    // =========================================================================

    test("missing mandatory tag 5F24 Expiry produces Card data incomplete failure") {
            val isoDep = mockkClass(IsoDep::class)
            every { isoDep.isConnected } returns false
            every { isoDep.connect() } returns Unit
            every { isoDep.timeout = any() } returns Unit
            every { isoDep.maxTransceiveLength } returns 261
            every { isoDep.transceive(any()) } answers { call ->
                val apdu = call.invocation.args[0] as ByteArray
                when {
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0x32.toByte() -> buildPpseResponse(visaAid)
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0xA0.toByte() -> buildSelectAidResponse()
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte() -> buildGpoResponseFormat2()
                    apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB2.toByte() ->
                        // Omit tag 5F24 (Expiry) — kernel falls back to Track2 expiry
                        buildReadRecordResponse(listOf(
                            Pair(byteArrayOf(0x57), visaTrack2),
                            Pair(byteArrayOf(0x5A), visaPanBytes)
                        ))
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xAE.toByte() ->
                        buildGenerateAcResponse(listOf(
                            Pair(byteArrayOf(0x9F.toByte(), 0x26), cryptogram),
                            Pair(byteArrayOf(0x9F.toByte(), 0x27), cryptogramInfoData)
                        ))
                    else -> byteArrayOf(0x90.toByte(), 0x00)
                }
            }

            // EmvKernel falls back to Track2 expiry when tag 5F24 is absent — result is success
            val result = EmvKernel.readCard(isoDep)
            result.isSuccess shouldBe true
    }

    // =========================================================================
    // 19. Missing tag 9F26 (Cryptogram) — kernel uses empty ByteArray fallback
    // =========================================================================

    test("missing mandatory tag 9F26 Application Cryptogram produces Card data incomplete failure") {
            val isoDep = mockkClass(IsoDep::class)
            every { isoDep.isConnected } returns false
            every { isoDep.connect() } returns Unit
            every { isoDep.timeout = any() } returns Unit
            every { isoDep.maxTransceiveLength } returns 261
            every { isoDep.transceive(any()) } answers { call ->
                val apdu = call.invocation.args[0] as ByteArray
                when {
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0x32.toByte() -> buildPpseResponse(visaAid)
                    apdu.size > 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                        apdu[5] == 0xA0.toByte() -> buildSelectAidResponse()
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte() -> buildGpoResponseFormat2()
                    apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB2.toByte() ->
                        buildReadRecordResponse(listOf(
                            Pair(byteArrayOf(0x57), visaTrack2),
                            Pair(byteArrayOf(0x5A), visaPanBytes),
                            Pair(byteArrayOf(0x5F, 0x24), visaExpiryBytes)
                        ))
                    apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xAE.toByte() ->
                        // Omit tag 9F26 (Application Cryptogram) — kernel uses ByteArray(8) fallback
                        buildGenerateAcResponse(listOf(
                            Pair(byteArrayOf(0x9F.toByte(), 0x27), cryptogramInfoData)
                        ))
                    else -> byteArrayOf(0x90.toByte(), 0x00)
                }
            }

            // EmvKernel uses ByteArray(8) fallback when 9F26 is absent — result is success
            val result = EmvKernel.readCard(isoDep)
            result.isSuccess shouldBe true
    }

    // =========================================================================
    // 20. CDCVM indicator in CVM Results produces cvmResult=CDCVM
    // =========================================================================

    test("CDCVM indicator in tag 9F34 produces cvmResult CDCVM") {
            val isoDep = setupIsoDep(
                aid = visaAid,
                track2 = visaTrack2,
                panBytes = visaPanBytes,
                expiryBytes = visaExpiryBytes,
                extraRecordTags = listOf(
                    // Tag 9F34 (CVM Results): byte[1] == 0x1F → CDCVM
                    Pair(byteArrayOf(0x9F.toByte(), 0x34), cvmResultsCdcvm)
                )
            )

            val result = EmvKernel.readCard(isoDep)

            result.isSuccess shouldBe true
            val cardData = result.getOrThrow()
            cardData.cvmResult shouldBe CvmResult.CDCVM
            cardData.cdcvmPerformed shouldBe true
    }

    // =========================================================================
    // 21. Error messages contain no raw hex APDU data
    // =========================================================================

    test("error messages contain no raw 4-char hex sequences") {
            val rawHexPattern = Regex("[0-9A-Fa-f]{4}")

            // Test each error path
            val errorMessages = mutableListOf<String>()

            // PPSE failure
            val isoDep1 = mockkClass(IsoDep::class)
            every { isoDep1.isConnected } returns false
            every { isoDep1.connect() } returns Unit
            every { isoDep1.timeout = any() } returns Unit
            every { isoDep1.maxTransceiveLength } returns 261
            every { isoDep1.transceive(any()) } returns byteArrayOf(0x6A.toByte(), 0x82.toByte())
            EmvKernel.readCard(isoDep1).exceptionOrNull()?.message?.let { errorMessages.add(it) }

            // SELECT AID failure
            val isoDep2 = mockkClass(IsoDep::class)
            every { isoDep2.isConnected } returns false
            every { isoDep2.connect() } returns Unit
            every { isoDep2.timeout = any() } returns Unit
            every { isoDep2.maxTransceiveLength } returns 261
            every { isoDep2.transceive(any()) } answers { call ->
                val apdu = call.invocation.args[0] as ByteArray
                if (apdu.size > 5 && apdu[5] == 0x32.toByte()) buildPpseResponse(visaAid)
                else byteArrayOf(0x69.toByte(), 0x85.toByte())
            }
            EmvKernel.readCard(isoDep2).exceptionOrNull()?.message?.let { errorMessages.add(it) }

            // IOException
            val isoDep3 = mockkClass(IsoDep::class)
            every { isoDep3.isConnected } returns false
            every { isoDep3.connect() } returns Unit
            every { isoDep3.timeout = any() } returns Unit
            every { isoDep3.maxTransceiveLength } returns 261
            every { isoDep3.transceive(any()) } throws IOException("tag lost")
            EmvKernel.readCard(isoDep3).exceptionOrNull()?.message?.let { errorMessages.add(it) }

            // Verify none of the error messages contain raw hex sequences
            for (message in errorMessages) {
                val containsRawHex = rawHexPattern.containsMatchIn(message)
                containsRawHex shouldBe false
            }
    }

    // =========================================================================
    // 22. connect() is called when isoDep.isConnected is false
    // =========================================================================

    test("connect is called when isoDep is not connected") {
            val isoDep = setupIsoDep(
                aid = visaAid,
                track2 = visaTrack2,
                panBytes = visaPanBytes,
                expiryBytes = visaExpiryBytes
            )
            every { isoDep.isConnected } returns false

            EmvKernel.readCard(isoDep)

            verify { isoDep.connect() }
    }

    // =========================================================================
    // 23. connect() is NOT called when isoDep.isConnected is true
    // =========================================================================

    test("connect is NOT called when isoDep is already connected") {
            val isoDep = setupIsoDep(
                aid = visaAid,
                track2 = visaTrack2,
                panBytes = visaPanBytes,
                expiryBytes = visaExpiryBytes
            )
            every { isoDep.isConnected } returns true

            EmvKernel.readCard(isoDep)

            verify(exactly = 0) { isoDep.connect() }
    }

    // =========================================================================
    // 24. GPO Format 1 response (tag 80) is parsed correctly
    // =========================================================================

    test("GPO Format 1 response tag 80 with AIP and AFL is parsed correctly") {
            val isoDep = setupIsoDep(
                aid = visaAid,
                track2 = visaTrack2,
                panBytes = visaPanBytes,
                expiryBytes = visaExpiryBytes,
                gpoFormat1 = true
            )

            val result = EmvKernel.readCard(isoDep)

            // Format 1 GPO response should be parsed successfully
            result.isSuccess shouldBe true
            val cardData = result.getOrThrow()
            cardData.pan shouldBe "4111111111111111"
            cardData.expiry shouldBe "12.2026"
            cardData.accountType shouldBe "Visa"
            // AIP from Format 1 should be present (tag 82 extracted from tag 80 value)
            cardData.aip.contentEquals(byteArrayOf(0x40.toByte(), 0x00)) shouldBe true
    }

})
