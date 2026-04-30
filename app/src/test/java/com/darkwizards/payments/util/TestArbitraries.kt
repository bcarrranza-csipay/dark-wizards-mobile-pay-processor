package com.darkwizards.payments.util

import android.nfc.tech.IsoDep
import com.darkwizards.payments.data.model.TlvTag
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.mockk.every
import io.mockk.mockkClass
import kotlin.random.Random

/**
 * Shared property-based test generators for the nfc-tap-to-pay feature.
 *
 * BER-TLV encoding rules (ISO 7816-4):
 *  - Bit 6 of the first tag byte (0x20) = 1 → constructed tag (has children)
 *  - Bit 6 of the first tag byte (0x20) = 0 → primitive tag (leaf value)
 *  - Bits 5-1 of the first tag byte all set (0x1F mask) → multi-byte tag
 *    (subsequent bytes have bit 8 set until the last byte which has bit 8 clear)
 */

// ---------------------------------------------------------------------------
// Primitive tag byte pools
// ---------------------------------------------------------------------------

/**
 * Well-known single-byte primitive tag identifiers used in EMV.
 * Bit 6 (0x20) is clear → primitive.
 * Bits 5-1 are NOT all set (≠ 0x1F) → single-byte tag.
 */
private val PRIMITIVE_SINGLE_BYTE_TAGS: List<ByteArray> = listOf(
    byteArrayOf(0x50),          // Application Label
    byteArrayOf(0x56),          // Track 1 Equivalent Data
    byteArrayOf(0x57),          // Track 2 Equivalent Data
    byteArrayOf(0x5A),          // Application PAN
    byteArrayOf(0x82.toByte()), // Application Interchange Profile (AIP)
    byteArrayOf(0x84.toByte()), // Dedicated File Name
    byteArrayOf(0x87.toByte()), // Application Priority Indicator
    byteArrayOf(0x8C.toByte()), // Card Risk Management Data Object List 1 (CDOL1)
    byteArrayOf(0x8D.toByte()), // CDOL2
    byteArrayOf(0x8E.toByte()), // CVM List
    byteArrayOf(0x8F.toByte()), // Certification Authority Public Key Index
    byteArrayOf(0x94.toByte()), // Application File Locator (AFL)
    byteArrayOf(0x95.toByte()), // Terminal Verification Results
    byteArrayOf(0x9A.toByte()), // Transaction Date
    byteArrayOf(0x9C.toByte())  // Transaction Type
)

/**
 * Well-known two-byte primitive tag identifiers used in EMV.
 * First byte has bits 5-1 all set (0x1F mask) → multi-byte tag.
 * Bit 6 of first byte is clear → primitive.
 * Second byte has bit 8 clear → last byte of tag.
 */
private val PRIMITIVE_TWO_BYTE_TAGS: List<ByteArray> = listOf(
    byteArrayOf(0x5F, 0x20),          // Cardholder Name
    byteArrayOf(0x5F, 0x24),          // Application Expiry Date
    byteArrayOf(0x5F, 0x25),          // Application Effective Date
    byteArrayOf(0x5F, 0x28),          // Issuer Country Code
    byteArrayOf(0x5F, 0x2A),          // Transaction Currency Code
    byteArrayOf(0x5F, 0x2D),          // Language Preference
    byteArrayOf(0x5F, 0x30),          // Service Code
    byteArrayOf(0x5F, 0x34),          // Application PAN Sequence Number
    byteArrayOf(0x9F.toByte(), 0x02), // Amount, Authorised
    byteArrayOf(0x9F.toByte(), 0x03), // Amount, Other
    byteArrayOf(0x9F.toByte(), 0x06), // Application Identifier (AID) - terminal
    byteArrayOf(0x9F.toByte(), 0x07), // Application Usage Control
    byteArrayOf(0x9F.toByte(), 0x08), // Application Version Number
    byteArrayOf(0x9F.toByte(), 0x0D), // Issuer Action Code - Default
    byteArrayOf(0x9F.toByte(), 0x0E), // Issuer Action Code - Denial
    byteArrayOf(0x9F.toByte(), 0x0F), // Issuer Action Code - Online
    byteArrayOf(0x9F.toByte(), 0x11), // Issuer Code Table Index
    byteArrayOf(0x9F.toByte(), 0x12), // Application Preferred Name
    byteArrayOf(0x9F.toByte(), 0x1A), // Terminal Country Code
    byteArrayOf(0x9F.toByte(), 0x1F), // Track 1 Discretionary Data
    byteArrayOf(0x9F.toByte(), 0x20), // Track 2 Discretionary Data
    byteArrayOf(0x9F.toByte(), 0x26), // Application Cryptogram (ARQC)
    byteArrayOf(0x9F.toByte(), 0x27), // Cryptogram Information Data
    byteArrayOf(0x9F.toByte(), 0x36), // Application Transaction Counter
    byteArrayOf(0x9F.toByte(), 0x37), // Unpredictable Number
    byteArrayOf(0x9F.toByte(), 0x38), // PDOL
    byteArrayOf(0x9F.toByte(), 0x42), // Application Currency Code
    byteArrayOf(0x9F.toByte(), 0x44), // Application Currency Exponent
    byteArrayOf(0x9F.toByte(), 0x45), // Data Authentication Code
    byteArrayOf(0x9F.toByte(), 0x4C), // ICC Dynamic Number
    byteArrayOf(0x9F.toByte(), 0x4D), // Log Entry
    byteArrayOf(0x9F.toByte(), 0x4F)  // Log Format
)

