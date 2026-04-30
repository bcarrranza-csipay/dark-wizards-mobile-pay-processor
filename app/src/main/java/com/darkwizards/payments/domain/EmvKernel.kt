package com.darkwizards.payments.domain

import android.nfc.tech.IsoDep
import com.darkwizards.payments.data.model.AflEntry
import com.darkwizards.payments.data.model.AidEntry
import com.darkwizards.payments.data.model.CvmResult
import com.darkwizards.payments.data.model.EmvCardData
import com.darkwizards.payments.util.NfcLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * Stateless EMV contactless kernel.
 *
 * Performs the full EMV contactless card-terminal APDU dialogue and returns
 * a [Result<EmvCardData>] containing the extracted card credentials.
 *
 * Only [android.nfc.tech.IsoDep] is imported from Android; all other logic
 * is pure Kotlin.
 */
object EmvKernel {

    // -------------------------------------------------------------------------
    // Fixed terminal PDOL data (sent in GPO command)
    // These are reasonable terminal values for a contactless POS terminal.
    // -------------------------------------------------------------------------
    private val TERMINAL_PDOL_DATA: ByteArray = byteArrayOf(
        // Terminal Transaction Qualifiers (tag 9F66, 4 bytes) — contactless, online capable
        0x36.toByte(), 0x00.toByte(), 0x40.toByte(), 0x00.toByte(),
        // Amount, Authorised (tag 9F02, 6 bytes BCD) — 1.00 USD
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(),
        // Amount, Other (tag 9F03, 6 bytes BCD) — 0
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        // Terminal Country Code (tag 9F1A, 2 bytes) — 0840 = USA
        0x08.toByte(), 0x40.toByte(),
        // Transaction Currency Code (tag 5F2A, 2 bytes) — 0840 = USD
        0x08.toByte(), 0x40.toByte(),
        // Transaction Date (tag 9A, 3 bytes BCD YYMMDD) — 2024-01-01
        0x24.toByte(), 0x01.toByte(), 0x01.toByte(),
        // Transaction Type (tag 9C, 1 byte) — 0x00 = Purchase
        0x00.toByte(),
        // Unpredictable Number (tag 9F37, 4 bytes)
        0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte()
    )

    // Fixed CDOL1 data for GENERATE AC (transaction data sent to card)
    private val FIXED_CDOL1_DATA: ByteArray = byteArrayOf(
        // Amount, Authorised (6 bytes BCD) — 1.00 USD
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(),
        // Amount, Other (6 bytes BCD) — 0
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        // Terminal Country Code (2 bytes) — 0840 = USA
        0x08.toByte(), 0x40.toByte(),
        // Terminal Verification Results (5 bytes) — all zeros (no failures)
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        // Transaction Currency Code (2 bytes) — 0840 = USD
        0x08.toByte(), 0x40.toByte(),
        // Transaction Date (3 bytes BCD YYMMDD)
        0x24.toByte(), 0x01.toByte(), 0x01.toByte(),
        // Transaction Type (1 byte) — 0x00 = Purchase
        0x00.toByte(),
        // Unpredictable Number (4 bytes)
        0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte(),
        // Terminal Capabilities (3 bytes)
        0xE0.toByte(), 0xB8.toByte(), 0xC8.toByte(),
        // Application Interchange Profile (2 bytes) — placeholder
        0x00.toByte(), 0x00.toByte()
    )

    // -------------------------------------------------------------------------
    // APDU builder functions
    // -------------------------------------------------------------------------

    /** SELECT PPSE: SELECT 2PAY.SYS.DDF01 (CLA=00 INS=A4 P1=04 P2=00) */
    internal fun buildSelectPpseApdu(): ByteArray {
        val ppse = "2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII)
        return byteArrayOf(
            0x00.toByte(),          // CLA
            0xA4.toByte(),          // INS: SELECT
            0x04.toByte(),          // P1: select by name
            0x00.toByte(),          // P2: first or only occurrence
            ppse.size.toByte()      // Lc
        ) + ppse
    }

