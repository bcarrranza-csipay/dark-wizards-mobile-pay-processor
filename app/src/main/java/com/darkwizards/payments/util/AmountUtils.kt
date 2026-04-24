package com.darkwizards.payments.util

import java.math.BigDecimal
import java.math.RoundingMode

object AmountUtils {

    /** Convert dollar string (e.g., "25.30") to cents string (e.g., "2530") */
    fun dollarsToCents(dollars: String): String {
        val bd = BigDecimal(dollars)
        return bd.multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toPlainString()
    }

    /** Convert cents string (e.g., "2530") to display string (e.g., "$25.30") */
    fun centsToDisplay(cents: String): String {
        val bd = BigDecimal(cents)
        val dollars = bd.divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        return "$${dollars.toPlainString()}"
    }

    /** Validate dollar amount input: positive, max 2 decimal places */
    fun isValidDollarAmount(input: String): Boolean {
        if (input.isBlank()) return false
        val bd = try {
            BigDecimal(input)
        } catch (_: NumberFormatException) {
            return false
        }
        if (bd.signum() <= 0) return false
        if (bd.scale() > 2) return false
        return true
    }
}