/**
 * Well-known single-byte constructed tag identifiers used in EMV.
 * Bit 6 (0x20) is set → constructed.
 * Bits 5-1 are NOT all set (≠ 0x1F) → single-byte tag.
 */
private val CONSTRUCTED_SINGLE_BYTE_TAGS: List<ByteArray> = listOf(
    byteArrayOf(0x61),          // Application Template
    byteArrayOf(0x6F),          // File Control Information (FCI) Template
    byteArrayOf(0x70),          // Record Template / EMV Proprietary Template
    byteArrayOf(0x77),          // Response Message Template Format 2
    byteArrayOf(0xA5.toByte()), // FCI Proprietary Template
    byteArrayOf(0xE1.toByte()), // Constructed context-specific
    byteArrayOf(0xE2.toByte())  // Constructed context-specific
)

/**
 * Well-known two-byte constructed tag identifiers used in EMV.
 * First byte has bits 5-1 all set (0x1F mask) → multi-byte tag.
 * Bit 6 of first byte is set → constructed.
 *
 * NOTE: Tags starting with 0xFF are intentionally excluded because BerTlvParser
 * treats 0xFF as an inter-record padding byte and skips it, which would break
 * the round-trip property. This is a valid EMV implementation choice (ISO 7816-4
 * allows 0x00 and 0xFF as padding between records).
 */
private val CONSTRUCTED_TWO_BYTE_TAGS: List<ByteArray> = listOf(
    byteArrayOf(0xBF.toByte(), 0x0C), // FCI Issuer Discretionary Data
    byteArrayOf(0xBF.toByte(), 0x10), // Constructed two-byte tag (test)
    byteArrayOf(0xBF.toByte(), 0x20)  // Constructed two-byte tag (test)
)

// ---------------------------------------------------------------------------
// Arb.tlvStructure
// ---------------------------------------------------------------------------

/**
 * Generates a list of structurally valid [TlvTag] objects following BER-TLV encoding rules.
 *
 * - Primitive tags have a random byte array value (0–32 bytes).
 * - Constructed tags have 1–3 child tags generated recursively up to [maxDepth].
 * - At depth 0, only primitive tags are generated (no further nesting).
 * - Tag bytes are chosen from well-known EMV tag pools to ensure correct
 *   constructed/primitive bit encoding.
 *
 * The generated structures are designed so that `BerTlvParser.encode` followed
 * by `BerTlvParser.parse` and `BerTlvParser.encode` again produces identical bytes
 * (Property 11: BER-TLV round-trip).
 */
fun Arb.Companion.tlvStructure(maxDepth: Int = 3): Arb<List<TlvTag>> = arbitrary { rs ->
    val random = rs.random
    val count = random.nextInt(1, 4) // 1–3 top-level tags
    List(count) { generateTlvTag(random, maxDepth) }
}

private fun generateTlvTag(random: Random, depth: Int): TlvTag {
    // At depth 0 or with 70% probability at any depth, generate a primitive tag
    val makePrimitive = depth <= 0 || random.nextInt(10) < 7

    return if (makePrimitive) {
        generatePrimitiveTag(random)
    } else {
        generateConstructedTag(random, depth)
    }
}

private fun generatePrimitiveTag(random: Random): TlvTag {
    // Pick a random primitive tag from the pools
    val allPrimitiveTags = PRIMITIVE_SINGLE_BYTE_TAGS + PRIMITIVE_TWO_BYTE_TAGS
    val tagBytes = allPrimitiveTags[random.nextInt(allPrimitiveTags.size)]

    // Generate a random value (0–32 bytes)
    val valueSize = random.nextInt(33) // 0..32
    val value = ByteArray(valueSize) { random.nextInt(256).toByte() }

    return TlvTag(tag = tagBytes, value = value, children = emptyList())
}