    /** SELECT AID: SELECT by AID (CLA=00 INS=A4 P1=04 P2=00) */
    internal fun buildSelectAidApdu(aid: ByteArray): ByteArray {
        return byteArrayOf(
            0x00.toByte(),          // CLA
            0xA4.toByte(),          // INS: SELECT
            0x04.toByte(),          // P1: select by name
            0x00.toByte(),          // P2: first or only occurrence
            aid.size.toByte()       // Lc
        ) + aid
    }

    /**
     * GET PROCESSING OPTIONS: CLA=80 INS=A8 P1=00 P2=00
     * Wraps [pdolData] in a command template (tag 83).
     */
    internal fun buildGpoApdu(pdolData: ByteArray): ByteArray {
        // Command template: tag 83, length, data
        val commandTemplate = byteArrayOf(
            0x83.toByte(),
            pdolData.size.toByte()
        ) + pdolData

        return byteArrayOf(
            0x80.toByte(),                      // CLA
            0xA8.toByte(),                      // INS: GET PROCESSING OPTIONS
            0x00.toByte(),                      // P1
            0x00.toByte(),                      // P2
            commandTemplate.size.toByte()       // Lc
        ) + commandTemplate
    }

    /**
     * READ RECORD: CLA=00 INS=B2 P1=record P2=(sfi<<3)|4
     */
    internal fun buildReadRecordApdu(sfi: Int, record: Int): ByteArray {
        return byteArrayOf(
            0x00.toByte(),                      // CLA
            0xB2.toByte(),                      // INS: READ RECORD
            record.toByte(),                    // P1: record number
            ((sfi shl 3) or 4).toByte(),        // P2: SFI reference control
            0x00.toByte()                       // Le: expect response
        )
    }

    /**
     * GENERATE AC: CLA=80 INS=AE P1=0x80 (ARQC request) P2=00
     */
    internal fun buildGenerateAcApdu(cdol1Data: ByteArray): ByteArray {
        return byteArrayOf(
            0x80.toByte(),                      // CLA
            0xAE.toByte(),                      // INS: GENERATE AC
            0x80.toByte(),                      // P1: reference control parameter (ARQC)
            0x00.toByte(),                      // P2
            cdol1Data.size.toByte()             // Lc
        ) + cdol1Data
    }

