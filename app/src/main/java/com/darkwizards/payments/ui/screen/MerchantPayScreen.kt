package com.darkwizards.payments.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.darkwizards.payments.ui.navigation.Screen
import com.darkwizards.payments.ui.theme.LocalColorTokens
import kotlin.math.roundToInt

/**
 * Formats a raw digit string (representing cents, right-to-left) into a currency display string.
 *
 * The digits string contains only digit characters — no decimal point. The decimal in the
 * display is purely visual, derived from the position of the last two digits (cents).
 *
 * Examples:
 *   ""     → "$0.00"
 *   "5"    → "$0.05"
 *   "12"   → "$0.12"
 *   "123"  → "$1.23"
 *   "1234" → "$12.34"
 *
 * @param digits A string containing only digit characters (no decimal point).
 * @return A formatted currency string like "$X.XX".
 */
fun formatAmountDisplay(digits: String): String {
    if (digits.isEmpty()) return "\$0.00"

    // Pad to at least 3 characters so we always have a dollars and cents portion
    val padded = digits.padStart(3, '0')

    val dollars = padded.dropLast(2).trimStart('0').ifEmpty { "0" }
    val cents = padded.takeLast(2)

    return "\$$dollars.$cents"
}

/**
 * Merchant-facing amount entry screen with numeric keypad.
 *
 * Displays "Enter Amount" header, a real-time currency display, a 3×4 numeric keypad
 * (digits 0–9, decimal, backspace), and a "Proceed to Payment" pill button.
 *
 * The amount state tracks the user's typed string, which may contain a decimal point.
 * The decimal button is disabled when the amount string already contains ".".
 * The display shows the amount formatted as a currency string.
 *
 * On valid submission, converts the dollar string to cents and navigates to
 * [Screen.PaymentOptions].
 *
 * @param navController Navigation controller used to navigate to PaymentOptions.
 */
@Composable
fun MerchantPayScreen(
    navController: NavController
) {
    val tokens = LocalColorTokens.current

    // The amount string tracks the user's typed input.
    // It may contain digit characters and at most one decimal point.
    // Capped at 8 significant digits to prevent overflow (max ~$999,999.99).
    var amount by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // The decimal button is disabled when the amount already contains "."
    val decimalButtonEnabled = !amount.contains(".")

    // Build the display string: if amount is empty or has no decimal, use formatAmountDisplay
    // treating the digits as cents. If amount contains a decimal, display it directly as dollars.
    val displayAmount = buildDisplayAmount(amount)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.backgroundColor)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Header
        Text(
            text = "Enter Amount",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Amount display field with Button1Color border
        Text(
            text = displayAmount,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 2.dp,
                    color = tokens.button1Color,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(vertical = 16.dp, horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Numeric keypad — 3 columns × 4 rows: [1,2,3], [4,5,6], [7,8,9], [.,0,⌫]
        val keypadRows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "⌫")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            keypadRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { key ->
                        val isDecimalKey = key == "."
                        val isEnabled = if (isDecimalKey) decimalButtonEnabled else true

                        Button(
                            onClick = {
                                errorMessage = null
                                when (key) {
                                    "⌫" -> {
                                        if (amount.isNotEmpty()) {
                                            amount = amount.dropLast(1)
                                        }
                                    }
                                    "." -> {
                                        // Only add decimal if not already present
                                        if (!amount.contains(".")) {
                                            amount = if (amount.isEmpty()) "0." else "$amount."
                                        }
                                    }
                                    else -> {
                                        // Cap at 8 digits (excluding decimal point) to prevent overflow
                                        val digitCount = amount.replace(".", "").length
                                        if (digitCount < 8) {
                                            amount = "$amount$key"
                                        }
                                    }
                                }
                            },
                            enabled = isEnabled,
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.numberPadColor,
                                contentColor = Color.White,
                                disabledContainerColor = tokens.numberPadColor.copy(alpha = 0.4f),
                                disabledContentColor = Color.White.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = key,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // "Proceed to Payment" pill button
        Button(
            onClick = {
                val validationError = validateAmount(amount)
                if (validationError != null) {
                    errorMessage = validationError
                } else {
                    // Strip the "$" prefix from the display and convert to cents
                    val dollarString = displayAmount.removePrefix("\$")
                    val baseAmountCents = (dollarString.toDouble() * 100).roundToInt()
                    navController.navigate(Screen.PaymentOptions.createRoute(baseAmountCents))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.button1Color,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Proceed to Payment",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Inline error message
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Builds the display string for the amount field.
 *
 * - If the amount is empty, returns "$0.00".
 * - If the amount contains a decimal point (user typed it), formats as "$X.XX" directly.
 * - If the amount is pure digits (no decimal), uses [formatAmountDisplay] to treat them as cents.
 *
 * @param amount The current amount string (may contain digits and at most one decimal point).
 * @return A formatted currency display string.
 */
internal fun buildDisplayAmount(amount: String): String {
    return when {
        amount.isEmpty() -> "\$0.00"
        amount.contains(".") -> "\$$amount"
        else -> formatAmountDisplay(amount)
    }
}

/**
 * Validates the amount string for submission.
 *
 * An amount is invalid if:
 * - amount is empty (display shows "$0.00")
 * - the parsed dollar value is 0.0 or negative
 * - the amount is not parseable as a positive Double
 *
 * @param amount The current amount string (may contain digits and at most one decimal point).
 * @return An error message string if invalid, or null if valid.
 */
internal fun validateAmount(amount: String): String? {
    if (amount.isEmpty() || amount == "." || amount == "0." || amount == "0") {
        return "Please enter a valid amount"
    }

    val dollarString = buildDisplayAmount(amount).removePrefix("\$")
    val dollarValue = dollarString.toDoubleOrNull()
        ?: return "Please enter a valid amount"

    if (dollarValue <= 0.0) {
        return "Please enter a valid amount"
    }

    return null
}
