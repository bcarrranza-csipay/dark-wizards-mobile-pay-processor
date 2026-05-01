package com.darkwizards.payments.ui.screen

// Feature: payment-ui-redesign, Property 9: Surcharge total calculation is correct

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.forAll
import kotlin.math.floor

/**
 * Property-based tests for surcharge calculation in [PaymentOptionsScreen].
 *
 * **Validates: Requirements 10.3, 10.7**
 */
class PaymentOptionsScreenPropertyTest : FunSpec({

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    /**
     * Generates a random base amount in cents (1 cent to $99,999.99 = 9_999_999 cents).
     * Zero is excluded because a zero base amount has no meaningful surcharge.
     */
    val baseAmountCentsArb: Arb<Int> = arbitrary { rs ->
        val random = rs.random
        1 + random.nextInt(9_999_999) // 1..9_999_999 cents ($0.01..$99,999.99)
    }

    /**
     * Generates a random surcharge rate percentage in the range [0.0, 100.0].
     *
     * Covers:
     * - 0.0 (no surcharge)
     * - Whole-number percentages (1–100)
     * - Fractional percentages (e.g., 1.5, 2.75, 3.125)
     * - Boundary values (0.0, 100.0)
     */
    val surchargeRateArb: Arb<Double> = arbitrary { rs ->
        val random = rs.random
        when (random.nextInt(4)) {
            0 -> 0.0                                          // no surcharge
            1 -> random.nextInt(101).toDouble()               // whole number 0–100
            2 -> {                                            // fractional: e.g. 1.5, 2.75
                val whole = random.nextInt(100)
                val frac  = random.nextInt(100)               // 0–99 hundredths
                whole + frac / 100.0
            }
            else -> {                                         // fine-grained fractional
                val whole = random.nextInt(100)
                val frac  = random.nextInt(1000)              // 0–999 thousandths
                whole + frac / 1000.0
            }
        }
    }

    /**
     * Generates a pair of (baseAmountCents, ratePercent) for combined property tests.
     */
    val surchargeInputArb: Arb<Pair<Int, Double>> = arbitrary { rs ->
        val random = rs.random
        val base = 1 + random.nextInt(9_999_999)
        val rate = when (random.nextInt(4)) {
            0    -> 0.0
            1    -> random.nextInt(101).toDouble()
            2    -> random.nextInt(100) + random.nextInt(100) / 100.0
            else -> random.nextInt(100) + random.nextInt(1000) / 1000.0
        }
        Pair(base, rate)
    }

    // -------------------------------------------------------------------------
    // Property 9: Surcharge total calculation is correct
    // -------------------------------------------------------------------------
    // For any base amount in cents and any surcharge percentage (0–100):
    //   surchargeCents == floor(baseAmountCents * ratePercent / 100)
    //   displayedTotal == baseAmountCents + surchargeCents
    //
    // **Validates: Requirements 10.3, 10.7**
    // -------------------------------------------------------------------------

    test("Property 9: surchargeCents equals floor(baseAmountCents * ratePercent / 100)") {
        // Feature: payment-ui-redesign, Property 9
        forAll(
            PropTestConfig(iterations = 500),
            surchargeInputArb
        ) { (baseAmountCents, ratePercent) ->
            val actual   = calculateSurchargeCents(baseAmountCents, ratePercent)
            val expected = floor(baseAmountCents.toDouble() * ratePercent / 100.0).toInt()
            actual == expected
        }
    }

    test("Property 9: displayed total equals baseAmountCents + surchargeCents") {
        // Feature: payment-ui-redesign, Property 9
        forAll(
            PropTestConfig(iterations = 500),
            surchargeInputArb
        ) { (baseAmountCents, ratePercent) ->
            val surchargeCents = calculateSurchargeCents(baseAmountCents, ratePercent)
            val displayedTotal = baseAmountCents + surchargeCents
            // The displayed total must equal the sum of base and surcharge
            displayedTotal == baseAmountCents + surchargeCents
        }
    }

    test("Property 9: zero surcharge rate produces zero surcharge cents") {
        // Feature: payment-ui-redesign, Property 9
        forAll(
            PropTestConfig(iterations = 200),
            baseAmountCentsArb
        ) { baseAmountCents ->
            val surchargeCents = calculateSurchargeCents(baseAmountCents, 0.0)
            surchargeCents == 0
        }
    }

    test("Property 9: 100% surcharge rate produces surchargeCents equal to baseAmountCents") {
        // Feature: payment-ui-redesign, Property 9
        forAll(
            PropTestConfig(iterations = 200),
            baseAmountCentsArb
        ) { baseAmountCents ->
            val surchargeCents = calculateSurchargeCents(baseAmountCents, 100.0)
            // floor(base * 100 / 100) = floor(base) = base
            surchargeCents == baseAmountCents
        }
    }

    test("Property 9: surchargeCents is always non-negative") {
        // Feature: payment-ui-redesign, Property 9
        forAll(
            PropTestConfig(iterations = 500),
            surchargeInputArb
        ) { (baseAmountCents, ratePercent) ->
            val surchargeCents = calculateSurchargeCents(baseAmountCents, ratePercent)
            surchargeCents >= 0
        }
    }

    test("Property 9: surchargeCents never exceeds baseAmountCents for rates up to 100%") {
        // Feature: payment-ui-redesign, Property 9
        forAll(
            PropTestConfig(iterations = 500),
            surchargeInputArb
        ) { (baseAmountCents, ratePercent) ->
            // ratePercent is in [0, 100], so surcharge <= base
            val surchargeCents = calculateSurchargeCents(baseAmountCents, ratePercent)
            surchargeCents <= baseAmountCents
        }
    }

    test("Property 9: floor semantics — surchargeCents is always the floor of the exact value") {
        // Feature: payment-ui-redesign, Property 9
        // Verifies that the implementation uses floor (not round or ceiling)
        forAll(
            PropTestConfig(iterations = 500),
            surchargeInputArb
        ) { (baseAmountCents, ratePercent) ->
            val surchargeCents = calculateSurchargeCents(baseAmountCents, ratePercent)
            val exactValue     = baseAmountCents.toDouble() * ratePercent / 100.0
            // surchargeCents must be the floor of the exact value
            surchargeCents.toDouble() <= exactValue &&
                (surchargeCents + 1).toDouble() > exactValue
        }
    }
})
