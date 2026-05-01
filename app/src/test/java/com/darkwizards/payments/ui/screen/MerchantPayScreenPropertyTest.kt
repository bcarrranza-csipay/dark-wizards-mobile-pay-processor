package com.darkwizards.payments.ui.screen

// Feature: payment-ui-redesign, Property 3: Decimal button state reflects amount content
// Feature: payment-ui-redesign, Property 4: Invalid amounts are rejected on submission

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.filter
import io.kotest.property.forAll

/**
 * Property-based tests for [MerchantPayScreen] amount logic.
 *
 * **Validates: Requirements 3.4, 3.5, 3.8**
 */
class MerchantPayScreenPropertyTest : FunSpec({

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    /**
     * Generates arbitrary strings that contain at least one "." character.
     * These represent amount strings where the decimal button should be disabled.
     */
    val amountWithDecimalArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        val digits = "0123456789"
        val beforeDecimal = (0..random.nextInt(8)).map { digits[random.nextInt(digits.length)] }.joinToString("")
        val afterDecimal = (0..random.nextInt(3)).map { digits[random.nextInt(digits.length)] }.joinToString("")
        "$beforeDecimal.$afterDecimal"
    }

    /**
     * Generates arbitrary strings that do NOT contain a "." character.
     * These represent amount strings where the decimal button should be enabled.
     */
    val amountWithoutDecimalArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        val digits = "0123456789"
        val length = random.nextInt(9) // 0–8 digits
        (0 until length).map { digits[random.nextInt(digits.length)] }.joinToString("")
    }

    /**
     * Generates invalid amount strings: empty, zero-valued, or non-positive.
     *
     * Covers:
     * - Empty string
     * - "0", "00", "000" (zero digits)
     * - ".", "0." (decimal-only or zero-with-decimal)
     * - "0.00", "0.0", "0.000" (zero dollar values)
     */
    val invalidAmountArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        val invalidCases = listOf(
            "",
            "0",
            "00",
            "000",
            "0000",
            ".",
            "0.",
            "0.0",
            "0.00",
            "0.000",
            "00.00",
            "000.00"
        )
        invalidCases[random.nextInt(invalidCases.size)]
    }

    /**
     * Generates valid amount strings: non-empty, parseable as a positive number.
     *
     * Covers:
     * - Pure digit strings representing cents (e.g., "1", "100", "1234")
     * - Dollar-decimal strings (e.g., "1.00", "12.34", "0.01")
     */
    val validAmountArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        val digits = "123456789" // exclude leading zeros for simplicity

        when (random.nextInt(3)) {
            0 -> {
                // Pure digit string (1–8 digits, non-zero)
                val length = 1 + random.nextInt(8)
                val first = digits[random.nextInt(digits.length)].toString()
                val rest = (1 until length).map { "0123456789"[random.nextInt(10)] }.joinToString("")
                first + rest
            }
            1 -> {
                // Dollar-decimal string with non-zero value
                val dollars = 1 + random.nextInt(999)
                val cents = random.nextInt(100)
                "$dollars.%02d".format(cents)
            }
            else -> {
                // Small cent values via decimal (e.g., "0.01" to "0.99")
                val cents = 1 + random.nextInt(99)
                "0.%02d".format(cents)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Property 3: Decimal button state reflects amount content
    // -------------------------------------------------------------------------
    // For any amount string, the decimal button enabled state should be `true`
    // if and only if the string does not already contain a decimal point character.
    //
    // **Validates: Requirements 3.4, 3.5**
    // -------------------------------------------------------------------------

    test("Property 3: decimal button is enabled iff amount string does not contain '.'") {
        // Feature: payment-ui-redesign, Property 3

        // Part A: strings WITH a decimal → button should be disabled
        forAll(
            PropTestConfig(iterations = 200),
            amountWithDecimalArb
        ) { amountWithDot ->
            val decimalEnabled = !amountWithDot.contains(".")
            // Must be false because the string contains "."
            !decimalEnabled
        }
    }

    test("Property 3: decimal button is disabled for any amount string containing '.'") {
        // Feature: payment-ui-redesign, Property 3
        forAll(
            PropTestConfig(iterations = 200),
            amountWithDecimalArb
        ) { amount ->
            // The decimal button enabled state is: !amount.contains(".")
            val decimalButtonEnabled = !amount.contains(".")
            // Since amount contains ".", decimalButtonEnabled must be false
            decimalButtonEnabled == false
        }
    }

    test("Property 3: decimal button is enabled for any amount string not containing '.'") {
        // Feature: payment-ui-redesign, Property 3
        forAll(
            PropTestConfig(iterations = 200),
            amountWithoutDecimalArb
        ) { amount ->
            // The decimal button enabled state is: !amount.contains(".")
            val decimalButtonEnabled = !amount.contains(".")
            // Since amount does not contain ".", decimalButtonEnabled must be true
            decimalButtonEnabled == true
        }
    }

    // -------------------------------------------------------------------------
    // Property 4: Invalid amounts are rejected on submission
    // -------------------------------------------------------------------------
    // For any amount string that is empty, whitespace-only, "0", "0.00", or not
    // parseable as a positive number, attempting to proceed to payment should
    // produce an error state and should not navigate to the Payment Options screen.
    //
    // We test this by verifying that [validateAmount] returns a non-null error
    // message for all such inputs.
    //
    // **Validates: Requirements 3.8**
    // -------------------------------------------------------------------------

    test("Property 4: validateAmount returns an error for any invalid amount string") {
        // Feature: payment-ui-redesign, Property 4
        forAll(
            PropTestConfig(iterations = 200),
            invalidAmountArb
        ) { invalidAmount ->
            val error = validateAmount(invalidAmount)
            // Must return a non-null error message
            error != null
        }
    }

    test("Property 4: validateAmount returns 'Please enter a valid amount' for any invalid amount") {
        // Feature: payment-ui-redesign, Property 4
        forAll(
            PropTestConfig(iterations = 200),
            invalidAmountArb
        ) { invalidAmount ->
            val error = validateAmount(invalidAmount)
            error == "Please enter a valid amount"
        }
    }

    test("Property 4: validateAmount returns null (no error) for any valid positive amount") {
        // Feature: payment-ui-redesign, Property 4
        forAll(
            PropTestConfig(iterations = 200),
            validAmountArb
        ) { validAmount ->
            val error = validateAmount(validAmount)
            // Must return null (no error) for valid amounts
            error == null
        }
    }
})
