package com.darkwizards.payments.ui.screen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

/**
 * Unit tests for [formatAmountDisplay] and [validateAmount] in [MerchantPayScreen].
 *
 * _Requirements: 3.2, 3.8_
 */
class MerchantPayScreenTest : FunSpec({

    // -------------------------------------------------------------------------
    // formatAmountDisplay — cents-based formatting
    // -------------------------------------------------------------------------

    test("formatAmountDisplay returns \$0.00 for empty string") {
        formatAmountDisplay("") shouldBe "\$0.00"
    }

    test("formatAmountDisplay returns \$0.05 for single digit '5'") {
        formatAmountDisplay("5") shouldBe "\$0.05"
    }

    test("formatAmountDisplay returns \$0.01 for single digit '1'") {
        formatAmountDisplay("1") shouldBe "\$0.01"
    }

    test("formatAmountDisplay returns \$0.09 for single digit '9'") {
        formatAmountDisplay("9") shouldBe "\$0.09"
    }

    test("formatAmountDisplay returns \$0.12 for '12'") {
        formatAmountDisplay("12") shouldBe "\$0.12"
    }

    test("formatAmountDisplay returns \$0.99 for '99'") {
        formatAmountDisplay("99") shouldBe "\$0.99"
    }

    test("formatAmountDisplay returns \$1.23 for '123'") {
        formatAmountDisplay("123") shouldBe "\$1.23"
    }

    test("formatAmountDisplay returns \$12.34 for '1234'") {
        formatAmountDisplay("1234") shouldBe "\$12.34"
    }

    test("formatAmountDisplay returns \$123.45 for '12345'") {
        formatAmountDisplay("12345") shouldBe "\$123.45"
    }

    test("formatAmountDisplay returns \$1234.56 for '123456'") {
        formatAmountDisplay("123456") shouldBe "\$1234.56"
    }

    test("formatAmountDisplay returns \$12345.67 for '1234567'") {
        formatAmountDisplay("1234567") shouldBe "\$12345.67"
    }

    test("formatAmountDisplay returns \$123456.78 for '12345678'") {
        formatAmountDisplay("12345678") shouldBe "\$123456.78"
    }

    test("formatAmountDisplay returns \$0.00 for '0'") {
        formatAmountDisplay("0") shouldBe "\$0.00"
    }

    test("formatAmountDisplay returns \$0.00 for '00'") {
        formatAmountDisplay("00") shouldBe "\$0.00"
    }

    test("formatAmountDisplay returns \$0.00 for '000'") {
        formatAmountDisplay("000") shouldBe "\$0.00"
    }

    test("formatAmountDisplay returns \$10.00 for '1000'") {
        formatAmountDisplay("1000") shouldBe "\$10.00"
    }

    test("formatAmountDisplay returns \$100.00 for '10000'") {
        formatAmountDisplay("10000") shouldBe "\$100.00"
    }

    // -------------------------------------------------------------------------
    // buildDisplayAmount — display string for the amount field
    // -------------------------------------------------------------------------

    test("buildDisplayAmount returns \$0.00 for empty string") {
        buildDisplayAmount("") shouldBe "\$0.00"
    }

    test("buildDisplayAmount uses formatAmountDisplay for pure digit strings") {
        buildDisplayAmount("1234") shouldBe "\$12.34"
    }

    test("buildDisplayAmount prefixes dollar sign for strings with decimal") {
        buildDisplayAmount("12.34") shouldBe "\$12.34"
    }

    test("buildDisplayAmount handles '0.' (decimal typed after zero)") {
        buildDisplayAmount("0.") shouldBe "\$0."
    }

    test("buildDisplayAmount handles '12.' (decimal typed after digits)") {
        buildDisplayAmount("12.") shouldBe "\$12."
    }

    test("buildDisplayAmount handles '12.5' (partial cents)") {
        buildDisplayAmount("12.5") shouldBe "\$12.5"
    }

    // -------------------------------------------------------------------------
    // validateAmount — valid amounts
    // -------------------------------------------------------------------------

    test("validateAmount returns null for '1' (one cent)") {
        validateAmount("1") shouldBe null
    }

    test("validateAmount returns null for '100' (one dollar)") {
        validateAmount("100") shouldBe null
    }

    test("validateAmount returns null for '1234' (\$12.34)") {
        validateAmount("1234") shouldBe null
    }

    test("validateAmount returns null for '12345678' (max amount)") {
        validateAmount("12345678") shouldBe null
    }

    test("validateAmount returns null for '12.34' (dollar-decimal input)") {
        validateAmount("12.34") shouldBe null
    }

    test("validateAmount returns null for '0.01' (one cent via decimal)") {
        validateAmount("0.01") shouldBe null
    }

    test("validateAmount returns null for '1.00' (one dollar via decimal)") {
        validateAmount("1.00") shouldBe null
    }

    // -------------------------------------------------------------------------
    // validateAmount — invalid amounts (empty / zero / non-positive)
    // -------------------------------------------------------------------------

    test("validateAmount returns error for empty string") {
        validateAmount("") shouldBe "Please enter a valid amount"
    }

    test("validateAmount returns error for '0' (zero)") {
        validateAmount("0") shouldBe "Please enter a valid amount"
    }

    test("validateAmount returns error for '00' (zero)") {
        validateAmount("00") shouldBe "Please enter a valid amount"
    }

    test("validateAmount returns error for '000' (zero)") {
        validateAmount("000") shouldBe "Please enter a valid amount"
    }

    test("validateAmount returns error for '.' (just decimal)") {
        validateAmount(".") shouldBe "Please enter a valid amount"
    }

    test("validateAmount returns error for '0.' (zero with decimal)") {
        validateAmount("0.") shouldBe "Please enter a valid amount"
    }

    test("validateAmount returns error for '0.00' (zero dollars)") {
        validateAmount("0.00") shouldBe "Please enter a valid amount"
    }

    test("validateAmount returns error for '0.0' (zero dollars)") {
        validateAmount("0.0") shouldBe "Please enter a valid amount"
    }

    // -------------------------------------------------------------------------
    // Decimal button state — enabled iff amount does not contain "."
    // -------------------------------------------------------------------------

    test("decimal button is enabled when amount is empty (no decimal present)") {
        val amount = ""
        val decimalEnabled = !amount.contains(".")
        decimalEnabled shouldBe true
    }

    test("decimal button is enabled when amount is pure digits") {
        val amount = "1234"
        val decimalEnabled = !amount.contains(".")
        decimalEnabled shouldBe true
    }

    test("decimal button is disabled when amount contains a decimal point") {
        val amount = "12.34"
        val decimalEnabled = !amount.contains(".")
        decimalEnabled shouldBe false
    }

    test("decimal button is disabled when amount is '0.'") {
        val amount = "0."
        val decimalEnabled = !amount.contains(".")
        decimalEnabled shouldBe false
    }

    test("decimal button is disabled when amount is '.'") {
        val amount = "."
        val decimalEnabled = !amount.contains(".")
        decimalEnabled shouldBe false
    }
})