private fun generateConstructedTag(random: Random, depth: Int): TlvTag {
    // Pick a random constructed tag from the pools
    val allConstructedTags = CONSTRUCTED_SINGLE_BYTE_TAGS + CONSTRUCTED_TWO_BYTE_TAGS
    val tagBytes = allConstructedTags[random.nextInt(allConstructedTags.size)]

    // Generate 1–3 children at depth - 1
    val childCount = random.nextInt(1, 4)
    val children = List(childCount) { generateTlvTag(random, depth - 1) }

    // For constructed tags, the value field is ignored by BerTlvParser.encode
    // (it re-encodes from children), so we set it to empty.
    return TlvTag(tag = tagBytes, value = byteArrayOf(), children = children)
}

// ---------------------------------------------------------------------------
// Mandatory EMV tag byte identifiers
// ---------------------------------------------------------------------------

/** The 7 mandatory EMV tags that must be present in every contactless transaction. */
private val MANDATORY_TAG_IDS: List<ByteArray> = listOf(
    byteArrayOf(0x57),                          // Track 2 Equivalent Data
    byteArrayOf(0x5A),                          // Application PAN
    byteArrayOf(0x5F, 0x24),                    // Application Expiry Date (3 bytes BCD)
    byteArrayOf(0x9F.toByte(), 0x26),           // Application Cryptogram (ARQC, 8 bytes)
    byteArrayOf(0x9F.toByte(), 0x27),           // Cryptogram Information Data (1 byte)
    byteArrayOf(0x82.toByte()),                 // Application Interchange Profile (2 bytes)
    byteArrayOf(0x94.toByte())                  // Application File Locator (AFL, multiples of 4)
)

/** Fixed value lengths for each mandatory tag (index-aligned with MANDATORY_TAG_IDS). */
private val MANDATORY_TAG_VALUE_LENGTHS: List<IntRange> = listOf(
    8..19,   // 0x57  Track 2 Equivalent Data: variable, 8–19 bytes
    7..10,   // 0x5A  Application PAN: 7–10 bytes (BCD-encoded 13–19 digit PAN)
    3..3,    // 0x5F24 Application Expiry Date: exactly 3 bytes (YYMMDD BCD)
    8..8,    // 0x9F26 Application Cryptogram: exactly 8 bytes
    1..1,    // 0x9F27 Cryptogram Information Data: exactly 1 byte
    2..2,    // 0x82  Application Interchange Profile: exactly 2 bytes
    4..16    // 0x94  Application File Locator: multiples of 4, 4–16 bytes
)

// ---------------------------------------------------------------------------
// Arb.tlvStructureWithMandatoryTags
// ---------------------------------------------------------------------------

/**
 * Generates a [List<TlvTag>] that contains all 7 mandatory EMV tags:
 * 0x57, 0x5A, 0x5F24, 0x9F26, 0x9F27, 0x82, 0x94.
 *
 * The mandatory tags are distributed across the structure at varying depths:
 * - Some mandatory tags are placed at the top level (depth 0).
 * - Others are nested inside constructed wrapper tags to exercise the recursive
 *   `BerTlvParser.findTag` behaviour.
 * - Additional random non-mandatory primitive tags may be interspersed.
 *
 * Each mandatory tag has a non-empty random value of the appropriate byte length
 * for that tag type.
 *
 * Designed for Property 12: Mandatory tag extraction.
 */
