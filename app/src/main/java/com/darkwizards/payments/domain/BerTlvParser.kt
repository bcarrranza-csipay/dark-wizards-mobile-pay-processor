package com.darkwizards.payments.domain

import com.darkwizards.payments.data.model.TlvTag

/**
 * Pure Kotlin BER-TLV parser following ISO 7816-4 encoding rules.
 * No Android dependencies.
 */
object BerTlvParser {

    /**
     * Parses a BER-TLV encoded byte array into a list of [TlvTag] objects.
     *
     * Handles:
     * - 1-byte and 2-byte tag identifiers
     * - Short-form and long-form length encodings
     * - Recursive parsing of constructed tags (bit 6 of first tag byte set)
     *
     * Returns an empty list for empty input. On malformed/truncated data,
     * returns whatever tags were successfully parsed up to the error point.
     */
    fun parse(bytes: ByteArray): List<TlvTag> {
        if (bytes.isEmpty()) return emptyList()
        return parseInternal(bytes, 0, bytes.size)
    }

    private fun parseInternal(bytes: ByteArray, startOffset: Int, endOffset: Int): List<TlvTag> {
        val tags = mutableListOf<TlvTag>()
        var offset = startOffset

        while (offset < endOffset) {
            // Need at least 2 bytes: tag + length
            if (offset >= endOffset) break

            val firstByte = bytes[offset].toInt() and 0xFF

            // Skip padding bytes (0x00 and 0xFF are used as padding in some EMV structures)
            if (firstByte == 0x00 || firstByte == 0xFF) {
                offset++
                continue
            }

            // --- Parse tag identifier ---
            val tagStart = offset
            val isMultiByte = (firstByte and 0x1F) == 0x1F
            val isConstructed = (firstByte and 0x20) != 0

            offset++ // consume first tag byte

            if (isMultiByte) {
                // Multi-byte tag: subsequent bytes have bit 8 set until the last byte
                if (offset >= endOffset) break // truncated
                while (offset < endOffset) {
                    val b = bytes[offset].toInt() and 0xFF
                    offset++
                    if ((b and 0x80) == 0) break // last byte of tag (bit 8 clear)
                }
            }

            val tagBytes = bytes.copyOfRange(tagStart, offset)

            // --- Parse length ---
            if (offset >= endOffset) break // truncated

            val firstLenByte = bytes[offset].toInt() and 0xFF
            offset++

            val length: Int
            if (firstLenByte <= 0x7F) {
                // Short form: length is directly encoded
                length = firstLenByte
            } else {
                // Long form: lower 7 bits = number of subsequent length bytes
                val numLenBytes = firstLenByte and 0x7F
                if (numLenBytes == 0 || offset + numLenBytes > endOffset) break // truncated or indefinite form
                var len = 0
                for (i in 0 until numLenBytes) {
                    len = (len shl 8) or (bytes[offset].toInt() and 0xFF)
                    offset++
                }
                length = len
            }

            // --- Parse value ---
            if (offset + length > endOffset) break // truncated value

            val valueBytes = bytes.copyOfRange(offset, offset + length)
            offset += length

            // --- Recursively parse constructed tags ---
            val children = if (isConstructed && length > 0) {
                parseInternal(valueBytes, 0, valueBytes.size)
            } else {
                emptyList()
            }

            tags.add(TlvTag(tag = tagBytes, value = valueBytes, children = children))
        }

        return tags
    }

    /**
     * Re-encodes a list of [TlvTag] objects back to a BER-TLV byte array.
     *
     * For constructed tags, the value is re-encoded from the children list
     * (not the raw value bytes), ensuring round-trip consistency.
     */
    fun encode(tags: List<TlvTag>): ByteArray {
        val result = mutableListOf<Byte>()
        for (tag in tags) {
            // Write tag bytes
            result.addAll(tag.tag.toList())

            // Determine value to encode
            val valueBytes = if (tag.children.isNotEmpty()) {
                encode(tag.children)
            } else {
                tag.value
            }

            // Write length
            val length = valueBytes.size
            when {
                length <= 0x7F -> result.add(length.toByte())
                length <= 0xFF -> {
                    result.add(0x81.toByte())
                    result.add(length.toByte())
                }
                length <= 0xFFFF -> {
                    result.add(0x82.toByte())
                    result.add((length shr 8).toByte())
                    result.add((length and 0xFF).toByte())
                }
                else -> {
                    result.add(0x83.toByte())
                    result.add((length shr 16).toByte())
                    result.add((length shr 8 and 0xFF).toByte())
                    result.add((length and 0xFF).toByte())
                }
            }

            // Write value
            result.addAll(valueBytes.toList())
        }
        return result.toByteArray()
    }

    /**
     * Searches for a tag with the given [tagId] in the tag list, recursively
     * searching through children of constructed tags.
     *
     * Returns the first matching [TlvTag], or null if not found.
     */
    fun findTag(tags: List<TlvTag>, tagId: ByteArray): TlvTag? {
        for (tag in tags) {
            if (tag.tag.contentEquals(tagId)) return tag
            if (tag.children.isNotEmpty()) {
                val found = findTag(tag.children, tagId)
                if (found != null) return found
            }
        }
        return null
    }
}
