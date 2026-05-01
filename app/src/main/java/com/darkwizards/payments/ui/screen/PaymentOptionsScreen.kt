package com.darkwizards.payments.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.darkwizards.payments.ui.viewmodel.SettingsViewModel
import kotlin.math.floor

/**
 * Calculates the surcharge amount in cents using floor division.
 *
 * Formula: `floor(baseAmountCents * ratePercent / 100)`
 *
 * @param baseAmountCents  The base transaction amount in cents (must be ≥ 0).
 * @param ratePercent      The surcharge rate as a percentage (0–100).
 * @return The surcharge amount in cents, rounded down to the nearest cent.
 */
fun calculateSurchargeCents(baseAmountCents: Int, ratePercent: Double): Int {
    if (baseAmountCents <= 0 || ratePercent <= 0.0) return 0
    return floor(baseAmountCents.toDouble() * ratePercent / 100.0).toInt()
}

/**
 * First customer-facing screen — Debit / Credit card type selection.
 *
 * Displays the base transaction amount prominently and two card type buttons.
 * No back arrow — this is the handover boundary between merchant and customer flows
 * (Requirement 10.4).
 *
 * When Credit is selected and a credit surcharge is configured, a surcharge disclaimer
 * is shown before the customer taps the button (Requirement 10.3).
 *
 * On card type selection, navigates to [Screen.TotalAmount] passing
 * `baseAmountCents`, `cardType`, and `surchargeCents` (Requirement 10.5).
 *
 * @param baseAmountCents    Merchant-entered amount in cents (from nav argument).
 * @param navController      Navigation controller used to navigate to TotalAmount.
 * @param settingsViewModel  Provides surcharge configuration via [SettingsViewModel.state].
 */
@Composable
fun PaymentOptionsScreen(
    baseAmountCents: Int,
    navController: NavController,
    settingsViewModel: SettingsViewModel
) {
    val tokens = LocalColorTokens.current
    val settingsState by settingsViewModel.state.collectAsState()

    // Parse surcharge percentages from settings (default to 0.0 if blank/invalid)
    val creditSurchargePercent = settingsState.creditSurchargePercent.toDoubleOrNull() ?: 0.0
    val debitSurchargePercent  = settingsState.debitSurchargePercent.toDoubleOrNull()  ?: 0.0

    // Pre-calculate credit surcharge for the disclaimer preview
    val creditSurchargeCents = calculateSurchargeCents(baseAmountCents, creditSurchargePercent)

    // Format the base amount as "$X.XX"
    val formattedBase = formatCentsAsDisplay(baseAmountCents)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.backgroundColor)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ── Base amount display ───────────────────────────────────────────────

        Text(
            text = formattedBase,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select payment type",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // ── Debit button (Button2Color) ───────────────────────────────────────

        Button(
            onClick = {
                val surchargeCents = calculateSurchargeCents(baseAmountCents, debitSurchargePercent)
                navController.navigate(
                    Screen.TotalAmount.createRoute(
                        baseAmountCents = baseAmountCents,
                        cardType        = "debit",
                        surchargeCents  = surchargeCents
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.button2Color,
                contentColor   = Color.White
            )
        ) {
            Text(
                text = "Debit",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Credit button (Button1Color) ──────────────────────────────────────

        Button(
            onClick = {
                val surchargeCents = calculateSurchargeCents(baseAmountCents, creditSurchargePercent)
                navController.navigate(
                    Screen.TotalAmount.createRoute(
                        baseAmountCents = baseAmountCents,
                        cardType        = "credit",
                        surchargeCents  = surchargeCents
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.button1Color,
                contentColor   = Color.White
            )
        ) {
            Text(
                text = "Credit",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // ── Credit surcharge disclaimer ───────────────────────────────────────
        // Shown when a credit surcharge is configured (> 0%), as a preview
        // before the customer taps the Credit button (Requirement 10.3).

        if (creditSurchargePercent > 0.0) {
            Spacer(modifier = Modifier.height(20.dp))

            val surchargeDisplay = formatCentsAsDisplay(creditSurchargeCents)
            val percentDisplay   = formatPercentDisplay(creditSurchargePercent)

            Text(
                text = "$percentDisplay surcharge applies (+$surchargeDisplay)",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Yellow.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

/**
 * Formats an amount in cents as a display string (e.g., 2500 → "$25.00").
 */
private fun formatCentsAsDisplay(cents: Int): String {
    val dollars = cents / 100
    val remainingCents = cents % 100
    return "\$$dollars.%02d".format(remainingCents)
}

/**
 * Formats a surcharge percentage for display (e.g., 3.0 → "3%", 2.5 → "2.5%").
 * Strips trailing ".0" for whole-number percentages.
 */
private fun formatPercentDisplay(percent: Double): String {
    return if (percent == percent.toLong().toDouble()) {
        "${percent.toLong()}%"
    } else {
        "$percent%"
    }
}