fun Arb.Companion.tlvStructureWithMandatoryTags(): Arb<List<TlvTag>> = arbitrary { rs ->
    val random = rs.random

    // Build the 7 mandatory TlvTag leaf nodes with appropriate random values
    val mandatoryTags: List<TlvTag> = MANDATORY_TAG_IDS.mapIndexed { index, tagId ->
        val lengthRange = MANDATORY_TAG_VALUE_LENGTHS[index]
        val valueLen = lengthRange.first + random.nextInt(lengthRange.last - lengthRange.first + 1)
        val value = ByteArray(valueLen) { random.nextInt(256).toByte() }
        TlvTag(tag = tagId, value = value, children = emptyList())
    }

    // Shuffle the mandatory tags so their order is random
    val shuffled = mandatoryTags.shuffled(random)

    // Decide how many mandatory tags to nest inside constructed wrappers (0–4)
    val nestCount = random.nextInt(0, minOf(5, shuffled.size))

    // Split: first nestCount tags will be nested, the rest go at the top level
    val toNest = shuffled.take(nestCount)
    val topLevel = shuffled.drop(nestCount).toMutableList()

    // Wrap the nested tags in constructed containers (1–2 levels deep)
    if (toNest.isNotEmpty()) {
        // Group the tags-to-nest into 1–2 constructed wrappers
        val wrapperCount = if (toNest.size == 1) 1 else random.nextInt(1, 3)
        val groups = toNest.chunked((toNest.size + wrapperCount - 1) / wrapperCount)

        for (group in groups) {
            // Optionally add a second level of nesting (50% chance)
            val innerChildren: List<TlvTag> = if (random.nextBoolean() && group.size > 1) {
                // Wrap half the group in an inner constructed tag
                val innerCount = group.size / 2
                val innerTag = TlvTag(
                    tag = CONSTRUCTED_SINGLE_BYTE_TAGS_FOR_MANDATORY[
                        random.nextInt(CONSTRUCTED_SINGLE_BYTE_TAGS_FOR_MANDATORY.size)
                    ],
                    value = byteArrayOf(),
                    children = group.take(innerCount)
                )
                listOf(innerTag) + group.drop(innerCount)
            } else {
                group
            }

            val outerWrapper = TlvTag(
                tag = CONSTRUCTED_SINGLE_BYTE_TAGS_FOR_MANDATORY[
                    random.nextInt(CONSTRUCTED_SINGLE_BYTE_TAGS_FOR_MANDATORY.size)
                ],
                value = byteArrayOf(),
                children = innerChildren
            )
            topLevel.add(outerWrapper)
        }
    }

    // Optionally intersperse 0–3 additional non-mandatory primitive tags
    val extraCount = random.nextInt(0, 4)
    val nonMandatoryPool = PRIMITIVE_SINGLE_BYTE_TAGS.filter { candidate ->
        MANDATORY_TAG_IDS.none { mandatory -> mandatory.contentEquals(candidate) }
    } + PRIMITIVE_TWO_BYTE_TAGS.filter { candidate ->
        MANDATORY_TAG_IDS.none { mandatory -> mandatory.contentEquals(candidate) }
    }

    repeat(extraCount) {
        val tagBytes = nonMandatoryPool[random.nextInt(nonMandatoryPool.size)]
        val valueLen = random.nextInt(1, 9) // 1–8 bytes, non-empty
        val value = ByteArray(valueLen) { random.nextInt(256).toByte() }
        val insertAt = random.nextInt(topLevel.size + 1)
        topLevel.add(insertAt, TlvTag(tag = tagBytes, value = value, children = emptyList()))
    }

    topLevel.shuffled(random)
}

/**
 * Constructed tag identifiers used as wrappers when nesting mandatory tags.
 * These are a subset of [CONSTRUCTED_SINGLE_BYTE_TAGS] chosen to avoid any
 * tag that could be confused with a mandatory primitive tag.
 */
private val CONSTRUCTED_SINGLE_BYTE_TAGS_FOR_MANDATORY: List<ByteArray> = listOf(
    byteArrayOf(0x61),          // Application Template
    byteArrayOf(0x6F),          // File Control Information (FCI) Template
    byteArrayOf(0x70),          // Record Template / EMV Proprietary Template
    byteArrayOf(0x77),          // Response Message Template Format 2
    byteArrayOf(0xA5.toByte()), // FCI Proprietary Template
    byteArrayOf(0xE1.toByte()), // Constructed context-specific
    byteArrayOf(0xE2.toByte())  // Constructed context-specific
)

// ---------------------------------------------------------------------------
// Arb.emvCardData — generates Map<Int, ByteArray> representing EMV tag data
// ---------------------------------------------------------------------------

/**
 * Generates a [Map<Int, ByteArray>] representing EMV tag data for use in
 * [EmvKernel.determineCvm] property tests.
 *
 * When [cdcvmPerformed] is `true`, the generated map includes tag `0x9F34`
 * (CVM Results) with byte[1] == `0x1F`, which is the CDCVM indicator checked
 * by [EmvKernel.determineCvm]. The map also includes a random CVM List (tag
 * `0x8E`) with various entries to ensure CDCVM takes priority regardless of
 * the CVM list contents.
 *
 * Used by Property 5: CDCVM suppresses PIN and Signature prompts.
 */
fun Arb.Companion.emvCardData(cdcvmPerformed: Boolean): Arb<Map<Int, ByteArray>> = arbitrary { rs ->
    val random = rs.random
    val map = mutableMapOf<Int, ByteArray>()

    if (cdcvmPerformed) {
        // Tag 0x9F34 (CVM Results): 3 bytes.
        // EmvKernel.determineCvm checks: cvmResults[1].toInt() and 0xFF == 0x1F
        // byte[0]: CVM code (arbitrary, e.g. 0x1E = CDCVM code)
        // byte[1]: 0x1F = CDCVM performed indicator
        // byte[2]: result byte (arbitrary)
        val cvmResultsByte0 = random.nextInt(256).toByte()
        val cvmResultsByte2 = random.nextInt(256).toByte()
        map[0x9F34] = byteArrayOf(cvmResultsByte0, 0x1F.toByte(), cvmResultsByte2)
    }

    // Optionally include a CVM List (tag 0x8E) with random entries to verify
    // that CDCVM takes priority over any CVM list contents.
    // CVM List format: 8 bytes (X amount + Y amount) + pairs of (cvmCode, cvmCondition)
    if (random.nextBoolean()) {
        // CVM codes that determineCvm recognises: 0x02 (ONLINE_PIN), 0x1E (SIGNATURE), 0x3F (NO_CVM)
        val knownCvmCodes = listOf(0x02, 0x1E, 0x3F)
        val entryCount = 1 + random.nextInt(3) // 1–3 entries
        val cvmListBytes = ByteArray(8 + entryCount * 2)
        // First 8 bytes: X and Y amounts (arbitrary)
        for (i in 0 until 8) cvmListBytes[i] = random.nextInt(256).toByte()
        // CVM entries
        for (i in 0 until entryCount) {
            val cvmCode = knownCvmCodes[random.nextInt(knownCvmCodes.size)]
            cvmListBytes[8 + i * 2] = cvmCode.toByte()
            cvmListBytes[8 + i * 2 + 1] = 0x00.toByte() // condition: always
        }
        map[0x8E] = cvmListBytes
    }

    map
}

