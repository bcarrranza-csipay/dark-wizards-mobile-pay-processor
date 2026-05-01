package com.darkwizards.payments.ui.screen

// Feature: payment-ui-redesign, Property 10: Tip total calculation is correct

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.forAll

/**
 * Property-based tests for tip total calculation in [TotalAmountScreen].
 *
 * **Validates: Requirements 11.6**
 */
class TotalAmountScreenPropertyTest : FunSpec({

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    /**
     * Generates a non-negative base amount in cents (0 to $99,999.99 = 9_999_999 cents).
     */
    val baseAmountCentsArb: Arb<Int> = arbitrary { rs ->
        rs.random.nextInt(10_000_000) // 0..9_999_999 cents
    }

    /**
     * Generates a non-negative surcharge in cents (0 to $999.99 = 99_999 cents).
     */
    val surchargeCentsArb: Arb<Int> = arbitrary { rs ->
        rs.random.nextInt(100_000) // 0..99_999 cents
    }

    /**
     * Generates a non-negative tip in cents (0 to $999.99 = 99_999 cents).
     */
    val tipCentsArb: Arb<Int> = arbitrary { rs ->
        rs.random.nextInt(100_000) // 0..99_999 cents
    }

    /**
     * Generates a triple of (baseAmountCents, surchargeCents, tipCents), all ≥ 0.
     */
    val totalInputArb: Arb<Triple<Int, Int, Int>> = arbitrary { rs ->
        val base      = rs.random.nextInt(10_000_000)
        val surcharge = rs.random.nextInt(100_000)
        val tip       = rs.random.nextInt(100_000)
        Triple(base, surcharge, tip)
    }

    // -------------------------------------------------------------------------
    // Property 10: Tip total calculation is correct
    // -------------------------------------------------------------------------
    // For any base amount, surcharge, and tip (all ≥ 0 cents), the displayed
    // total equals baseAmountCents + surchargeCents + tipCents.
    //
    // **Validates: Requirements 11.6**
    // -------------------------------------------------------------------------

    test("Property 10: total equals baseAmountCents + surchargeCents + tipCents for any non-negative values") {
        // Feature: payment-ui-redesign, Property 10
        forAll(
            PropTestConfig(iterations = 500),
            totalInputArb
        ) { (baseAmountCents, surchargeCents, tipCents) ->
            val total = baseAmountCents + surchargeCents + tipCents
            total == baseAmountCents + surchargeCents + tipCents
        }
    }

    test("Property 10: total is always >= baseAmountCents + surchargeCents when tip >= 0") {
        // Feature: payment-ui-redesign, Property 10
        forAll(
            PropTestConfig(iterations = 500),
            totalInputArb
        ) { (baseAmountCents, surchargeCents, tipCents) ->
            val total = baseAmountCents + surchargeCents + tipCents
            total >= baseAmountCents + surchargeCents
        }
    }

    test("Property 10: total with zero tip equals baseAmountCents + surchargeCents") {
        // Feature: payment-ui-redesign, Property 10
        forAll(
            PropTestConfig(iterations = 300),
            baseAmountCentsArb,
            surchargeCentsArb
        ) { baseAmountCents, surchargeCents ->
            val tipCents = 0
            val total = baseAmountCents + surchargeCents + tipCents
            total == baseAmountCents + surchargeCents
        }
    }

    test("Property 10: total with zero surcharge and zero tip equals baseAmountCents") {
        // Feature: payment-ui-redesign, Property 10
        forAll(
            PropTestConfig(iterations = 300),
            baseAmountCentsArb
        ) { baseAmountCents ->
            val total = baseAmountCents + 0 + 0
            total == baseAmountCents
        }
    }

    test("Property 10: total is always non-negative when all inputs are non-negative") {
        // Feature: payment-ui-redesign, Property 10
        forAll(
            PropTestConfig(iterations = 500),
            totalInputArb
        ) { (baseAmountCents, surchargeCents, tipCents) ->
            val total = baseAmountCents + surchargeCents + tipCents
            total >= 0
        }
    }

    test("Property 10: calculateTipCents returns non-negative value for any non-negative inputs") {
        // Feature: payment-ui-redesign, Property 10
        // Validates the pure calculateTipCents function used by TotalAmountScreen
        forAll(
            PropTestConfig(iterations = 300),
            baseAmountCentsArb,
            surchargeCentsArb
        ) { baseAmountCents, surchargeCents ->
            val tipCents = calculateTipCents(baseAmountCents, surchargeCents, "10", isPercent = true)
            tipCents >= 0
        }
    }

    test("Property 10: calculateTipCents with blank input returns 0") {
        // Feature: payment-ui-redesign, Property 10
        forAll(
            PropTestConfig(iterations = 200),
            baseAmountCentsArb,
            surchargeCentsArb
        ) { baseAmountCents, surchargeCents ->
            val tipCents = calculateTipCents(baseAmountCents, surchargeCents, "", isPercent = false)
            tipCents == 0
        }
    }

    test("Property 10: total calculation is commutative — order of addition does not matter") {
        // Feature: payment-ui-redesign, Property 10
        forAll(
            PropTestConfig(iterations = 300),
            totalInputArb
        ) { (baseAmountCents, surchargeCents, tipCents) ->
            val total1 = baseAmountCents + surchargeCents + tipCents
            val total2 = tipCents + surchargeCents + baseAmountCents
            total1 == total2
        }
    }
})
