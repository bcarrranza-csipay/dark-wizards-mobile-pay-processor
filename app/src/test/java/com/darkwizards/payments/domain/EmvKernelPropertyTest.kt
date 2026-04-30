package com.darkwizards.payments.domain

// Feature: nfc-tap-to-pay, Property 1: AID priority selection
// Feature: nfc-tap-to-pay, Property 2: AFL record count
// Feature: nfc-tap-to-pay, Property 3: Non-success APDU aborts
// Feature: nfc-tap-to-pay, Property 5: CDCVM suppresses prompts
// Feature: nfc-tap-to-pay, Property 6: CVM list priority
// Feature: nfc-tap-to-pay, Property 13: Missing mandatory tag aborts

import android.nfc.tech.IsoDep
import com.darkwizards.payments.data.model.AflEntry
import com.darkwizards.payments.data.model.AidEntry
import com.darkwizards.payments.data.model.CvmResult
import com.darkwizards.payments.util.emvCardData
import com.darkwizards.payments.util.cvmListWithFirstEntry
import com.darkwizards.payments.util.emvResponseMissingOneTag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.triple
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic

/**
 * Property-based tests for [EmvKernel].
 *
 * **Validates: Requirements 3.2**
 */
class EmvKernelPropertyTest : FunSpec({

    // -------------------------------------------------------------------------
    // Property 1: AID priority selection
    // -------------------------------------------------------------------------
    // For any list of AidEntry objects with distinct priority values,
    // EmvKernel.selectHighestPriorityAid SHALL return the entry with the lowest
    // priority indicator value (EMV convention: lower value = higher priority).
    //
    // Strategy:
    //   1. Generate a list of (ByteArray, Int) pairs using
    //      Arb.list(Arb.pair(Arb.byteArray(Arb.int(5..16)), Arb.int(1..15))).
    //      The ByteArray generator produces arrays of 5–16 random bytes.
    //   2. Deduplicate by priority value so all priorities are distinct.
    //   3. Skip (discard) cases where the deduplicated list is empty.
    //   4. Build AidEntry objects from the deduplicated pairs.
    //   5. Call EmvKernel.selectHighestPriorityAid(aids).
    //   6. Assert the returned entry has the minimum priority value in the list.
    // -------------------------------------------------------------------------

    // Custom Arb that generates ByteArrays of size 5..16 with random byte content,
    // matching the spec's Arb.byteArray(Arb.int(5..16)) intent.
    val aidBytesArb: Arb<ByteArray> = arbitrary { rs ->
        val size = 5 + rs.random.nextInt(12) // 5..16
        ByteArray(size) { rs.random.nextInt(256).toByte() }
    }

    test("Property 1: AID priority selection — selectHighestPriorityAid returns entry with lowest priority indicator") {
        // Feature: nfc-tap-to-pay, Property 1: AID priority selection
        // **Validates: Requirements 3.2**
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.list(Arb.pair(aidBytesArb, Arb.int(1..15)))
        ) { pairs ->
            // Deduplicate by priority so all priorities are distinct
            val distinctPairs = pairs
                .groupBy { (_, priority) -> priority }
                .map { (_, group) -> group.first() }

            // Skip empty lists (assume/discard)
            if (distinctPairs.isEmpty()) return@checkAll

            // Build AidEntry list from the deduplicated pairs
            val aids = distinctPairs.map { (aidBytes, priority) ->
                AidEntry(aid = aidBytes, priority = priority)
            }

            // Call the function under test
            val selected = EmvKernel.selectHighestPriorityAid(aids)

            // The selected entry must have the minimum priority value
            val expectedMinPriority = aids.minOf { it.priority }
            selected.priority shouldBe expectedMinPriority
        }
    }

    // -------------------------------------------------------------------------
    // Property 2: AFL record count drives READ RECORD commands
    // -------------------------------------------------------------------------
    // For any AFL byte array encoding N total records across one or more SFI
    // ranges, the number of READ RECORD APDUs issued SHALL equal exactly N.
    //
    // Strategy:
    //   1. Generate a list of triples (sfi: 1..10, firstRecord: 1..5, rawLast: 1..10).
    //   2. Clamp lastRecord = max(firstRecord, rawLast) so lastRecord >= firstRecord.
    //   3. Encode as a valid AFL byte array: each entry is 4 bytes:
    //      [(sfi shl 3) or 4, firstRecord, lastRecord, 0].
    //   4. Call EmvKernel.parseAfl(aflBytes) to get the parsed List<AflEntry>.
    //   5. Compute expected N = sum of (lastRecord - firstRecord + 1) per triple.
    //   6. Compute actual N = sum of (entry.lastRecord - entry.firstRecord + 1) per entry.
    //   7. Assert actual N == expected N.
    // -------------------------------------------------------------------------

    test("Property 2: AFL record count — parseAfl returns entries whose total record count equals N encoded in AFL") {
        // Feature: nfc-tap-to-pay, Property 2: AFL record count
        // **Validates: Requirements 3.5**
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.list(Arb.triple(Arb.int(1..10), Arb.int(1..5), Arb.int(1..10)))
        ) { triples ->
            // Skip empty lists
            if (triples.isEmpty()) return@checkAll

            // Clamp lastRecord so it is always >= firstRecord
            val entries = triples.map { (sfi, firstRecord, rawLast) ->
                Triple(sfi, firstRecord, maxOf(firstRecord, rawLast))
            }

            // Encode as AFL byte array: 4 bytes per entry
            val aflBytes = ByteArray(entries.size * 4)
            entries.forEachIndexed { idx, (sfi, firstRecord, lastRecord) ->
                aflBytes[idx * 4 + 0] = ((sfi shl 3) or 4).toByte()
                aflBytes[idx * 4 + 1] = firstRecord.toByte()
                aflBytes[idx * 4 + 2] = lastRecord.toByte()
                aflBytes[idx * 4 + 3] = 0
            }

            // Parse the AFL
            val parsed = EmvKernel.parseAfl(aflBytes)

            // Expected total record count from the generated triples
            val expectedN = entries.sumOf { (_, firstRecord, lastRecord) ->
                lastRecord - firstRecord + 1
            }

            // Actual total record count from parsed entries
            val actualN = parsed.sumOf { entry ->
                entry.lastRecord - entry.firstRecord + 1
            }

            actualN shouldBe expectedN
        }
    }

    // -------------------------------------------------------------------------
    // Property 3: Non-success APDU aborts transaction and shows user-friendly message
    // -------------------------------------------------------------------------
    // For any APDU response status word that is not 9000 or 61xx,
    // EmvKernel.readCard SHALL return a failure Result AND the error message
    // SHALL contain no raw hex sequences matching [0-9A-Fa-f]{4}.
    //
    // Strategy:
    //   1. Generate pairs of (sw1: Byte, sw2: Byte) filtered to exclude success
    //      status words (9000 and 61xx) using EmvKernel.isSuccessStatus.
    //   2. Mock IsoDep to return a 2-byte response [sw1, sw2] for the first
    //      APDU command (SELECT PPSE). This triggers the non-success path
    //      immediately at step 1 of readCard.
    //   3. Call EmvKernel.readCard(isoDep) and assert:
    //      a. result.isFailure == true
    //      b. The failure exception message contains no 4-char hex sequences
    //         matching [0-9A-Fa-f]{4} (no raw APDU data exposed to the user).
    // -------------------------------------------------------------------------

    test("Property 3: Non-success APDU aborts transaction and shows user-friendly message") {
        // Feature: nfc-tap-to-pay, Property 3: Non-success APDU aborts
        // **Validates: Requirements 3.7, 12.2**

        // Generator: any (sw1, sw2) pair that is NOT a success status word
        val nonSuccessSwArb = Arb.pair(Arb.byte(), Arb.byte())
            .filter { (sw1, sw2) -> !EmvKernel.isSuccessStatus(sw1, sw2) }

        // Regex to detect any 4-character hexadecimal sequence in the error message
        val rawHexPattern = Regex("[0-9A-Fa-f]{4}")

        // Mock android.util.Log so it doesn't throw in JVM unit tests
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0

        checkAll(
            PropTestConfig(iterations = 100),
            nonSuccessSwArb
        ) { (sw1, sw2) ->
            // Mock IsoDep — use mockkClass since IsoDep is a final Android class
            val isoDep = mockkClass(IsoDep::class)

            // Set up the mock: not connected initially, connect() is a no-op,
            // timeout setter is a no-op, and transceive() returns [sw1, sw2]
            every { isoDep.isConnected } returns false
            every { isoDep.connect() } returns Unit
            every { isoDep.timeout = any() } returns Unit
            // Return the non-success status word as the full APDU response
            every { isoDep.transceive(any()) } returns byteArrayOf(sw1, sw2)

            // Call the function under test
            val result = EmvKernel.readCard(isoDep)

            // Assert 1: result must be a failure
            result.isFailure shouldBe true

            // Assert 2: the error message must not contain any raw 4-char hex sequences
            val errorMessage = result.exceptionOrNull()?.message
            errorMessage shouldNotBe null
            val containsRawHex = rawHexPattern.containsMatchIn(errorMessage!!)
            containsRawHex shouldBe false
        }
    }

    // -------------------------------------------------------------------------
    // Property 5: CDCVM suppresses PIN and Signature prompts
    // -------------------------------------------------------------------------
    // For any EMV card data where the CDCVM-performed indicator is set in the
    // CVM Results data object (tag 0x9F34, byte[1] == 0x1F), the determineCvm
    // function SHALL return CvmResult.CDCVM, regardless of any CVM List contents.
    //
    // Strategy:
    //   1. Use Arb.emvCardData(cdcvmPerformed = true) to generate Map<Int, ByteArray>
    //      where tag 0x9F34 has byte[1] == 0x1F (the CDCVM indicator).
    //   2. The generator also optionally includes a CVM List (tag 0x8E) with
    //      various entries (ONLINE_PIN, SIGNATURE, NO_CVM) to verify CDCVM
    //      takes priority over the CVM list.
    //   3. Call EmvKernel.determineCvm(emvData) and assert the result is CDCVM.
    // -------------------------------------------------------------------------

    test("Property 5: CDCVM suppresses PIN and Signature prompts") {
        // Feature: nfc-tap-to-pay, Property 5: CDCVM suppresses prompts
        // **Validates: Requirements 4.4, 5.5**
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.emvCardData(cdcvmPerformed = true)
        ) { emvData ->
            val result = EmvKernel.determineCvm(emvData)
            result shouldBe CvmResult.CDCVM
        }
    }

    // -------------------------------------------------------------------------
    // Property 6: CVM list priority order is respected
    // -------------------------------------------------------------------------
    // For any CVM List containing multiple entries in a given order,
    // EmvKernel.determineCvm SHALL select the first supported CVM in list order
    // (ONLINE_PIN, SIGNATURE, or NO_CVM), regardless of what follows it.
    //
    // Strategy:
    //   1. Generate a non-empty shuffled list of CvmResult values (excluding CDCVM,
    //      which is detected via tag 0x9F34 rather than the CVM List).
    //   2. Encode the list as a valid CVM List byte array (tag 0x8E):
    //      - 8 bytes of X/Y amounts (zeroed)
    //      - For each CvmResult: 2 bytes (cvmCode, condition=0x00)
    //        where ONLINE_PIN=0x02, SIGNATURE=0x1E, NO_CVM=0x3F
    //   3. Build an emvData map containing only tag 0x8E (no CDCVM indicator).
    //   4. Call EmvKernel.determineCvm(emvData).
    //   5. Assert the result equals the first CvmResult in the generated list.
    //
    // This directly validates that determineCvm iterates the CVM List in order
    // and returns the first recognised entry, not the last or a random one.
    // -------------------------------------------------------------------------

    test("Property 6: CVM list priority order is respected") {
        // Feature: nfc-tap-to-pay, Property 6: CVM list priority
        // **Validates: Requirements 5.1**
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.cvmListWithFirstEntry()
        ) { (emvData, expectedFirst) ->
            val result = EmvKernel.determineCvm(emvData)
            result shouldBe expectedFirst
        }
    }

    // -------------------------------------------------------------------------
    // Property 13: Missing mandatory tag aborts transaction
    // -------------------------------------------------------------------------
    // For any EMV response that is missing any one of the mandatory tags
    // (57, 5A, 5F24, 9F26, 9F27, 82, 94), EmvKernel.readCard SHALL return a
    // failure Result.
    //
    // Strategy:
    //   1. Use Arb.emvResponseMissingOneTag() to generate a mock IsoDep that
    //      returns valid APDU responses for the full EMV flow (PPSE SELECT →
    //      AID SELECT → GPO → READ RECORD → GENERATE AC), but with exactly one
    //      mandatory tag absent from the combined response data.
    //   2. The generator randomly picks which of the 7 mandatory tags to omit.
    //   3. Call EmvKernel.readCard(isoDep) and assert result.isFailure == true.
    //
    // Note on Tag 5A: The spec (Requirement 11.4) lists Tag 5A (PAN) as mandatory.
    // The current EmvKernel implementation derives the PAN from Tag 57 (Track2)
    // rather than checking Tag 5A directly. When Tag 5A is omitted but Tag 57 is
    // present, the kernel can still extract the PAN from Track2. This means the
    // property may not hold for Tag 5A with the current implementation — if a
    // counterexample is found for Tag 5A (omittedMapKey == 0x5A), it indicates
    // a spec/implementation gap rather than a test error.
    // -------------------------------------------------------------------------

    test("Property 13: Missing mandatory tag aborts transaction") {
        // Feature: nfc-tap-to-pay, Property 13: Missing mandatory tag aborts
        // **Validates: Requirements 11.4**

        // Mock android.util.Log so it doesn't throw in JVM unit tests
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.emvResponseMissingOneTag()
        ) { (isoDep, omittedMapKey) ->
            val result = EmvKernel.readCard(isoDep)
            result.isFailure shouldBe true
        }
    }
})
