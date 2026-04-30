package com.darkwizards.payments.domain

// Feature: nfc-tap-to-pay, Property 11: BER-TLV round-trip
// Feature: nfc-tap-to-pay, Property 12: Mandatory tag extraction

import com.darkwizards.payments.util.tlvStructure
import com.darkwizards.payments.util.tlvStructureWithMandatoryTags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.PropTestConfig
import io.kotest.property.Arb
import io.kotest.property.forAll

/**
 * Property-based tests for [BerTlvParser].
 *
 * **Validates: Requirements 11.1, 11.2, 11.3, 11.5**
 */
class BerTlvParserPropertyTest : FunSpec({

    // -------------------------------------------------------------------------
    // Property 11: BER-TLV round-trip
    // -------------------------------------------------------------------------
    // For any valid BER-TLV encoded byte array (including nested constructed tags),
    // BerTlvParser.parse then BerTlvParser.encode SHALL produce a byte-for-byte
    // identical result.
    //
    // Strategy:
    //   1. Generate a structurally valid TLV tag list using Arb.tlvStructure(maxDepth=3).
    //   2. Encode it to bytes with BerTlvParser.encode  → originalBytes
    //   3. Parse originalBytes with BerTlvParser.parse  → parsedTags
    //   4. Re-encode parsedTags with BerTlvParser.encode → reEncodedBytes
    //   5. Assert reEncodedBytes contentEquals originalBytes (byte-for-byte identical).
    //
    // This validates that the encode→parse→encode pipeline is idempotent, which
    // is the formal statement of the round-trip property.
    // -------------------------------------------------------------------------

    test("Property 11: BER-TLV round-trip — encode then parse then encode produces identical bytes") {
        // Feature: nfc-tap-to-pay, Property 11: BER-TLV round-trip
        forAll(
            PropTestConfig(iterations = 100),
            Arb.tlvStructure(maxDepth = 3)
        ) { tlvList ->
            // Step 1: encode the generated TLV structure to bytes
            val originalBytes = BerTlvParser.encode(tlvList)

            // Step 2: parse the bytes back into a tag list
            val parsedTags = BerTlvParser.parse(originalBytes)

            // Step 3: re-encode the parsed tag list
            val reEncodedBytes = BerTlvParser.encode(parsedTags)

            // Step 4: assert byte-for-byte equality
            reEncodedBytes.contentEquals(originalBytes)
        }
    }

    // -------------------------------------------------------------------------
    // Property 12: Mandatory tag extraction
    // -------------------------------------------------------------------------
    // For any TLV structure containing all mandatory tags (57, 5A, 5F24, 9F26,
    // 9F27, 82, 94) in any order or nesting depth, BerTlvParser.findTag SHALL
    // successfully locate each tag and return its correct value bytes.
    //
    // Strategy:
    //   1. Generate a TLV tag list that always contains all 7 mandatory tags,
    //      possibly nested inside constructed wrapper tags, using
    //      Arb.tlvStructureWithMandatoryTags().
    //   2. For each mandatory tag ID, call BerTlvParser.findTag(tags, tagId).
    //   3. Assert the result is not null (tag was found).
    //   4. Assert the found tag's value bytes match the value that was generated
    //      for that tag (correct value returned, not a different tag's value).
    //
    // This validates that findTag's recursive search correctly traverses
    // constructed tags at any nesting depth.
    // -------------------------------------------------------------------------

    test("Property 12: for any TLV structure with all mandatory tags, findTag locates each tag correctly") {
        // Feature: nfc-tap-to-pay, Property 12: Mandatory tag extraction
        // **Validates: Requirements 11.3**
        forAll(
            PropTestConfig(iterations = 100),
            Arb.tlvStructureWithMandatoryTags()
        ) { tlvList ->
            // The 7 mandatory EMV tag identifiers
            val mandatoryTagIds = listOf(
                byteArrayOf(0x57),                          // Track 2 Equivalent Data
                byteArrayOf(0x5A),                          // Application PAN
                byteArrayOf(0x5F, 0x24),                    // Application Expiry Date
                byteArrayOf(0x9F.toByte(), 0x26),           // Application Cryptogram (ARQC)
                byteArrayOf(0x9F.toByte(), 0x27),           // Cryptogram Information Data
                byteArrayOf(0x82.toByte()),                  // Application Interchange Profile
                byteArrayOf(0x94.toByte())                   // Application File Locator
            )

            // Helper: recursively find the expected TlvTag leaf in the generated structure
            // so we can compare the value bytes (handles nested placement)
            fun findExpectedTag(tags: List<com.darkwizards.payments.data.model.TlvTag>, tagId: ByteArray): com.darkwizards.payments.data.model.TlvTag? {
                for (tag in tags) {
                    if (tag.tag.contentEquals(tagId)) return tag
                    if (tag.children.isNotEmpty()) {
                        val found = findExpectedTag(tag.children, tagId)
                        if (found != null) return found
                    }
                }
                return null
            }

            // For each mandatory tag, verify findTag locates it and returns the correct value
            mandatoryTagIds.all { tagId ->
                val found = BerTlvParser.findTag(tlvList, tagId)
                val expected = findExpectedTag(tlvList, tagId)

                // found must not be null (tag must be located)
                found != null &&
                // found value must match the expected value bytes exactly
                expected != null &&
                found.value.contentEquals(expected.value)
            }
        }
    }
})
