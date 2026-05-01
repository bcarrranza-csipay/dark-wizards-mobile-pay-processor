package com.darkwizards.payments.ui.screen

// Feature: payment-ui-redesign, Property 5: Transaction list is reverse-chronological

import com.darkwizards.payments.data.model.PaymentType
import com.darkwizards.payments.data.model.TransactionRecord
import com.darkwizards.payments.data.model.TransactionStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.forAll
import java.time.LocalDateTime

/**
 * Property-based tests for [sortTransactionsReverseChronological].
 *
 * **Validates: Requirements 4.1**
 */
class TransactionReportScreenPropertyTest : FunSpec({

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    /**
     * Generates a single [TransactionRecord] with a unique [LocalDateTime] offset
     * from a base epoch by the given number of seconds.
     */
    fun makeTransaction(id: String, offsetSeconds: Long): TransactionRecord {
        return TransactionRecord(
            transactionId = id,
            amount = "\$10.00",
            amountCents = 1000,
            feeAmount = "\$0.00",
            dateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0).plusSeconds(offsetSeconds),
            paymentType = PaymentType.CARD_PRESENT,
            status = TransactionStatus.APPROVED,
            approvalNumber = "AUTH$id",
            accountLast4 = "1234",
            accountType = "VISA"
        )
    }

    /**
     * Generates a non-empty list of [TransactionRecord] objects with distinct timestamps.
     *
     * Each record gets a unique offset in seconds (0, 1, 2, ...) so all timestamps
     * are guaranteed to be distinct. The list is shuffled to ensure the sort is
     * actually exercised.
     */
    val transactionListArb: Arb<List<TransactionRecord>> = arbitrary { rs ->
        val random = rs.random
        // Generate between 2 and 20 transactions
        val size = 2 + random.nextInt(19)

        // Create a pool of distinct offsets and shuffle them so the input order is random
        val offsets = (0 until size).map { it.toLong() * 60L } // 1-minute apart
        val shuffledOffsets = offsets.shuffled(random)

        shuffledOffsets.mapIndexed { index, offset ->
            makeTransaction("TX${index.toString().padStart(4, '0')}", offset)
        }
    }

    /**
     * Generates a single-element list (edge case: trivially sorted).
     */
    val singleTransactionListArb: Arb<List<TransactionRecord>> = arbitrary { rs ->
        val random = rs.random
        val offset = random.nextLong(0L, 1_000_000L)
        listOf(makeTransaction("TX0001", offset))
    }

    // -------------------------------------------------------------------------
    // Property 5: Transaction list is reverse-chronological
    // -------------------------------------------------------------------------
    // For any non-empty list of TransactionRecord objects with distinct timestamps,
    // sortTransactionsReverseChronological returns them in descending order by dateTime.
    //
    // **Validates: Requirements 4.1**
    // -------------------------------------------------------------------------

    test("Property 5: sorted list is in descending dateTime order") {
        // Feature: payment-ui-redesign, Property 5
        forAll(
            PropTestConfig(iterations = 200),
            transactionListArb
        ) { transactions ->
            val sorted = sortTransactionsReverseChronological(transactions)

            // Verify each consecutive pair is in descending (or equal) order
            sorted.zipWithNext().all { (earlier, later) ->
                !earlier.dateTime.isBefore(later.dateTime)
            }
        }
    }

    test("Property 5: first element has the most recent dateTime") {
        // Feature: payment-ui-redesign, Property 5
        forAll(
            PropTestConfig(iterations = 200),
            transactionListArb
        ) { transactions ->
            val sorted = sortTransactionsReverseChronological(transactions)
            val maxDateTime = transactions.maxOf { it.dateTime }

            // The first element must have the maximum (most recent) dateTime
            sorted.first().dateTime == maxDateTime
        }
    }

    test("Property 5: last element has the oldest dateTime") {
        // Feature: payment-ui-redesign, Property 5
        forAll(
            PropTestConfig(iterations = 200),
            transactionListArb
        ) { transactions ->
            val sorted = sortTransactionsReverseChronological(transactions)
            val minDateTime = transactions.minOf { it.dateTime }

            // The last element must have the minimum (oldest) dateTime
            sorted.last().dateTime == minDateTime
        }
    }

    test("Property 5: sorted list contains the same elements as the input list") {
        // Feature: payment-ui-redesign, Property 5
        // Sorting must not add or remove elements — only reorder them.
        forAll(
            PropTestConfig(iterations = 200),
            transactionListArb
        ) { transactions ->
            val sorted = sortTransactionsReverseChronological(transactions)

            // Same size
            sorted.size == transactions.size &&
                // Same elements (by transactionId)
                sorted.map { it.transactionId }.toSet() == transactions.map { it.transactionId }.toSet()
        }
    }

    test("Property 5: single-element list is unchanged after sorting") {
        // Feature: payment-ui-redesign, Property 5
        forAll(
            PropTestConfig(iterations = 100),
            singleTransactionListArb
        ) { transactions ->
            val sorted = sortTransactionsReverseChronological(transactions)
            sorted.size == 1 && sorted.first().transactionId == transactions.first().transactionId
        }
    }

    test("Property 5: sorting is idempotent — sorting an already-sorted list produces the same result") {
        // Feature: payment-ui-redesign, Property 5
        forAll(
            PropTestConfig(iterations = 200),
            transactionListArb
        ) { transactions ->
            val sortedOnce = sortTransactionsReverseChronological(transactions)
            val sortedTwice = sortTransactionsReverseChronological(sortedOnce)

            // Sorting an already-sorted list should produce the same order
            sortedOnce.map { it.transactionId } == sortedTwice.map { it.transactionId }
        }
    }
})
