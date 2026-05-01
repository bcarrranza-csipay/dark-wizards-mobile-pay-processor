package com.darkwizards.payments.ui.screen

import androidx.compose.ui.text.AnnotatedString
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Unit tests for [ManualEntryScreen] pure logic functions:
 * - [ExpiryVisualTransformation]
 * - [validateManualEntryForm]
 * - [validateExpiry]
 * - [isValidZip]
 * - [convertExpiryToMmYyyy]
 *
 * _Requirements: 14.3, 14.5, 14.6, 14.7_
 */
class ManualEntryScreenTest : FunSpec({

    // =========================================================================
    // ExpiryVisualTransformation — inserts "/" after position 2
    // =========================================================================
    // Requirements: 14.3

    val transformation = ExpiryVisualTransformation()

    test("ExpiryVisualTransformation: empty string stays empty") {
        val result = transformation.filter(AnnotatedString(""))
        result.text.text shouldBe ""
    }

    test("ExpiryVisualTransformation: single digit stays unchanged") {
        val result = transformation.filter(AnnotatedString("1"))
        result.text.text shouldBe "1"
    }

    test("ExpiryVisualTransformation: two digits stay unchanged (no slash yet)") {
        val result = transformation.filter(AnnotatedString("12"))
        result.text.text shouldBe "12"
    }

    test("ExpiryVisualTransformation: three digits insert slash after position 2") {
        val result = transformation.filter(AnnotatedString("122"))
        result.text.text shouldBe "12/2"
    }

    test("ExpiryVisualTransformation: four digits (MMYY) produce MM/YY") {
        val result = transformation.filter(AnnotatedString("1225"))
        result.text.text shouldBe "12/25"
    }

    test("ExpiryVisualTransformation: '0125' produces '01/25'") {
        val result = transformation.filter(AnnotatedString("0125"))
        result.text.text shouldBe "01/25"
    }

    test("ExpiryVisualTransformation: '1299' produces '12/99'") {
        val result = transformation.filter(AnnotatedString("1299"))
        result.text.text shouldBe "12/99"
    }

    test("ExpiryVisualTransformation: '0101' produces '01/01'") {
        val result = transformation.filter(AnnotatedString("0101"))
        result.text.text shouldBe "01/01"
    }

    test("ExpiryVisualTransformation: raw value is preserved (no slash in raw)") {
        // The raw stored value should remain digits only
        val raw = "1225"
        val result = transformation.filter(AnnotatedString(raw))
        // Displayed has slash, but original text is unchanged
        result.text.text shouldBe "12/25"
        // The offset mapping: original length 4, transformed length 5
        result.offsetMapping.originalToTransformed(0) shouldBe 0
        result.offsetMapping.originalToTransformed(1) shouldBe 1
        result.offsetMapping.originalToTransformed(2) shouldBe 2
        result.offsetMapping.originalToTransformed(3) shouldBe 4
        result.offsetMapping.originalToTransformed(4) shouldBe 5
    }

    test("ExpiryVisualTransformation: offset mapping transformedToOriginal skips slash position") {
        val result = transformation.filter(AnnotatedString("1225"))
        result.offsetMapping.transformedToOriginal(0) shouldBe 0
        result.offsetMapping.transformedToOriginal(1) shouldBe 1
        result.offsetMapping.transformedToOriginal(2) shouldBe 2
        result.offsetMapping.transformedToOriginal(3) shouldBe 2  // "/" maps back to position 2
        result.offsetMapping.transformedToOriginal(4) shouldBe 3
        result.offsetMapping.transformedToOriginal(5) shouldBe 4
    }

    // =========================================================================
    // validateExpiry — raw MMYY digits
    // =========================================================================
    // Requirements: 14.6

    test("validateExpiry: valid '1225' returns null") {
        validateExpiry("1225") shouldBe null
    }

    test("validateExpiry: valid '0125' returns null") {
        validateExpiry("0125") shouldBe null
    }

    test("validateExpiry: valid '1299' returns null") {
        validateExpiry("1299") shouldBe null
    }

    test("validateExpiry: valid '0101' returns null") {
        validateExpiry("0101") shouldBe null
    }

    test("validateExpiry: empty string returns error") {
        validateExpiry("") shouldBe "Expiry must be MM/YY"
    }

    test("validateExpiry: 3 digits returns error") {
        validateExpiry("122") shouldBe "Expiry must be MM/YY"
    }

    test("validateExpiry: 5 digits returns error") {
        validateExpiry("12255") shouldBe "Expiry must be MM/YY"
    }

    test("validateExpiry: month 00 is invalid") {
        validateExpiry("0025") shouldBe "Expiry must be MM/YY"
    }

    test("validateExpiry: month 13 is invalid") {
        validateExpiry("1325") shouldBe "Expiry must be MM/YY"
    }

    test("validateExpiry: non-digit characters return error") {
        validateExpiry("12/5") shouldBe "Expiry must be MM/YY"
    }

    test("validateExpiry: string with slash returns error (slash not allowed in raw)") {
        validateExpiry("12/25") shouldBe "Expiry must be MM/YY"
    }

    // =========================================================================
    // isValidZip — exactly 5 numeric digits
    // =========================================================================
    // Requirements: 14.7

    test("isValidZip: '12345' is valid") {
        isValidZip("12345") shouldBe true
    }

    test("isValidZip: '00000' is valid") {
        isValidZip("00000") shouldBe true
    }

    test("isValidZip: '99999' is valid") {
        isValidZip("99999") shouldBe true
    }

    test("isValidZip: empty string is invalid") {
        isValidZip("") shouldBe false
    }

    test("isValidZip: 4 digits is invalid") {
        isValidZip("1234") shouldBe false
    }

    test("isValidZip: 6 digits is invalid") {
        isValidZip("123456") shouldBe false
    }

    test("isValidZip: 5 chars with letter is invalid") {
        isValidZip("1234A") shouldBe false
    }

    test("isValidZip: 5 chars with hyphen is invalid") {
        isValidZip("1234-") shouldBe false
    }

    test("isValidZip: '12345-6789' (ZIP+4) is invalid") {
        isValidZip("12345-6789") shouldBe false
    }

    // =========================================================================
    // validateManualEntryForm — form-level validation
    // =========================================================================
    // Requirements: 14.5, 14.6, 14.7

    test("validateManualEntryForm: all valid fields (AVS off) returns empty map") {
        val errors = validateManualEntryForm(
            cardNumber = "4111111111111111",
            expiry = "1225",
            cvv = "123",
            zip = "",
            avsEnabled = false
        )
        errors.shouldBeEmpty()
    }

    test("validateManualEntryForm: all valid fields (AVS on) returns empty map") {
        val errors = validateManualEntryForm(
            cardNumber = "4111111111111111",
            expiry = "1225",
            cvv = "123",
            zip = "12345",
            avsEnabled = true
        )
        errors.shouldBeEmpty()
    }

    test("validateManualEntryForm: empty card number produces cardNumber error") {
        val errors = validateManualEntryForm(
            cardNumber = "",
            expiry = "1225",
            cvv = "123",
            zip = "",
            avsEnabled = false
        )
        errors shouldContainKey "cardNumber"
        errors["cardNumber"] shouldBe "Card number is required"
    }

    test("validateManualEntryForm: blank card number produces cardNumber error") {
        val errors = validateManualEntryForm(
            cardNumber = "   ",
            expiry = "1225",
            cvv = "123",
            zip = "",
            avsEnabled = false
        )
        errors shouldContainKey "cardNumber"
    }

    test("validateManualEntryForm: invalid expiry produces expiry error") {
        val errors = validateManualEntryForm(
            cardNumber = "4111111111111111",
            expiry = "122",  // only 3 digits
            cvv = "123",
            zip = "",
            avsEnabled = false
        )
        errors shouldContainKey "expiry"
        errors["expiry"] shouldBe "Expiry must be MM/YY"
    }

    test("validateManualEntryForm: empty expiry produces expiry error") {
        val errors = validateManualEntryForm(
            cardNumber = "4111111111111111",
            expiry = "",
            cvv = "123",
            zip = "",
            avsEnabled = false
        )
        errors shouldContainKey "expiry"
    }

    test("validateManualEntryForm: empty CVV produces cvv error") {
        val errors = validateManualEntryForm(
            cardNumber = "4111111111111111",
            expiry = "1225",
            cvv = "",
            zip = "",
            avsEnabled = false
        )
        errors shouldContainKey "cvv"
        errors["cvv"] shouldBe "CVV is required"
    }

    test("validateManualEntryForm: blank CVV produces cvv error") {
        val errors = validateManualEntryForm(
            cardNumber = "4111111111111111",
            expiry = "1225",
            cvv = "  ",
            zip = "",
            avsEnabled = false
        )
        errors shouldContainKey "cvv"
    }

    test("validateManualEntryForm: invalid ZIP (4 digits) with AVS on produces zip error") {
        val errors = validateManualEntryForm(
            cardNumber = "4111111111111111",
            expiry = "1225",
            cvv = "123",
            zip = "1234",
            avsEnabled = true
        )
        errors shouldContainKey "zip"
        errors["zip"] shouldBe "Invalid ZIP"
    }

    test("validateManualEntryForm: invalid ZIP (6 digits) with AVS on produces zip error") {
        val errors = validateManualEntryForm(
            cardNumber = "4111111111111111",
            expiry = "1225",
            cvv = "123",
            zip = "123456",
            avsEnabled = true
        )
        errors shouldContainKey "zip"
    }

    test("validateManualEntryForm: invalid ZIP with AVS off does NOT produce zip error") {
        val errors = validateManualEntryForm(
            cardNumber = "4111111111111111",
            expiry = "1225",
            cvv = "123",
            zip = "1234",  // invalid ZIP but AVS is off
            avsEnabled = false
        )
        errors shouldNotContainKey "zip"
    }

    test("validateManualEntryForm: all fields empty produces errors for all required fields") {
        val errors = validateManualEntryForm(
            cardNumber = "",
            expiry = "",
            cvv = "",
            zip = "",
            avsEnabled = false
        )
        errors shouldContainKey "cardNumber"
        errors shouldContainKey "expiry"
        errors shouldContainKey "cvv"
    }

    test("validateManualEntryForm: all fields empty with AVS on produces errors including zip") {
        val errors = validateManualEntryForm(
            cardNumber = "",
            expiry = "",
            cvv = "",
            zip = "",
            avsEnabled = true
        )
        errors shouldContainKey "cardNumber"
        errors shouldContainKey "expiry"
        errors shouldContainKey "cvv"
        errors shouldContainKey "zip"
    }

    // =========================================================================
    // convertExpiryToMmYyyy — MMYY → MM.YYYY
    // =========================================================================

    test("convertExpiryToMmYyyy: '1225' → '12.2025'") {
        convertExpiryToMmYyyy("1225") shouldBe "12.2025"
    }

    test("convertExpiryToMmYyyy: '0130' → '01.2030'") {
        convertExpiryToMmYyyy("0130") shouldBe "01.2030"
    }

    test("convertExpiryToMmYyyy: '0699' → '06.2099'") {
        convertExpiryToMmYyyy("0699") shouldBe "06.2099"
    }

    test("convertExpiryToMmYyyy: '1200' → '12.2000'") {
        convertExpiryToMmYyyy("1200") shouldBe "12.2000"
    }

    // =========================================================================
    // shouldShowAvsFields — pure function
    // =========================================================================
    // Requirements: 7.2, 7.3

    test("shouldShowAvsFields: returns true when avsEnabled is true") {
        shouldShowAvsFields(true) shouldBe true
    }

    test("shouldShowAvsFields: returns false when avsEnabled is false") {
        shouldShowAvsFields(false) shouldBe false
    }
})
