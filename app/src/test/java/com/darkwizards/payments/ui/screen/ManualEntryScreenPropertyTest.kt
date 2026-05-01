package com.darkwizards.payments.ui.screen

// Feature: payment-ui-redesign, Property 11: Expiry auto-formatting inserts slash correctly
// Feature: payment-ui-redesign, Property 12: Manual entry form validation rejects invalid inputs
// Feature: payment-ui-redesign, Property 13: Validation errors clear when field is corrected
// Feature: payment-ui-redesign, Property 8: AVS fields visibility matches toggle state

import androidx.compose.ui.text.AnnotatedString
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.filter
import io.kotest.property.forAll

/**
 * Property-based tests for [ManualEntryScreen] pure logic functions.
 *
 * **Validates: Requirements 14.3, 14.5, 14.6, 14.7, 16.3, 16.4, 7.2, 7.3**
 */
class ManualEntryScreenPropertyTest : FunSpec({

    // =========================================================================
    // Generators
    // =========================================================================

    /** Generates a sequence of 1–4 digit characters (valid raw expiry input range). */
    val digitSequenceArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        val length = 1 + random.nextInt(4)  // 1..4 digits
        (0 until length).map { random.nextInt(10).toString() }.joinToString("")
    }

    /** Generates exactly 4 digit characters (full MMYY raw expiry). */
    val fourDigitArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        (0 until 4).map { random.nextInt(10).toString() }.joinToString("")
    }

    /** Generates a valid MMYY expiry (month 01–12, year 00–99). */
    val validExpiryRawArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        val month = 1 + random.nextInt(12)  // 1..12
        val year = random.nextInt(100)       // 0..99
        "%02d%02d".format(month, year)
    }

    /** Generates an invalid expiry raw string (wrong length or invalid month). */
    val invalidExpiryRawArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        when (random.nextInt(4)) {
            0 -> ""  // empty
            1 -> {
                // Wrong length (1–3 or 5+ digits)
                val len = when (random.nextInt(2)) {
                    0 -> 1 + random.nextInt(3)  // 1..3
                    else -> 5 + random.nextInt(4)  // 5..8
                }
                (0 until len).map { random.nextInt(10).toString() }.joinToString("")
            }
            2 -> {
                // Invalid month (00 or 13–99)
                val month = when (random.nextInt(2)) {
                    0 -> 0  // month 00
                    else -> 13 + random.nextInt(87)  // 13..99
                }
                val year = random.nextInt(100)
                "%02d%02d".format(month, year)
            }
            else -> {
                // Contains non-digit characters
                val digits = (0 until 3).map { random.nextInt(10).toString() }.joinToString("")
                digits + "X"
            }
        }
    }

    /** Generates a valid non-empty card number string. */
    val validCardNumberArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        val length = 13 + random.nextInt(7)  // 13..19 digits
        (0 until length).map { random.nextInt(10).toString() }.joinToString("")
    }

    /** Generates a valid CVV string (3–4 digits). */
    val validCvvArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        val length = 3 + random.nextInt(2)  // 3..4 digits
        (0 until length).map { random.nextInt(10).toString() }.joinToString("")
    }

    /** Generates a valid 5-digit ZIP code. */
    val validZipArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        (0 until 5).map { random.nextInt(10).toString() }.joinToString("")
    }

    /** Generates an invalid ZIP code (not exactly 5 digits). */
    val invalidZipArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        when (random.nextInt(4)) {
            0 -> ""  // empty
            1 -> (0 until (1 + random.nextInt(4))).map { random.nextInt(10).toString() }.joinToString("")  // 1..4 digits
            2 -> (0 until (6 + random.nextInt(5))).map { random.nextInt(10).toString() }.joinToString("")  // 6..10 digits
            else -> {
                // 5 chars but with a non-digit
                val digits = (0 until 4).map { random.nextInt(10).toString() }.joinToString("")
                digits + "A"
            }
        }
    }

    // =========================================================================
    // Property 11: Expiry auto-formatting inserts slash correctly
    // =========================================================================
    // For any sequence of digit characters, the displayed value contains "/"
    // after the first two digits, and the raw stored value equals the original
    // digit sequence.
    //
    // **Validates: Requirements 14.3**
    // =========================================================================

    test("Property 11: displayed value contains '/' after first two digits for any digit sequence of length >= 3") {
        // Feature: payment-ui-redesign, Property 11
        val transformation = ExpiryVisualTransformation()

        forAll(
            PropTestConfig(iterations = 200),
            digitSequenceArb.filter { it.length >= 3 }
        ) { digits ->
            val result = transformation.filter(AnnotatedString(digits))
            val displayed = result.text.text
            // The slash must appear at index 2
            displayed.length == digits.length + 1 &&
                displayed[2] == '/' &&
                displayed.removeRange(2, 3) == digits
        }
    }

    test("Property 11: displayed value has no slash for digit sequences of length <= 2") {
        // Feature: payment-ui-redesign, Property 11
        val transformation = ExpiryVisualTransformation()

        val shortDigitArb: Arb<String> = arbitrary { rs ->
            val random = rs.random
            val length = random.nextInt(3)  // 0..2
            (0 until length).map { random.nextInt(10).toString() }.joinToString("")
        }

        forAll(
            PropTestConfig(iterations = 200),
            shortDigitArb
        ) { digits ->
            val result = transformation.filter(AnnotatedString(digits))
            val displayed = result.text.text
            // No slash for short sequences
            !displayed.contains('/') && displayed == digits
        }
    }

    test("Property 11: raw stored value equals original digit sequence (no slash in raw)") {
        // Feature: payment-ui-redesign, Property 11
        // The VisualTransformation only changes the display; the raw text is unchanged.
        val transformation = ExpiryVisualTransformation()

        forAll(
            PropTestConfig(iterations = 200),
            digitSequenceArb
        ) { digits ->
            val result = transformation.filter(AnnotatedString(digits))
            // The displayed text with slash removed equals the original digits
            val displayedWithoutSlash = result.text.text.replace("/", "")
            displayedWithoutSlash == digits
        }
    }

    test("Property 11: for any 4-digit MMYY, displayed value is exactly MM/YY") {
        // Feature: payment-ui-redesign, Property 11
        val transformation = ExpiryVisualTransformation()

        forAll(
            PropTestConfig(iterations = 200),
            fourDigitArb
        ) { mmyy ->
            val result = transformation.filter(AnnotatedString(mmyy))
            val displayed = result.text.text
            val expected = "${mmyy.substring(0, 2)}/${mmyy.substring(2)}"
            displayed == expected
        }
    }

    // =========================================================================
    // Property 12: Manual entry form validation rejects invalid inputs
    // =========================================================================
    // For any form submission where at least one required field is empty/invalid,
    // at least one inline error is shown and submitCardNotPresent is not invoked.
    //
    // **Validates: Requirements 14.5, 14.6, 14.7**
    // =========================================================================

    test("Property 12: empty card number always produces at least one error") {
        // Feature: payment-ui-redesign, Property 12
        forAll(
            PropTestConfig(iterations = 200),
            validExpiryRawArb,
            validCvvArb
        ) { expiry, cvv ->
            val errors = validateManualEntryForm(
                cardNumber = "",
                expiry = expiry,
                cvv = cvv,
                zip = "",
                avsEnabled = false
            )
            errors.isNotEmpty()
        }
    }

    test("Property 12: invalid expiry always produces at least one error") {
        // Feature: payment-ui-redesign, Property 12
        forAll(
            PropTestConfig(iterations = 200),
            validCardNumberArb,
            invalidExpiryRawArb,
            validCvvArb
        ) { cardNumber, expiry, cvv ->
            val errors = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = expiry,
                cvv = cvv,
                zip = "",
                avsEnabled = false
            )
            errors.isNotEmpty()
        }
    }

    test("Property 12: empty CVV always produces at least one error") {
        // Feature: payment-ui-redesign, Property 12
        forAll(
            PropTestConfig(iterations = 200),
            validCardNumberArb,
            validExpiryRawArb
        ) { cardNumber, expiry ->
            val errors = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = expiry,
                cvv = "",
                zip = "",
                avsEnabled = false
            )
            errors.isNotEmpty()
        }
    }

    test("Property 12: invalid ZIP with AVS enabled always produces at least one error") {
        // Feature: payment-ui-redesign, Property 12
        forAll(
            PropTestConfig(iterations = 200),
            validCardNumberArb,
            validExpiryRawArb,
            validCvvArb,
            invalidZipArb
        ) { cardNumber, expiry, cvv, zip ->
            val errors = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = expiry,
                cvv = cvv,
                zip = zip,
                avsEnabled = true
            )
            errors.isNotEmpty()
        }
    }

    test("Property 12: valid inputs with AVS off produce no errors") {
        // Feature: payment-ui-redesign, Property 12
        forAll(
            PropTestConfig(iterations = 200),
            validCardNumberArb,
            validExpiryRawArb,
            validCvvArb
        ) { cardNumber, expiry, cvv ->
            val errors = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = expiry,
                cvv = cvv,
                zip = "",
                avsEnabled = false
            )
            errors.isEmpty()
        }
    }

    test("Property 12: valid inputs with AVS on and valid ZIP produce no errors") {
        // Feature: payment-ui-redesign, Property 12
        forAll(
            PropTestConfig(iterations = 200),
            validCardNumberArb,
            validExpiryRawArb,
            validCvvArb,
            validZipArb
        ) { cardNumber, expiry, cvv, zip ->
            val errors = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = expiry,
                cvv = cvv,
                zip = zip,
                avsEnabled = true
            )
            errors.isEmpty()
        }
    }

    // =========================================================================
    // Property 13: Validation errors clear when field is corrected
    // =========================================================================
    // For any field currently showing a validation error, updating it to a valid
    // value clears the error message for that field.
    //
    // **Validates: Requirements 16.3, 16.4**
    // =========================================================================

    test("Property 13: correcting card number from empty to non-empty clears cardNumber error") {
        // Feature: payment-ui-redesign, Property 13
        forAll(
            PropTestConfig(iterations = 200),
            validCardNumberArb,
            validExpiryRawArb,
            validCvvArb
        ) { validCard, expiry, cvv ->
            // First: empty card number produces error
            val errorsWithEmpty = validateManualEntryForm(
                cardNumber = "",
                expiry = expiry,
                cvv = cvv,
                zip = "",
                avsEnabled = false
            )
            val hadError = errorsWithEmpty.containsKey("cardNumber")

            // Then: valid card number clears the error
            val errorsWithValid = validateManualEntryForm(
                cardNumber = validCard,
                expiry = expiry,
                cvv = cvv,
                zip = "",
                avsEnabled = false
            )
            val errorCleared = !errorsWithValid.containsKey("cardNumber")

            hadError && errorCleared
        }
    }

    test("Property 13: correcting expiry from invalid to valid clears expiry error") {
        // Feature: payment-ui-redesign, Property 13
        forAll(
            PropTestConfig(iterations = 200),
            validCardNumberArb,
            invalidExpiryRawArb,
            validExpiryRawArb,
            validCvvArb
        ) { cardNumber, invalidExpiry, validExpiry, cvv ->
            // First: invalid expiry produces error
            val errorsWithInvalid = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = invalidExpiry,
                cvv = cvv,
                zip = "",
                avsEnabled = false
            )
            val hadError = errorsWithInvalid.containsKey("expiry")

            // Then: valid expiry clears the error
            val errorsWithValid = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = validExpiry,
                cvv = cvv,
                zip = "",
                avsEnabled = false
            )
            val errorCleared = !errorsWithValid.containsKey("expiry")

            hadError && errorCleared
        }
    }

    test("Property 13: correcting CVV from empty to non-empty clears cvv error") {
        // Feature: payment-ui-redesign, Property 13
        forAll(
            PropTestConfig(iterations = 200),
            validCardNumberArb,
            validExpiryRawArb,
            validCvvArb
        ) { cardNumber, expiry, validCvv ->
            // First: empty CVV produces error
            val errorsWithEmpty = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = expiry,
                cvv = "",
                zip = "",
                avsEnabled = false
            )
            val hadError = errorsWithEmpty.containsKey("cvv")

            // Then: valid CVV clears the error
            val errorsWithValid = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = expiry,
                cvv = validCvv,
                zip = "",
                avsEnabled = false
            )
            val errorCleared = !errorsWithValid.containsKey("cvv")

            hadError && errorCleared
        }
    }

    test("Property 13: correcting ZIP from invalid to valid clears zip error") {
        // Feature: payment-ui-redesign, Property 13
        forAll(
            PropTestConfig(iterations = 200),
            validCardNumberArb,
            validExpiryRawArb,
            validCvvArb,
            invalidZipArb,
            validZipArb
        ) { cardNumber, expiry, cvv, invalidZip, validZip ->
            // First: invalid ZIP with AVS on produces error
            val errorsWithInvalid = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = expiry,
                cvv = cvv,
                zip = invalidZip,
                avsEnabled = true
            )
            val hadError = errorsWithInvalid.containsKey("zip")

            // Then: valid ZIP clears the error
            val errorsWithValid = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = expiry,
                cvv = cvv,
                zip = validZip,
                avsEnabled = true
            )
            val errorCleared = !errorsWithValid.containsKey("zip")

            hadError && errorCleared
        }
    }

    // =========================================================================
    // Property 8: AVS fields visibility matches toggle state
    // =========================================================================
    // For any AVS toggle state, ManualEntryScreen displays AVS fields iff the
    // toggle is true.
    //
    // We test this via the pure [shouldShowAvsFields] function.
    //
    // **Validates: Requirements 7.2, 7.3**
    // =========================================================================

    test("Property 8: shouldShowAvsFields returns true iff avsEnabled is true") {
        // Feature: payment-ui-redesign, Property 8
        forAll(
            PropTestConfig(iterations = 200),
            Arb.boolean()
        ) { avsEnabled ->
            shouldShowAvsFields(avsEnabled) == avsEnabled
        }
    }

    test("Property 8: AVS fields are validated iff avsEnabled is true") {
        // Feature: payment-ui-redesign, Property 8
        // When AVS is enabled, an invalid ZIP produces a zip error.
        // When AVS is disabled, the same invalid ZIP produces no zip error.
        forAll(
            PropTestConfig(iterations = 200),
            validCardNumberArb,
            validExpiryRawArb,
            validCvvArb,
            invalidZipArb
        ) { cardNumber, expiry, cvv, invalidZip ->
            val errorsAvsOn = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = expiry,
                cvv = cvv,
                zip = invalidZip,
                avsEnabled = true
            )
            val errorsAvsOff = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = expiry,
                cvv = cvv,
                zip = invalidZip,
                avsEnabled = false
            )
            // AVS on: zip error present; AVS off: zip error absent
            errorsAvsOn.containsKey("zip") && !errorsAvsOff.containsKey("zip")
        }
    }

    test("Property 8: valid ZIP with AVS enabled produces no zip error") {
        // Feature: payment-ui-redesign, Property 8
        forAll(
            PropTestConfig(iterations = 200),
            validCardNumberArb,
            validExpiryRawArb,
            validCvvArb,
            validZipArb
        ) { cardNumber, expiry, cvv, zip ->
            val errors = validateManualEntryForm(
                cardNumber = cardNumber,
                expiry = expiry,
                cvv = cvv,
                zip = zip,
                avsEnabled = true
            )
            !errors.containsKey("zip")
        }
    }
})