// ---------------------------------------------------------------------------
// Arb.pan() — generates PAN strings of 13–19 digits
// ---------------------------------------------------------------------------

/**
 * Generates a random PAN (Primary Account Number) string of 13–19 decimal digits.
 *
 * Used by Property 14: Track2 round-trip.
 */
fun Arb.Companion.pan(): Arb<String> = arbitrary { rs ->
    val random = rs.random
    val length = 13 + random.nextInt(7) // 13..19
    (1..length).map { random.nextInt(10).toString() }.joinToString("")
}

// ---------------------------------------------------------------------------
// Arb.expiry() — generates YYMM strings (e.g., "2612")
// ---------------------------------------------------------------------------

/**
 * Generates a random expiry string in YYMM format (e.g., "2612" = December 2026).
 *
 * - YY: 00–99 (two-digit year)
 * - MM: 01–12 (two-digit month, zero-padded)
 *
 * Used by Property 14: Track2 round-trip.
 */
fun Arb.Companion.expiry(): Arb<String> = arbitrary { rs ->
    val random = rs.random
    val yy = random.nextInt(100).toString().padStart(2, '0') // "00".."99"
    val mm = (1 + random.nextInt(12)).toString().padStart(2, '0') // "01".."12"
    "$yy$mm"
}

// ---------------------------------------------------------------------------
// Arb.emvResponseMissingOneTag — generates a mock IsoDep setup missing one mandatory tag
// ---------------------------------------------------------------------------

/**
 * Mandatory EMV tag identifiers and their integer keys (as used in EmvKernel.emvData map).
 * These are the 7 tags that EmvKernel.readCard checks before returning success.
 */
private data class MandatoryTagSpec(
    val tagId: ByteArray,
    val mapKey: Int,
    val valueRange: IntRange
)

private val MANDATORY_TAG_SPECS: List<MandatoryTagSpec> = listOf(
    MandatoryTagSpec(byteArrayOf(0x57),                        0x57,   8..19),  // Track 2 Equivalent Data
    MandatoryTagSpec(byteArrayOf(0x5A),                        0x5A,   7..10),  // Application PAN
    MandatoryTagSpec(byteArrayOf(0x5F, 0x24),                  0x5F24, 3..3),   // Application Expiry Date
    MandatoryTagSpec(byteArrayOf(0x9F.toByte(), 0x26),         0x9F26, 8..8),   // Application Cryptogram
    MandatoryTagSpec(byteArrayOf(0x9F.toByte(), 0x27),         0x9F27, 1..1),   // Cryptogram Information Data
    MandatoryTagSpec(byteArrayOf(0x82.toByte()),                0x82,   2..2),   // Application Interchange Profile
    MandatoryTagSpec(byteArrayOf(0x94.toByte()),                0x94,   4..4)    // Application File Locator
)

/**
 * Encodes a single BER-TLV primitive tag to bytes: [tag bytes][length byte][value bytes].
 * Only handles short-form length (value <= 127 bytes), which is sufficient for EMV tags.
 */
private fun encodePrimitiveTlv(tagBytes: ByteArray, value: ByteArray): ByteArray {
    return tagBytes + byteArrayOf(value.size.toByte()) + value
}

/**
 * Encodes a constructed BER-TLV tag wrapping the given children bytes.
 * Tag 0x70 (Record Template) is used as the wrapper.
 */
private fun encodeConstructedTlv(tagByte: Byte, childrenBytes: ByteArray): ByteArray {
    return byteArrayOf(tagByte, childrenBytes.size.toByte()) + childrenBytes
}

/**
 * Builds a minimal valid PPSE SELECT response containing one AID (A0000000031010 = Visa).
 * Structure: 6F [FCI Template] → A5 [FCI Proprietary] → BF0C [Issuer Discretionary] → 61 [App Template] → 4F [AID] + 87 [Priority]
 * Returns the response bytes with 90 00 status appended.
 */