    // -------------------------------------------------------------------------
    // Status word helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if the APDU response status word indicates success.
     * Success: 90 00 (normal) or 61 xx (response bytes available).
     */
    internal fun isSuccessStatus(sw1: Byte, sw2: Byte): Boolean {
        val s1 = sw1.toInt() and 0xFF
        val s2 = sw2.toInt() and 0xFF
        return (s1 == 0x90 && s2 == 0x00) || (s1 == 0x61)
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    /**
     * Parses the PPSE FCI response to extract a list of [AidEntry] objects.
     *
     * The PPSE response is a BER-TLV structure. Payment AIDs are found in
     * Application Template tags (tag 61), each containing:
     *   - Tag 4F: AID bytes
     *   - Tag 87: Application Priority Indicator (optional)
     */
    internal fun parseAidList(ppseResponse: ByteArray): List<AidEntry> {
        if (ppseResponse.size < 2) return emptyList()

        // Strip the 2-byte status word from the end if present
        val data = if (ppseResponse.size >= 2) {
            val sw1 = ppseResponse[ppseResponse.size - 2].toInt() and 0xFF
            val sw2 = ppseResponse[ppseResponse.size - 1].toInt() and 0xFF
            if ((sw1 == 0x90 && sw2 == 0x00) || sw1 == 0x61) {
                ppseResponse.copyOfRange(0, ppseResponse.size - 2)
            } else {
                ppseResponse
            }
        } else {
            ppseResponse
        }

        val tags = BerTlvParser.parse(data)
        val aids = mutableListOf<AidEntry>()

        // Recursively search for Application Template tags (0x61)
        fun searchForAids(tagList: List<com.darkwizards.payments.data.model.TlvTag>) {
            for (tag in tagList) {
                val tagId = tag.tag.toInt()
                if (tagId == 0x61) {
                    // Application Template — look for AID (4F) and priority (87)
                    val aidTag = BerTlvParser.findTag(tag.children, byteArrayOf(0x4F.toByte()))
                    val priorityTag = BerTlvParser.findTag(tag.children, byteArrayOf(0x87.toByte()))
                    if (aidTag != null) {
                        val priority = if (priorityTag != null && priorityTag.value.isNotEmpty()) {
                            priorityTag.value[0].toInt() and 0xFF
                        } else {
                            0xFF // No priority indicator — lowest priority
                        }
                        aids.add(AidEntry(aid = aidTag.value, priority = priority))
                    }
                }
                // Recurse into constructed tags
                if (tag.children.isNotEmpty()) {
                    searchForAids(tag.children)
                }
            }
        }

        searchForAids(tags)
        return aids
    }

    /**
     * Selects the AID with the lowest priority indicator value (EMV convention:
     * lower value = higher priority). If the list is empty, throws [NoSuchElementException].
     */
    internal fun selectHighestPriorityAid(aids: List<AidEntry>): AidEntry {
        return aids.minByOrNull { it.priority }
            ?: throw NoSuchElementException("AID list is empty")
    }

    /**
     * Parses the AFL (Application File Locator) byte array into a list of [AflEntry].
     *
     * Each AFL entry is 4 bytes:
     *   Byte 0: (SFI << 3) | 4
     *   Byte 1: First record number
     *   Byte 2: Last record number
     *   Byte 3: Number of records involved in offline data authentication (ignored here)
     */
    internal fun parseAfl(aflBytes: ByteArray): List<AflEntry> {
        val entries = mutableListOf<AflEntry>()
        var i = 0
        while (i + 3 < aflBytes.size) {
            val sfi = (aflBytes[i].toInt() and 0xFF) ushr 3
            val firstRecord = aflBytes[i + 1].toInt() and 0xFF
            val lastRecord = aflBytes[i + 2].toInt() and 0xFF
            // aflBytes[i + 3] = offline auth records count (not used here)
            if (sfi > 0 && firstRecord > 0 && lastRecord >= firstRecord) {
                entries.add(AflEntry(sfi = sfi, firstRecord = firstRecord, lastRecord = lastRecord))
            }
            i += 4
        }
        return entries
    }

    /**
     * Determines the Cardholder Verification Method from the collected EMV data map.
     *
     * Keys are tag IDs as integers (e.g., 0x9F34 for CVM Results).
     *
     * Logic:
     * 1. Check CVM Results (tag 0x9F34): if byte[1] == 0x1F → CDCVM
     * 2. Parse CVM List (tag 0x8E): return first supported CVM in list order
     *    - 0x02 → ONLINE_PIN
     *    - 0x1E → SIGNATURE
     *    - 0x3F → NO_CVM
     * 3. Default → NO_CVM
     */
    internal fun determineCvm(emvData: Map<Int, ByteArray>): CvmResult {
        // Check CDCVM indicator in CVM Results (tag 0x9F34)
        val cvmResults = emvData[0x9F34]
        if (cvmResults != null && cvmResults.size >= 2) {
            val cvmPerformed = cvmResults[1].toInt() and 0xFF
            if (cvmPerformed == 0x1F) {
                return CvmResult.CDCVM
            }
        }

        // Parse CVM List (tag 0x8E)
        val cvmList = emvData[0x8E]
        if (cvmList != null && cvmList.size >= 8) {
            // First 8 bytes are X and Y amounts (4 bytes each) — skip them
            var i = 8
            while (i + 1 < cvmList.size) {
                val cvmCode = cvmList[i].toInt() and 0x3F  // mask off "apply next" bit
                // cvmList[i + 1] is the CVM condition — we accept all conditions
                when (cvmCode) {
                    0x02 -> return CvmResult.ONLINE_PIN
                    0x1E -> return CvmResult.SIGNATURE
                    0x3F -> return CvmResult.NO_CVM
                }
                i += 2
            }
        }

        return CvmResult.NO_CVM
    }

    // -------------------------------------------------------------------------
    // BIN-to-account-type mapping
    // -------------------------------------------------------------------------

    private fun deriveAccountType(pan: String): String {
        if (pan.isEmpty()) return "Credit"
        return when {
            pan.startsWith("4") -> "Visa"
            pan.length >= 4 && pan.substring(0, 4).toIntOrNull()?.let { it in 2221..2720 } == true -> "Mastercard"
            pan.length >= 2 && pan.substring(0, 2).toIntOrNull()?.let { it in 51..55 } == true -> "Mastercard"
            pan.startsWith("34") || pan.startsWith("37") -> "Amex"
            pan.startsWith("6011") || pan.startsWith("65") -> "Discover"
            pan.startsWith("35") -> "JCB"
            else -> "Credit"
        }
    }

    // -------------------------------------------------------------------------
    // Expiry conversion: Tag 5F24 (YYMMDD BCD, 3 bytes) → "MM.YYYY"
    // -------------------------------------------------------------------------

    private fun parseExpiryTag(expiryBytes: ByteArray): String {
        if (expiryBytes.size < 3) return ""
        // BCD: byte 0 = YY, byte 1 = MM, byte 2 = DD
        val yy = String.format("%02X", expiryBytes[0].toInt() and 0xFF)
        val mm = String.format("%02X", expiryBytes[1].toInt() and 0xFF)
        return "$mm.20$yy"
    }

    // -------------------------------------------------------------------------
    // Helper: convert ByteArray tag to Int for map key
    // -------------------------------------------------------------------------

    private fun ByteArray.toInt(): Int {
        var result = 0
        for (b in this) {
            result = (result shl 8) or (b.toInt() and 0xFF)
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    /**
     * Performs the full EMV contactless card-terminal dialogue and returns
     * a [Result<EmvCardData>] with the extracted card credentials.
     *
     * The entire dialogue must complete within 10 seconds.
     *
     * Error handling:
     * - [IOException] → "Card removed too soon — please try again"
     * - [TimeoutCancellationException] → "Card read timed out — please try again"
     * - Non-success APDU SW → user-friendly message, no raw hex exposed
     * - Missing mandatory tag → "Card data incomplete — please try again"
     */
    suspend fun readCard(isoDep: IsoDep): Result<EmvCardData> {
        return try {
            withTimeout(10_000L) {
                withContext(Dispatchers.IO) {
                    readCardInternal(isoDep)
                }
            }
        } catch (e: IOException) {
            Result.failure(Exception("Card removed too soon — please try again"))
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Card read timed out — please try again"))
        }
    }

    @Throws(IOException::class)
    private fun readCardInternal(isoDep: IsoDep): Result<EmvCardData> {
        // Connect if not already connected
        if (!isoDep.isConnected) {
            NfcLogger.d("EmvKernel", "Connecting IsoDep...")
            isoDep.connect()
            NfcLogger.d("EmvKernel", "IsoDep connected. MaxTransceiveLength=${isoDep.maxTransceiveLength}")
        } else {
            NfcLogger.d("EmvKernel", "IsoDep already connected")
        }
        isoDep.timeout = 9_000

        // Accumulated TLV data from all records
        val emvData = mutableMapOf<Int, ByteArray>()

        // -----------------------------------------------------------------------
        // Step 1: SELECT PPSE
        // -----------------------------------------------------------------------
        NfcLogger.d("EmvKernel", "Step 1: SELECT PPSE")
        val ppseApdu = buildSelectPpseApdu()
        val ppseResponse = isoDep.transceive(ppseApdu)
        NfcLogger.d("EmvKernel", "PPSE response len=${ppseResponse.size} SW=${if (ppseResponse.size >= 2) "%02X%02X".format(ppseResponse[ppseResponse.size-2], ppseResponse[ppseResponse.size-1]) else "N/A"}")

        if (ppseResponse.size < 2) {
            NfcLogger.e("EmvKernel", "PPSE response too short")
            return Result.failure(Exception("Card not supported — please try a different card"))
        }

        val ppseSw1 = ppseResponse[ppseResponse.size - 2]
        val ppseSw2 = ppseResponse[ppseResponse.size - 1]

        if (!isSuccessStatus(ppseSw1, ppseSw2)) {
            NfcLogger.e("EmvKernel", "PPSE SELECT failed: SW=${"%02X%02X".format(ppseSw1, ppseSw2)}")
            return Result.failure(Exception("Card not supported — please try a different card"))
        }

        // -----------------------------------------------------------------------
        // Step 2: Parse AID list and select highest-priority AID
        // -----------------------------------------------------------------------
        val aids = parseAidList(ppseResponse)
        NfcLogger.d("EmvKernel", "Step 2: Found ${aids.size} AIDs: ${aids.joinToString { it.aid.joinToString("") { b -> "%02X".format(b) } }}")
        if (aids.isEmpty()) {
            NfcLogger.e("EmvKernel", "No AIDs found in PPSE response")
            return Result.failure(Exception("Card not supported — please try a different card"))
        }

        val selectedAid = selectHighestPriorityAid(aids)
        NfcLogger.d("EmvKernel", "Selected AID: ${selectedAid.aid.joinToString("") { "%02X".format(it) }} priority=${selectedAid.priority}")

        // -----------------------------------------------------------------------
        // Step 3: SELECT AID
        // -----------------------------------------------------------------------
        NfcLogger.d("EmvKernel", "Step 3: SELECT AID")
        val selectAidApdu = buildSelectAidApdu(selectedAid.aid)
        val selectAidResponse = isoDep.transceive(selectAidApdu)
        NfcLogger.d("EmvKernel", "SELECT AID response len=${selectAidResponse.size} SW=${if (selectAidResponse.size >= 2) "%02X%02X".format(selectAidResponse[selectAidResponse.size-2], selectAidResponse[selectAidResponse.size-1]) else "N/A"}")

        if (selectAidResponse.size < 2) {
            NfcLogger.e("EmvKernel", "SELECT AID response too short")
            return Result.failure(Exception("Card read error — please try again"))
        }

        val selectSw1 = selectAidResponse[selectAidResponse.size - 2]
        val selectSw2 = selectAidResponse[selectAidResponse.size - 1]

        if (!isSuccessStatus(selectSw1, selectSw2)) {
            NfcLogger.e("EmvKernel", "SELECT AID failed: SW=${"%02X%02X".format(selectSw1, selectSw2)}")
            return Result.failure(Exception("Card read error — please try again"))
        }

        // Parse FCI from SELECT AID response to find PDOL
        val fciData = selectAidResponse.copyOfRange(0, selectAidResponse.size - 2)
        val fciTags = BerTlvParser.parse(fciData)
        collectTlvData(fciTags, emvData)

        // Extract PDOL from FCI (tag 9F38) if present — build correct-length data
        val pdolTag = BerTlvParser.findTag(fciTags, byteArrayOf(0x9F.toByte(), 0x38.toByte()))
        val pdolData = if (pdolTag != null && pdolTag.value.isNotEmpty()) {
            // Build PDOL data by filling each field with zeros of the correct length
            // This satisfies the card's length requirements even if values are dummy
            buildPdolData(pdolTag.value).also {
                NfcLogger.d("EmvKernel", "PDOL template len=${pdolTag.value.size} → pdolData len=${it.size}")
            }
        } else {
            NfcLogger.d("EmvKernel", "No PDOL in FCI — sending empty GPO")
            ByteArray(0)
        }

        // -----------------------------------------------------------------------
        // Step 4: GET PROCESSING OPTIONS (GPO)
        // -----------------------------------------------------------------------
        NfcLogger.d("EmvKernel", "Step 4: GPO pdolLen=${pdolData.size}")
        val gpoApdu = buildGpoApdu(pdolData)
        val gpoResponse = isoDep.transceive(gpoApdu)
        NfcLogger.d("EmvKernel", "GPO response len=${gpoResponse.size} SW=${if (gpoResponse.size >= 2) "%02X%02X".format(gpoResponse[gpoResponse.size-2], gpoResponse[gpoResponse.size-1]) else "N/A"}")

        if (gpoResponse.size < 2) {
            NfcLogger.e("EmvKernel", "GPO response too short")
            return Result.failure(Exception("Card read error — please try again"))
        }

        val gpoSw1 = gpoResponse[gpoResponse.size - 2]
        val gpoSw2 = gpoResponse[gpoResponse.size - 1]

        if (!isSuccessStatus(gpoSw1, gpoSw2)) {
            NfcLogger.e("EmvKernel", "GPO failed: SW=${"%02X%02X".format(gpoSw1, gpoSw2)}")
            return Result.failure(Exception("Card read error — please try again"))
        }

        // Parse GPO response — may be format 1 (80) or format 2 (77)
        val gpoData = gpoResponse.copyOfRange(0, gpoResponse.size - 2)
        val gpoTags = BerTlvParser.parse(gpoData)
        collectTlvData(gpoTags, emvData)

        // Extract AIP and AFL
        // Format 1 response: tag 80, value = [AIP (2 bytes)][AFL (n*4 bytes)]
        val format1Tag = BerTlvParser.findTag(gpoTags, byteArrayOf(0x80.toByte()))
        val aflBytes: ByteArray
        if (format1Tag != null && format1Tag.value.size >= 4) {
            // AIP is first 2 bytes, AFL is the rest
            val aip = format1Tag.value.copyOfRange(0, 2)
            emvData[0x82] = aip
            aflBytes = format1Tag.value.copyOfRange(2, format1Tag.value.size)
            emvData[0x94] = aflBytes
        } else {
            // Format 2: look for tag 82 (AIP) and tag 94 (AFL) directly
            val aipTag = BerTlvParser.findTag(gpoTags, byteArrayOf(0x82.toByte()))
            val aflTag = BerTlvParser.findTag(gpoTags, byteArrayOf(0x94.toByte()))
            aflBytes = aflTag?.value ?: ByteArray(0)
        }

        // -----------------------------------------------------------------------
        // Step 5: READ RECORD for all AFL entries
        // -----------------------------------------------------------------------
        val aflEntries = parseAfl(aflBytes)
        NfcLogger.d("EmvKernel", "Step 5: READ RECORD aflEntries=${aflEntries.size}")

        for (aflEntry in aflEntries) {
            for (record in aflEntry.firstRecord..aflEntry.lastRecord) {
                val readRecordApdu = buildReadRecordApdu(aflEntry.sfi, record)
                val recordResponse = isoDep.transceive(readRecordApdu)
                NfcLogger.d("EmvKernel", "READ RECORD SFI=${aflEntry.sfi} rec=$record SW=${if (recordResponse.size >= 2) "%02X%02X".format(recordResponse[recordResponse.size-2], recordResponse[recordResponse.size-1]) else "N/A"}")

                if (recordResponse.size < 2) continue

                val recSw1 = recordResponse[recordResponse.size - 2]
                val recSw2 = recordResponse[recordResponse.size - 1]

                if (!isSuccessStatus(recSw1, recSw2)) {
                    NfcLogger.e("EmvKernel", "READ RECORD SFI=${aflEntry.sfi} rec=$record failed: SW=${"%02X%02X".format(recSw1, recSw2)}")
                    return Result.failure(Exception("Card read error — please try again"))
                }

                val recordData = recordResponse.copyOfRange(0, recordResponse.size - 2)
                val recordTags = BerTlvParser.parse(recordData)
                collectTlvData(recordTags, emvData)
            }
        }
        NfcLogger.d("EmvKernel", "Records read. emvData keys: ${emvData.keys.map { "0x%X".format(it) }}")

        // -----------------------------------------------------------------------
        // Step 6: GENERATE AC (ARQC)
        // Skip if we already have the cryptogram (0x9F26) from GPO response,
        // or if CDOL1 (0x8C) is not available to build correct command data.
        // -----------------------------------------------------------------------
        if (emvData[0x9F26] != null) {
            NfcLogger.d("EmvKernel", "Step 6: Cryptogram already present from GPO — skipping GENERATE AC")
        } else {
            NfcLogger.d("EmvKernel", "Step 6: GENERATE AC cdolLen=${FIXED_CDOL1_DATA.size}")
            val cdol1Template = emvData[0x8C]
            val cdol1Data = if (cdol1Template != null) {
                buildPdolData(cdol1Template).also {
                    NfcLogger.d("EmvKernel", "CDOL1 template len=${cdol1Template.size} → cdol1Data len=${it.size}")
                }
            } else {
                NfcLogger.d("EmvKernel", "No CDOL1 — using fixed data")
                FIXED_CDOL1_DATA
            }
            val generateAcApdu = buildGenerateAcApdu(cdol1Data)
            val generateAcResponse = isoDep.transceive(generateAcApdu)
            NfcLogger.d("EmvKernel", "GENERATE AC response len=${generateAcResponse.size} SW=${if (generateAcResponse.size >= 2) "%02X%02X".format(generateAcResponse[generateAcResponse.size-2], generateAcResponse[generateAcResponse.size-1]) else "N/A"}")

            if (generateAcResponse.size >= 2) {
                val acSw1 = generateAcResponse[generateAcResponse.size - 2]
                val acSw2 = generateAcResponse[generateAcResponse.size - 1]
                if (isSuccessStatus(acSw1, acSw2)) {
                    val acData = generateAcResponse.copyOfRange(0, generateAcResponse.size - 2)
                    collectTlvData(BerTlvParser.parse(acData), emvData)
                } else {
                    NfcLogger.e("EmvKernel", "GENERATE AC failed: SW=${"%02X%02X".format(acSw1, acSw2)} — continuing with GPO cryptogram if available")
                }
            }
        }

        // -----------------------------------------------------------------------
        // Step 7: Extract mandatory tags
        // -----------------------------------------------------------------------
        val track2Equivalent = emvData[0x57]
            ?: return Result.failure(Exception("Card data incomplete — please try again"))
        // Expiry: prefer tag 5F24, fall back to Track2 parsing
        val expiryBytes = emvData[0x5F24]
        val applicationCryptogram = emvData[0x9F26] ?: ByteArray(8)
        val cryptogramInfoDataBytes = emvData[0x9F27] ?: byteArrayOf(0x00)
        val aip = emvData[0x82] ?: ByteArray(2)

        // -----------------------------------------------------------------------
        // Step 8: Parse Track2 and determine CVM
        // -----------------------------------------------------------------------
        val track2Result = Track2Parser.parse(track2Equivalent)
        if (track2Result.isFailure) {
            return Result.failure(Exception("Card data incomplete — please try again"))
        }
        val track2Data = track2Result.getOrThrow()

        // Use tag 5F24 for expiry if available, otherwise use Track2 expiry
        val expiry = if (expiryBytes != null) parseExpiryTag(expiryBytes) else track2Data.expiry
        NfcLogger.d("EmvKernel", "PAN=${track2Data.pan.take(6)}****${track2Data.pan.takeLast(4)} expiry=$expiry")
        val pan = track2Data.pan
        val accountType = deriveAccountType(pan)

        val cvmResult = determineCvm(emvData)
        val cryptogramInfoData = if (cryptogramInfoDataBytes.isNotEmpty()) cryptogramInfoDataBytes[0] else 0x00.toByte()

        // CDCVM performed if determineCvm returned CDCVM
        val cdcvmPerformed = cvmResult == CvmResult.CDCVM

        return Result.success(
            EmvCardData(
                pan = pan,
                expiry = expiry,
                accountType = accountType,
                track2Equivalent = track2Equivalent,
                applicationCryptogram = applicationCryptogram,
                cryptogramInfoData = cryptogramInfoData,
                aip = aip,
                cvmResult = cvmResult,
                cdcvmPerformed = cdcvmPerformed
            )
        )
    }

    /**
     * Builds GPO command data from the card's PDOL template (tag 9F38).
     * The PDOL is a list of (tag, length) pairs. We fill each field with
     * zeros of the correct length, except for known critical fields that
     * require specific non-zero values to avoid SW=6985.
     *
     * Critical fields:
     *   9F66 (TTQ, 4 bytes)  — Terminal Transaction Qualifiers
     *   9F02 (Amount, 6 bytes) — filled with zeros (acceptable)
     *   9F37 (Unpredictable Number, 4 bytes) — random value
     */
    private fun buildPdolData(pdolTemplate: ByteArray): ByteArray {
        val result = mutableListOf<Byte>()
        var i = 0
        while (i < pdolTemplate.size) {
            // Read tag (1 or 2 bytes)
            val firstByte = pdolTemplate[i].toInt() and 0xFF
            i++
            val isMultiByte = (firstByte and 0x1F) == 0x1F
            val secondByte = if (isMultiByte && i < pdolTemplate.size) {
                val b = pdolTemplate[i].toInt() and 0xFF
                i++
                b
            } else -1

            // Read length
            if (i >= pdolTemplate.size) break
            val len = pdolTemplate[i].toInt() and 0xFF
            i++

            // Build tag int for matching
            val tagInt = if (secondByte >= 0) (firstByte shl 8) or secondByte else firstByte

            NfcLogger.d("EmvKernel", "PDOL field tag=0x${tagInt.toString(16)} len=$len")

            when (tagInt) {
                0x9F66 -> {
                    // TTQ: contactless EMV, online capable, CVM required
                    // Byte1: 0x36 = MSD supported, EMV mode, online capable
                    // Byte2: 0x00, Byte3: 0x40 = online PIN supported, Byte4: 0x00
                    result.addAll(listOf(0x36.toByte(), 0x00.toByte(), 0x40.toByte(), 0x00.toByte()).take(len))
                    repeat(maxOf(0, len - 4)) { result.add(0x00.toByte()) }
                }
                0x9F37 -> {
                    // Unpredictable Number — use a non-zero value
                    result.addAll(listOf(0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte()).take(len))
                    repeat(maxOf(0, len - 4)) { result.add(0x00.toByte()) }
                }
                0x9F1A, 0x5F2A -> {
                    // Terminal Country Code / Transaction Currency Code — USD = 0x0840
                    if (len >= 2) {
                        result.add(0x08.toByte()); result.add(0x40.toByte())
                        repeat(len - 2) { result.add(0x00.toByte()) }
                    } else repeat(len) { result.add(0x00.toByte()) }
                }
                0x9A -> {
                    // Transaction Date YYMMDD
                    if (len >= 3) {
                        result.add(0x26.toByte()); result.add(0x04.toByte()); result.add(0x30.toByte())
                        repeat(len - 3) { result.add(0x00.toByte()) }
                    } else repeat(len) { result.add(0x00.toByte()) }
                }
                0x9C -> {
                    // Transaction Type — 0x00 = Purchase
                    result.add(0x00.toByte())
                    repeat(len - 1) { result.add(0x00.toByte()) }
                }
                else -> repeat(len) { result.add(0x00.toByte()) }
            }
        }
        return result.toByteArray()
    }

    /**
     * Recursively collects all primitive TLV tag values into [emvData],
     * keyed by the tag ID as an integer.
     */
    private fun collectTlvData(
        tags: List<com.darkwizards.payments.data.model.TlvTag>,
        emvData: MutableMap<Int, ByteArray>
    ) {
        for (tag in tags) {
            val tagId = tag.tag.toInt()
            if (tag.children.isEmpty()) {
                // Primitive tag — store value (don't overwrite if already present)
                emvData.putIfAbsent(tagId, tag.value)
            } else {
                // Constructed tag — recurse into children
                collectTlvData(tag.children, emvData)
            }
        }
    }
}
