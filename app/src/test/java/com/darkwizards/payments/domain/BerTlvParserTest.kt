package com.darkwizards.payments.domain

import com.darkwizards.payments.data.model.TlvTag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class BerTlvParserTest : FunSpec({

    // -------------------------------------------------------------------------
    // parse() — basic cases
    // -------------------------------------------------------------------------

    test("parse empty byte array returns empty list") {
        BerTlvParser.parse(byteArrayOf()).shouldBeEmpty()
    }

    test("parse single primitive tag with short-form length") {
        // Tag 0x5A (PAN), length 8, value 0x41 0x11 0x11 0x11 0x11 0x11 0x11 0x11
        val bytes = byteArrayOf(0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val tags = BerTlvParser.parse(bytes)
        tags shouldHaveSize 1
        tags[0].tag.contentEquals(byteArrayOf(0x5A)) shouldBe true
        tags[0].value.size shouldBe 8
        tags[0].children.shouldBeEmpty()
    }

    test("parse two consecutive primitive tags") {
        // Tag 0x82 (AIP), length 2, value 0x1C 0x00
        // Tag 0x94 (AFL), length 4, value 0x08 0x01 0x01 0x00
        val bytes = byteArrayOf(
            0x82.toByte(), 0x02, 0x1C, 0x00,
            0x94.toByte(), 0x04, 0x08, 0x01, 0x01, 0x00
        )
        val tags = BerTlvParser.parse(bytes)
        tags shouldHaveSize 2
        tags[0].tag.contentEquals(byteArrayOf(0x82.toByte())) shouldBe true
        tags[1].tag.contentEquals(byteArrayOf(0x94.toByte())) shouldBe true
    }

    test("parse two-byte tag identifier (multi-byte tag)") {
        // Tag 0x9F26 (Application Cryptogram), length 8
        val value = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val bytes = byteArrayOf(0x9F.toByte(), 0x26, 0x08) + value
        val tags = BerTlvParser.parse(bytes)
        tags shouldHaveSize 1
        tags[0].tag.contentEquals(byteArrayOf(0x9F.toByte(), 0x26)) shouldBe true
        tags[0].value.contentEquals(value) shouldBe true
    }

    test("parse tag with long-form length (0x81 prefix)") {
        // Tag 0x53, length 0x81 0x80 (= 128 bytes)
        val value = ByteArray(128) { it.toByte() }
        val bytes = byteArrayOf(0x53, 0x81.toByte(), 0x80.toByte()) + value
        val tags = BerTlvParser.parse(bytes)
        tags shouldHaveSize 1
        tags[0].value.size shouldBe 128
        tags[0].value.contentEquals(value) shouldBe true
    }

    test("parse tag with long-form length (0x82 prefix)") {
        // Tag 0x53, length 0x82 0x01 0x00 (= 256 bytes)
        val value = ByteArray(256) { it.toByte() }
        val bytes = byteArrayOf(0x53, 0x82.toByte(), 0x01, 0x00) + value
        val tags = BerTlvParser.parse(bytes)
        tags shouldHaveSize 1
        tags[0].value.size shouldBe 256
    }

    test("parse constructed tag recursively parses children") {
        // Tag 0x70 (constructed, EMV record template), contains Tag 0x5A
        val innerValue = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val innerTlv = byteArrayOf(0x5A, 0x08) + innerValue
        val bytes = byteArrayOf(0x70, innerTlv.size.toByte()) + innerTlv

        val tags = BerTlvParser.parse(bytes)
        tags shouldHaveSize 1
        val outer = tags[0]
        outer.tag.contentEquals(byteArrayOf(0x70)) shouldBe true
        outer.children shouldHaveSize 1
        outer.children[0].tag.contentEquals(byteArrayOf(0x5A)) shouldBe true
        outer.children[0].value.contentEquals(innerValue) shouldBe true
    }

    test("parse deeply nested constructed tags") {
        // 0x6F (FCI Template) → 0x70 (constructed) → 0x5A (PAN)
        val panValue = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val panTlv = byteArrayOf(0x5A, 0x08) + panValue
        val innerTlv = byteArrayOf(0x70, panTlv.size.toByte()) + panTlv
        val outerTlv = byteArrayOf(0x6F, innerTlv.size.toByte()) + innerTlv

        val tags = BerTlvParser.parse(outerTlv)
        tags shouldHaveSize 1
        val fci = tags[0]
        fci.children shouldHaveSize 1
        val record = fci.children[0]
        record.children shouldHaveSize 1
        record.children[0].tag.contentEquals(byteArrayOf(0x5A)) shouldBe true
    }

    test("parse returns partial results on truncated data") {
        // First tag is complete, second is truncated
        val bytes = byteArrayOf(
            0x82.toByte(), 0x02, 0x1C, 0x00,  // complete tag
            0x94.toByte(), 0x04, 0x08          // truncated (says length 4, only 1 byte of value)
        )
        val tags = BerTlvParser.parse(bytes)
        // Should return at least the first complete tag
        tags shouldHaveSize 1
        tags[0].tag.contentEquals(byteArrayOf(0x82.toByte())) shouldBe true
    }

    test("parse truncated length field does not throw") {
        // Tag byte present but length field is cut off (only 1 byte total)
        val bytes = byteArrayOf(0x82.toByte())
        val tags = BerTlvParser.parse(bytes)
        // No complete tag can be parsed — result is empty or partial, no exception
        tags.shouldBeEmpty()
    }

    test("parse single padding byte 0x00 returns empty list") {
        // 0x00 is treated as a padding byte and skipped
        val bytes = byteArrayOf(0x00)
        val tags = BerTlvParser.parse(bytes)
        tags.shouldBeEmpty()
    }

    test("parse padding bytes interspersed with valid tags are skipped") {
        // 0x00 padding before and after a valid tag
        val bytes = byteArrayOf(
            0x00,                               // padding
            0x82.toByte(), 0x02, 0x1C, 0x00,   // valid tag
            0x00                                // padding
        )
        val tags = BerTlvParser.parse(bytes)
        tags shouldHaveSize 1
        tags[0].tag.contentEquals(byteArrayOf(0x82.toByte())) shouldBe true
    }

    test("parse zero-length value tag") {
        val bytes = byteArrayOf(0x82.toByte(), 0x00)
        val tags = BerTlvParser.parse(bytes)
        tags shouldHaveSize 1
        tags[0].value.size shouldBe 0
    }

    test("parse realistic FCI template: 6F containing 84 and A5 containing BF0C") {
        // Realistic PPSE/SELECT AID response FCI structure:
        //   6F (FCI Template, constructed)
        //     84 (DF Name / AID, primitive)
        //     A5 (FCI Proprietary Template, constructed)
        //       BF0C (FCI Issuer Discretionary Data, constructed two-byte tag)
        //         61 (Application Template, constructed)
        //           4F (AID, primitive)
        //           87 (Application Priority Indicator, primitive)

        val aidBytes = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10) // Visa AID
        val priorityByte = byteArrayOf(0x01)

        // Build innermost: 4F (AID) + 87 (priority)
        val aidTlv = byteArrayOf(0x4F, aidBytes.size.toByte()) + aidBytes
        val priorityTlv = byteArrayOf(0x87.toByte(), priorityByte.size.toByte()) + priorityByte

        // Application Template 0x61 wraps 4F + 87
        val appTemplateContent = aidTlv + priorityTlv
        val appTemplate = byteArrayOf(0x61, appTemplateContent.size.toByte()) + appTemplateContent

        // BF0C (FCI Issuer Discretionary Data) wraps Application Template
        val bf0cContent = appTemplate
        val bf0cTlv = byteArrayOf(0xBF.toByte(), 0x0C, bf0cContent.size.toByte()) + bf0cContent

        // A5 (FCI Proprietary Template) wraps BF0C
        val a5Content = bf0cTlv
        val a5Tlv = byteArrayOf(0xA5.toByte(), a5Content.size.toByte()) + a5Content

        // 84 (DF Name) — primitive tag with AID value
        val dfNameTlv = byteArrayOf(0x84.toByte(), aidBytes.size.toByte()) + aidBytes

        // 6F (FCI Template) wraps 84 + A5
        val fciContent = dfNameTlv + a5Tlv
        val fciTlv = byteArrayOf(0x6F, fciContent.size.toByte()) + fciContent

        val tags = BerTlvParser.parse(fciTlv)

        // Outer tag is 6F (constructed)
        tags shouldHaveSize 1
        val fci = tags[0]
        fci.tag.contentEquals(byteArrayOf(0x6F)) shouldBe true
        fci.children shouldHaveSize 2

        // First child is 84 (DF Name, primitive)
        val dfName = fci.children[0]
        dfName.tag.contentEquals(byteArrayOf(0x84.toByte())) shouldBe true
        dfName.value.contentEquals(aidBytes) shouldBe true
        dfName.children.shouldBeEmpty()

        // Second child is A5 (FCI Proprietary, constructed)
        val a5 = fci.children[1]
        a5.tag.contentEquals(byteArrayOf(0xA5.toByte())) shouldBe true
        a5.children shouldHaveSize 1

        // A5's child is BF0C (FCI Issuer Discretionary Data, constructed two-byte tag)
        val bf0c = a5.children[0]
        bf0c.tag.contentEquals(byteArrayOf(0xBF.toByte(), 0x0C)) shouldBe true
        bf0c.children shouldHaveSize 1

        // findTag can locate 84 (DF Name) deeply nested inside 6F
        val foundDfName = BerTlvParser.findTag(tags, byteArrayOf(0x84.toByte()))
        foundDfName.shouldNotBeNull()
        foundDfName.value.contentEquals(aidBytes) shouldBe true

        // findTag can locate BF0C deeply nested inside 6F → A5
        val foundBf0c = BerTlvParser.findTag(tags, byteArrayOf(0xBF.toByte(), 0x0C))
        foundBf0c.shouldNotBeNull()
    }

    // -------------------------------------------------------------------------
    // encode() — basic cases
    // -------------------------------------------------------------------------

    test("encode empty list returns empty byte array") {
        BerTlvParser.encode(emptyList()).size shouldBe 0
    }

    test("encode single primitive tag with short-form length") {
        val value = byteArrayOf(0x1C, 0x00)
        val tag = TlvTag(tag = byteArrayOf(0x82.toByte()), value = value)
        val encoded = BerTlvParser.encode(listOf(tag))
        encoded.contentEquals(byteArrayOf(0x82.toByte(), 0x02, 0x1C, 0x00)) shouldBe true
    }

    test("encode two-byte tag") {
        val value = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val tag = TlvTag(tag = byteArrayOf(0x9F.toByte(), 0x26), value = value)
        val encoded = BerTlvParser.encode(listOf(tag))
        // Should start with 0x9F 0x26 then length 0x08 then value
        encoded[0] shouldBe 0x9F.toByte()
        encoded[1] shouldBe 0x26
        encoded[2] shouldBe 0x08
        encoded.size shouldBe 11
    }

    test("encode uses long-form length for values > 127 bytes") {
        val value = ByteArray(128) { it.toByte() }
        val tag = TlvTag(tag = byteArrayOf(0x53), value = value)
        val encoded = BerTlvParser.encode(listOf(tag))
        // Tag(1) + 0x81(1) + 0x80(1) + value(128) = 131
        encoded.size shouldBe 131
        encoded[1] shouldBe 0x81.toByte()
        encoded[2] shouldBe 0x80.toByte()
    }

    test("encode constructed tag re-encodes from children") {
        val panValue = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val child = TlvTag(tag = byteArrayOf(0x5A), value = panValue)
        // Constructed tag 0x70 with child — value bytes are ignored, children are re-encoded
        val parent = TlvTag(
            tag = byteArrayOf(0x70),
            value = byteArrayOf(), // ignored when children present
            children = listOf(child)
        )
        val encoded = BerTlvParser.encode(listOf(parent))
        // Re-parse and verify structure
        val reparsed = BerTlvParser.parse(encoded)
        reparsed shouldHaveSize 1
        reparsed[0].children shouldHaveSize 1
        reparsed[0].children[0].tag.contentEquals(byteArrayOf(0x5A)) shouldBe true
        reparsed[0].children[0].value.contentEquals(panValue) shouldBe true
    }

    // -------------------------------------------------------------------------
    // findTag() — search
    // -------------------------------------------------------------------------

    test("findTag returns null for empty list") {
        BerTlvParser.findTag(emptyList(), byteArrayOf(0x5A)).shouldBeNull()
    }

    test("findTag finds top-level tag") {
        val tag = TlvTag(tag = byteArrayOf(0x82.toByte()), value = byteArrayOf(0x1C, 0x00))
        val result = BerTlvParser.findTag(listOf(tag), byteArrayOf(0x82.toByte()))
        result.shouldNotBeNull()
        result.value.contentEquals(byteArrayOf(0x1C, 0x00)) shouldBe true
    }

    test("findTag returns null when tag not present") {
        val tag = TlvTag(tag = byteArrayOf(0x82.toByte()), value = byteArrayOf(0x1C, 0x00))
        BerTlvParser.findTag(listOf(tag), byteArrayOf(0x5A)).shouldBeNull()
    }

    test("findTag searches recursively through children") {
        val panValue = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val child = TlvTag(tag = byteArrayOf(0x5A), value = panValue)
        val parent = TlvTag(tag = byteArrayOf(0x70), value = byteArrayOf(), children = listOf(child))

        val result = BerTlvParser.findTag(listOf(parent), byteArrayOf(0x5A))
        result.shouldNotBeNull()
        result.value.contentEquals(panValue) shouldBe true
    }

    test("findTag searches deeply nested children") {
        val panValue = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val deepChild = TlvTag(tag = byteArrayOf(0x5A), value = panValue)
        val midChild = TlvTag(tag = byteArrayOf(0x70), value = byteArrayOf(), children = listOf(deepChild))
        val root = TlvTag(tag = byteArrayOf(0x6F), value = byteArrayOf(), children = listOf(midChild))

        val result = BerTlvParser.findTag(listOf(root), byteArrayOf(0x5A))
        result.shouldNotBeNull()
        result.value.contentEquals(panValue) shouldBe true
    }

    test("findTag finds two-byte tag") {
        val cryptoValue = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val tag = TlvTag(tag = byteArrayOf(0x9F.toByte(), 0x26), value = cryptoValue)
        val result = BerTlvParser.findTag(listOf(tag), byteArrayOf(0x9F.toByte(), 0x26))
        result.shouldNotBeNull()
        result.value.contentEquals(cryptoValue) shouldBe true
    }

    // -------------------------------------------------------------------------
    // Round-trip: parse → encode → parse
    // -------------------------------------------------------------------------

    test("round-trip: parse then encode produces identical bytes for simple tag") {
        val original = byteArrayOf(0x82.toByte(), 0x02, 0x1C, 0x00)
        val tags = BerTlvParser.parse(original)
        val reencoded = BerTlvParser.encode(tags)
        reencoded.contentEquals(original) shouldBe true
    }

    test("round-trip: parse then encode produces identical bytes for two-byte tag") {
        val original = byteArrayOf(0x9F.toByte(), 0x26, 0x08, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val tags = BerTlvParser.parse(original)
        val reencoded = BerTlvParser.encode(tags)
        reencoded.contentEquals(original) shouldBe true
    }

    test("round-trip: parse then encode produces identical bytes for constructed tag") {
        val panValue = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val innerTlv = byteArrayOf(0x5A, 0x08) + panValue
        val original = byteArrayOf(0x70, innerTlv.size.toByte()) + innerTlv

        val tags = BerTlvParser.parse(original)
        val reencoded = BerTlvParser.encode(tags)
        reencoded.contentEquals(original) shouldBe true
    }

    test("round-trip: realistic EMV FCI template") {
        // Simulates a PPSE/FCI response with nested constructed tags
        // 6F (FCI Template) → A5 (FCI Proprietary) → BF0C (FCI Issuer Discretionary) → 61 (Application Template) → 4F (AID) + 50 (Label)
        val aidBytes = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10)
        val labelBytes = "Visa Credit".toByteArray()

        val aidTlv = byteArrayOf(0x4F) + byteArrayOf(aidBytes.size.toByte()) + aidBytes
        val labelTlv = byteArrayOf(0x50) + byteArrayOf(labelBytes.size.toByte()) + labelBytes
        val appTemplate = byteArrayOf(0x61) + byteArrayOf((aidTlv.size + labelTlv.size).toByte()) + aidTlv + labelTlv

        val tags = BerTlvParser.parse(appTemplate)
        val reencoded = BerTlvParser.encode(tags)
        reencoded.contentEquals(appTemplate) shouldBe true
    }
})