private fun buildPpseResponse(): ByteArray {
    val aid = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10) // Visa Credit AID
    val aidTlv = encodePrimitiveTlv(byteArrayOf(0x4F), aid)
    val priorityTlv = encodePrimitiveTlv(byteArrayOf(0x87.toByte()), byteArrayOf(0x01))
    val appTemplate = encodeConstructedTlv(0x61, aidTlv + priorityTlv)
    val issuerDiscretionary = encodeConstructedTlv(0xBF.toByte(), byteArrayOf(0x0C) + byteArrayOf(appTemplate.size.toByte()) + appTemplate)
    // BF0C is a 2-byte tag — need to encode it properly
    val bf0cChildren = appTemplate
    val bf0cTlv = byteArrayOf(0xBF.toByte(), 0x0C, bf0cChildren.size.toByte()) + bf0cChildren
    val fciProprietary = encodeConstructedTlv(0xA5.toByte(), bf0cTlv)
    val fciTemplate = encodeConstructedTlv(0x6F, fciProprietary)
    return fciTemplate + byteArrayOf(0x90.toByte(), 0x00)
}

/**
 * Builds a minimal valid SELECT AID response (FCI with PDOL tag 9F38).
 * Returns the response bytes with 90 00 status appended.
 */
private fun buildSelectAidResponse(): ByteArray {
    // PDOL: tag 9F38, value = list of terminal data objects (we use a simple 1-entry PDOL)
    // The actual PDOL value content doesn't matter for our test — just needs to be non-empty
    // so EmvKernel uses TERMINAL_PDOL_DATA in the GPO command.
    val pdolValue = byteArrayOf(0x9F.toByte(), 0x66, 0x04) // TTQ, 4 bytes
    val pdolTlv = encodePrimitiveTlv(byteArrayOf(0x9F.toByte(), 0x38), pdolValue)
    val fciProprietary = encodeConstructedTlv(0xA5.toByte(), pdolTlv)
    val fciTemplate = encodeConstructedTlv(0x6F, fciProprietary)
    return fciTemplate + byteArrayOf(0x90.toByte(), 0x00)
}

/**
 * Builds a valid GPO response in Format 2 (tag 77) containing AIP (tag 82) and AFL (tag 94),
 * unless [omitTag82] or [omitTag94] is true.
 *
 * The AFL encodes exactly 1 record in SFI 1: [0x0C, 0x01, 0x01, 0x00]
 * (SFI=1 → (1 shl 3) or 4 = 0x0C, firstRecord=1, lastRecord=1, offlineAuth=0)
 *
 * Returns the response bytes with 90 00 status appended.
 */
private fun buildGpoResponse(omitTag82: Boolean, omitTag94: Boolean): ByteArray {
    val aip = byteArrayOf(0x40.toByte(), 0x00) // AIP: SDA supported
    val afl = byteArrayOf(0x0C, 0x01, 0x01, 0x00) // SFI=1, record 1..1

    var children = byteArrayOf()
    if (!omitTag82) {
        children += encodePrimitiveTlv(byteArrayOf(0x82.toByte()), aip)
    }
    if (!omitTag94) {
        children += encodePrimitiveTlv(byteArrayOf(0x94.toByte()), afl)
    }

    // Wrap in tag 77 (Response Message Template Format 2)
    val response77 = encodeConstructedTlv(0x77, children)
    return response77 + byteArrayOf(0x90.toByte(), 0x00)
}

/**
 * Builds a READ RECORD response (tag 70 wrapper) containing the specified primitive tags.
 * Each entry in [tags] is a pair of (tagBytes, valueBytes).
 * Returns the response bytes with 90 00 status appended.
 */
private fun buildReadRecordResponse(tags: List<Pair<ByteArray, ByteArray>>): ByteArray {
    var children = byteArrayOf()
    for ((tagBytes, value) in tags) {
        children += encodePrimitiveTlv(tagBytes, value)
    }
    val record70 = encodeConstructedTlv(0x70, children)
    return record70 + byteArrayOf(0x90.toByte(), 0x00)
}

/**
 * Builds a GENERATE AC response (tag 77 wrapper) containing the specified primitive tags.
 * Each entry in [tags] is a pair of (tagBytes, valueBytes).
 * Returns the response bytes with 90 00 status appended.
 */
private fun buildGenerateAcResponse(tags: List<Pair<ByteArray, ByteArray>>): ByteArray {
    var children = byteArrayOf()
    for ((tagBytes, value) in tags) {
        children += encodePrimitiveTlv(tagBytes, value)
    }
    val response77 = encodeConstructedTlv(0x77, children)
    return response77 + byteArrayOf(0x90.toByte(), 0x00)
}

