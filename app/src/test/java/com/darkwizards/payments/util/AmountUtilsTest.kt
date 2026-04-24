package com.darkwizards.payments.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse

class AmountUtilsTest : FunSpec({

    test("dollarsToCents converts 25.30 to 2530") {
        AmountUtils.dollarsToCents("25.30") shouldBe "2530"
    }

    test("dollarsToCents converts whole dollar amount 100 to 10000") {
        AmountUtils.dollarsToCents("100") shouldBe "10000"
    }

    test("dollarsToCents converts 0.01 to 1") {
        AmountUtils.dollarsToCents("0.01") shouldBe "1"
    }

    test("dollarsToCents converts 0.10 to 10") {
        AmountUtils.dollarsToCents("0.10") shouldBe "10"
    }

    test("dollarsToCents converts 999999.99 to 99999999") {
        AmountUtils.dollarsToCents("999999.99") shouldBe "99999999"
    }

    test("centsToDisplay converts 2530 to dollar display") {
        AmountUtils.centsToDisplay("2530") shouldBe "$25.30"
    }

    test("centsToDisplay converts 1 to dollar display") {
        AmountUtils.centsToDisplay("1") shouldBe "$0.01"
    }

    test("centsToDisplay converts 10000 to dollar display") {
        AmountUtils.centsToDisplay("10000") shouldBe "$100.00"
    }

    test("centsToDisplay converts 10 to dollar display") {
        AmountUtils.centsToDisplay("10") shouldBe "$0.10"
    }

    test("isValidDollarAmount accepts 25.30") {
        AmountUtils.isValidDollarAmount("25.30").shouldBeTrue()
    }

    test("isValidDollarAmount accepts 100") {
        AmountUtils.isValidDollarAmount("100").shouldBeTrue()
    }

    test("isValidDollarAmount accepts 0.01") {
        AmountUtils.isValidDollarAmount("0.01").shouldBeTrue()
    }

    test("isValidDollarAmount rejects empty string") {
        AmountUtils.isValidDollarAmount("").shouldBeFalse()
    }

    test("isValidDollarAmount rejects non-numeric input") {
        AmountUtils.isValidDollarAmount("abc").shouldBeFalse()
    }

    test("isValidDollarAmount rejects negative amount") {
        AmountUtils.isValidDollarAmount("-5").shouldBeFalse()
    }

    test("isValidDollarAmount rejects more than 2 decimal places") {
        AmountUtils.isValidDollarAmount("1.234").shouldBeFalse()
    }

    test("isValidDollarAmount rejects zero") {
        AmountUtils.isValidDollarAmount("0").shouldBeFalse()
    }

    test("isValidDollarAmount rejects 0.00") {
        AmountUtils.isValidDollarAmount("0.00").shouldBeFalse()
    }
})