/**
 * Generates a [Pair<android.nfc.tech.IsoDep, Int>] where:
 * - The [IsoDep] mock is set up to return valid APDU responses for the full EMV flow
 *   (PPSE SELECT → AID SELECT → GPO → READ RECORD → GENERATE AC), but with exactly
 *   one mandatory tag missing from the combined response data.
 * - The [Int] is the map key of the omitted mandatory tag (for documentation/debugging).
 *
 * The 7 mandatory tags are: 57, 5A, 5F24, 9F26, 9F27, 82, 94.
 * One is randomly chosen to be absent; all others are present with valid values.
 *
 * Tag distribution across APDU responses:
 * - Tag 82 (AIP) and 94 (AFL): GPO response (Format 2, tag 77)
 * - Tags 57, 5A, 5F24: READ RECORD response (tag 70)
 * - Tags 9F26, 9F27: GENERATE AC response (tag 77)
 *
 * Note: Tag 5A (PAN) is listed as mandatory in the spec (Requirement 11.4) but the
 * current EmvKernel implementation derives the PAN from Tag 57 (Track2) rather than
 * checking Tag 5A directly. This generator omits Tag 5A from the READ RECORD response
 * to test the spec requirement.
 *
 * Used by Property 13: Missing mandatory tag aborts transaction.
 */
fun Arb.Companion.emvResponseMissingOneTag(): Arb<Pair<IsoDep, Int>> = arbitrary { rs ->
    val random = rs.random

    // Pick one mandatory tag to omit
    val omitIndex = random.nextInt(MANDATORY_TAG_SPECS.size)
    val omittedSpec = MANDATORY_TAG_SPECS[omitIndex]
    val omittedMapKey = omittedSpec.mapKey

    // Generate random valid values for all mandatory tags except the omitted one
    fun randomValue(spec: MandatoryTagSpec): ByteArray {
        val len = spec.valueRange.first + random.nextInt(spec.valueRange.last - spec.valueRange.first + 1)
        return ByteArray(len) { random.nextInt(256).toByte() }
    }

    // Determine which tags go in each response
    val omit57   = omittedMapKey == 0x57
    val omit5A   = omittedMapKey == 0x5A
    val omit5F24 = omittedMapKey == 0x5F24
    val omit9F26 = omittedMapKey == 0x9F26
    val omit9F27 = omittedMapKey == 0x9F27
    val omit82   = omittedMapKey == 0x82
    val omit94   = omittedMapKey == 0x94

    // Build READ RECORD response tags (57, 5A, 5F24)
    val recordTags = mutableListOf<Pair<ByteArray, ByteArray>>()
    if (!omit57)   recordTags.add(Pair(byteArrayOf(0x57), randomValue(MANDATORY_TAG_SPECS[0])))
    if (!omit5A)   recordTags.add(Pair(byteArrayOf(0x5A), randomValue(MANDATORY_TAG_SPECS[1])))
    if (!omit5F24) recordTags.add(Pair(byteArrayOf(0x5F, 0x24), randomValue(MANDATORY_TAG_SPECS[2])))

    // Build GENERATE AC response tags (9F26, 9F27)
    val acTags = mutableListOf<Pair<ByteArray, ByteArray>>()
    if (!omit9F26) acTags.add(Pair(byteArrayOf(0x9F.toByte(), 0x26), randomValue(MANDATORY_TAG_SPECS[3])))
    if (!omit9F27) acTags.add(Pair(byteArrayOf(0x9F.toByte(), 0x27), randomValue(MANDATORY_TAG_SPECS[4])))

    // Build APDU responses
    val ppseResponse      = buildPpseResponse()
    val selectAidResponse = buildSelectAidResponse()
    val gpoResponse       = buildGpoResponse(omitTag82 = omit82, omitTag94 = omit94)
    val readRecordResponse = buildReadRecordResponse(recordTags)
    val generateAcResponse = buildGenerateAcResponse(acTags)

    // Mock IsoDep
    val isoDep = mockkClass(IsoDep::class)
    every { isoDep.isConnected } returns false
    every { isoDep.connect() } returns Unit
    every { isoDep.timeout = any() } returns Unit

    // Wire up transceive responses based on APDU command bytes
    // EmvKernel sends APDUs in this order:
    //   1. SELECT PPSE  (starts with 00 A4 04 00 0E 32...)
    //   2. SELECT AID   (starts with 00 A4 04 00 07 A0...)
    //   3. GPO          (starts with 80 A8 00 00...)
    //   4. READ RECORD  (starts with 00 B2 01 0C 00)
    //   5. GENERATE AC  (starts with 80 AE 80 00...)
    every { isoDep.transceive(any()) } answers { call ->
        val apdu = call.invocation.args[0] as ByteArray
        when {
            // SELECT PPSE: CLA=00 INS=A4 P1=04 P2=00 Lc=0E data="2PAY.SYS.DDF01"
            apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                apdu.size > 5 && apdu[5] == 0x32.toByte() -> ppseResponse
            // SELECT AID: CLA=00 INS=A4 P1=04 P2=00 Lc=07 data=A0000000031010
            apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
                apdu.size > 5 && apdu[5] == 0xA0.toByte() -> selectAidResponse
            // GPO: CLA=80 INS=A8
            apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xA8.toByte() -> gpoResponse
            // READ RECORD: CLA=00 INS=B2
            apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB2.toByte() -> readRecordResponse
            // GENERATE AC: CLA=80 INS=AE
            apdu.size >= 2 && apdu[0] == 0x80.toByte() && apdu[1] == 0xAE.toByte() -> generateAcResponse
            // Default: return success with empty data
            else -> byteArrayOf(0x90.toByte(), 0x00)
        }
    }

    Pair(isoDep, omittedMapKey)
}

// ---------------------------------------------------------------------------
// Arb.cvmListWithFirstEntry() — generates a CVM List map and the expected result
// ---------------------------------------------------------------------------

/**
 * Generates a [Pair<Map<Int, ByteArray>, CvmResult>] for Property 6.
 *
 * The map contains tag `0x8E` (CVM List) encoding a non-empty, shuffled list
 * of supported CVM entries (ONLINE_PIN, SIGNATURE, NO_CVM). No CDCVM indicator
 * (tag `0x9F34`) is included, so [EmvKernel.determineCvm] will fall through to
 * the CVM List branch.
 *
 * The second element of the pair is the [CvmResult] that corresponds to the
 * **first** entry in the generated CVM List — this is what [EmvKernel.determineCvm]
 * must return.
 *
 * CVM code mapping (matches EmvKernel.determineCvm):
 *   - `ONLINE_PIN` → 0x02
 *   - `SIGNATURE`  → 0x1E
 *   - `NO_CVM`     → 0x3F
 *
 * CVM List byte layout (ISO/IEC 7816-4 / EMV Book 3):
 *   - Bytes 0–3: X amount (4 bytes, zeroed — not used by determineCvm)
 *   - Bytes 4–7: Y amount (4 bytes, zeroed — not used by determineCvm)
 *   - Bytes 8+:  CVM entries, 2 bytes each: [cvmCode, cvmCondition]
 *
 * Used by Property 6: CVM list priority order is respected.
 */
fun Arb.Companion.cvmListWithFirstEntry(): Arb<Pair<Map<Int, ByteArray>, com.darkwizards.payments.data.model.CvmResult>> =
    arbitrary { rs ->
        val random = rs.random

        // The three CVM results that determineCvm recognises from the CVM List
        val supportedCvms = listOf(
            com.darkwizards.payments.data.model.CvmResult.ONLINE_PIN,
            com.darkwizards.payments.data.model.CvmResult.SIGNATURE,
            com.darkwizards.payments.data.model.CvmResult.NO_CVM
        )

        // CVM code byte for each CvmResult (matches EmvKernel.determineCvm switch)
        fun cvmCode(cvm: com.darkwizards.payments.data.model.CvmResult): Byte = when (cvm) {
            com.darkwizards.payments.data.model.CvmResult.ONLINE_PIN -> 0x02.toByte()
            com.darkwizards.payments.data.model.CvmResult.SIGNATURE  -> 0x1E.toByte()
            com.darkwizards.payments.data.model.CvmResult.NO_CVM     -> 0x3F.toByte()
            com.darkwizards.payments.data.model.CvmResult.CDCVM      -> 0x3F.toByte() // fallback, not used
        }

        // Generate a non-empty shuffled list of 1–3 supported CVM entries.
        // Shuffle so the first entry is random across iterations.
        val entryCount = 1 + random.nextInt(3) // 1..3
        val entries = supportedCvms.shuffled(random).take(entryCount)

        // The expected result is the first entry in the list
        val expectedFirst = entries.first()

        // Encode as a CVM List byte array:
        //   8 bytes (X + Y amounts, zeroed) + 2 bytes per entry
        val cvmListBytes = ByteArray(8 + entries.size * 2)
        // Bytes 0–7: X and Y amounts — leave as zero
        entries.forEachIndexed { i, cvm ->
            cvmListBytes[8 + i * 2]     = cvmCode(cvm)
            cvmListBytes[8 + i * 2 + 1] = 0x00.toByte() // condition: always
        }

        // Build the emvData map — only tag 0x8E, no CDCVM indicator (0x9F34)
        val emvData: Map<Int, ByteArray> = mapOf(0x8E to cvmListBytes)

        Pair(emvData, expectedFirst)
    }
