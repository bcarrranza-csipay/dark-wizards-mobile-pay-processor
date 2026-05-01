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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.darkwizards.payments.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

// ── Pure calculation function (extracted for property testing) ────────────────

/**
 * Calculates the tip amount in cents from the tip input string and mode.
 *
 * When [isPercent] is `true`, the [tipInput] is interpreted as a percentage of
 * `baseAmountCents + surchargeCents`. When `false`, it is interpreted as a dollar
 * amount and converted to cents.
 *
 * Returns 0 for any blank, unparseable, or non-positive input.
 *
 * @param baseAmountCents  Base transaction amount in cents (≥ 0).
 * @param surchargeCents   Surcharge amount in cents (≥ 0).
 * @param tipInput         The raw tip input string (digits and optional decimal point).
 * @param isPercent        `true` if [tipInput] is a percentage; `false` if it is a dollar amount.
 * @return The tip amount in cents (≥ 0).
 */
fun calculateTipCents(
    baseAmountCents: Int,
    surchargeCents: Int,
    tipInput: String,
    isPercent: Boolean
): Int {
    if (tipInput.isBlank()) return 0
    val value = tipInput.toDoubleOrNull() ?: return 0
    if (value <= 0.0) return 0
    return if (isPercent) {
        val subtotal = baseAmountCents + surchargeCents
        ((subtotal.toDouble() * value / 100.0).roundToInt()).coerceAtLeast(0)
    } else {
        (value * 100.0).roundToInt().coerceAtLeast(0)
    }
}

/**
 * Formats an amount in cents as a display string (e.g., 2575 → "$25.75").
 * Internal helper — mirrors the private helper in [PaymentOptionsScreen].
 */
internal fun formatCentsDisplay(cents: Int): String {
    val dollars = cents / 100
    val remainingCents = cents % 100
    return "\$$dollars.%02d".format(remainingCents)
}

// ── Screen composable ─────────────────────────────────────────────────────────

/**
 * Customer-facing tip selection and total confirmation screen.
 *
 * Displays the total (base + surcharge) prominently, with an optional surcharge
 * breakdown line. When tip is enabled via [SettingsViewModel]:
 * - If presets are configured: shows one button per preset percentage + "Custom" button.
 * - If no presets and tip enabled: shows a numeric tip entry pad with $/% toggle.
 *
 * The displayed total updates in real-time as the customer selects or enters a tip:
 *   `total = baseAmountCents + surchargeCents + tipCents`
 *
 * A "Continue" pill button navigates to [Screen.PaymentType] passing all four
 * amount components.
 *
 * Back arrow navigates to [Screen.PaymentOptions] (Requirement 11.7).
 *
 * @param baseAmountCents    Base transaction amount in cents (from nav argument).
 * @param cardType           "debit" or "credit" (from nav argument).
 * @param surchargeCents     Surcharge amount in cents (from nav argument).
 * @param navController      Navigation controller.
 * @param settingsViewModel  Provides tip configuration via [SettingsViewModel.state].
 */
@Composable
fun TotalAmountScreen(
    baseAmountCents: Int,
    cardType: String,
    surchargeCents: Int,
    navController: NavController,
    settingsViewModel: SettingsViewModel
) {
    val tokens = LocalColorTokens.current
    val settingsState by settingsViewModel.state.collectAsState()

    // ── Tip state ─────────────────────────────────────────────────────────────

    // tipCents: the currently selected/entered tip in cents
    var tipCents by remember { mutableStateOf(0) }

    // For the custom tip entry pad (no presets): raw digit string and $/% toggle
    var customTipInput by remember { mutableStateOf("") }
    var isPercentMode by remember { mutableStateOf(false) }

    // Whether the customer has tapped "Custom" when presets are shown
    var showCustomPad by remember { mutableStateOf(false) }

    // Derive tip configuration from settings
    val tipEnabled = settingsState.tipEnabled
    val tipPresets = settingsState.tipPresets.filter { it.isNotBlank() }
    val hasPresets = tipPresets.isNotEmpty()

    // Recompute tipCents whenever customTipInput or isPercentMode changes
    // (preset selection sets tipCents directly)
    val computedTipCents = if (showCustomPad || (!hasPresets && tipEnabled)) {
        calculateTipCents(baseAmountCents, surchargeCents, customTipInput, isPercentMode)
    } else {
        tipCents
    }

    // ── Totals ────────────────────────────────────────────────────────────────

    val totalCents = baseAmountCents + surchargeCents + computedTipCents
    val formattedTotal = formatCentsDisplay(totalCents)
    val formattedSurcharge = formatCentsDisplay(surchargeCents)

    // ── Layout ────────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back arrow
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Total display ─────────────────────────────────────────────────────

        Text(
            text = "Total",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = formattedTotal,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        // Surcharge breakdown line (only when surcharge > 0)
        if (surchargeCents > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "incl. $formattedSurcharge surcharge",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Yellow.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Tip section (only when tip is enabled) ────────────────────────────

        if (tipEnabled) {
            Text(
                text = "Add a tip?",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (hasPresets && !showCustomPad) {
                // ── Preset tip buttons ────────────────────────────────────────

                // Show one button per preset percentage
                tipPresets.forEach { preset ->
                    val presetValue = preset.toDoubleOrNull() ?: 0.0
                    val presetCents = calculateTipCents(baseAmountCents, surchargeCents, preset, isPercent = true)
                    val isSelected = tipCents == presetCents && !showCustomPad

                    Button(
                        onClick = {
                            tipCents = presetCents
                            showCustomPad = false
                            customTipInput = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) tokens.button1Color else tokens.button2Color,
                            contentColor = Color.White
                        )
                    ) {
                        val label = if (presetValue == presetValue.toLong().toDouble()) {
                            "${presetValue.toLong()}%"
                        } else {
                            "$presetValue%"
                        }
                        Text(
                            text = label,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // "Custom" button — always shown when presets exist
                Button(
                    onClick = {
                        showCustomPad = true
                        customTipInput = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.button2Color,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Custom",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // "No tip" option
                Button(
                    onClick = {
                        tipCents = 0
                        showCustomPad = false
                        customTipInput = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    Text(
                        text = "No tip",
                        fontSize = 16.sp
                    )
                }

            } else {
                // ── Custom tip entry pad ──────────────────────────────────────
                // Shown when: no presets configured, OR customer tapped "Custom"

                // $/% toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$",
                        color = if (!isPercentMode) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = isPercentMode,
                        onCheckedChange = {
                            isPercentMode = it
                            customTipInput = ""
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = tokens.button1Color,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = tokens.button2Color
                        )
                    )
                    Text(
                        text = "%",
                        color = if (isPercentMode) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tip input display
                val tipDisplayText = if (customTipInput.isEmpty()) {
                    if (isPercentMode) "0%" else "$0.00"
                } else {
                    if (isPercentMode) "$customTipInput%" else "\$$customTipInput"
                }

                Text(
                    text = tipDisplayText,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = tokens.button1Color,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Numeric keypad: digits 0–9 + backspace
                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "⌫")
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
                                if (key.isEmpty()) {
                                    // Empty placeholder cell
                                    Spacer(modifier = Modifier.weight(1f))
                                } else {
                                    Button(
                                        onClick = {
                                            when (key) {
                                                "⌫" -> {
                                                    if (customTipInput.isNotEmpty()) {
                                                        customTipInput = customTipInput.dropLast(1)
                                                    }
                                                }
                                                else -> {
                                                    // Cap at 6 digits to prevent overflow
                                                    if (customTipInput.replace(".", "").length < 6) {
                                                        customTipInput = "$customTipInput$key"
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = tokens.numberPadColor,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(
                                            text = key,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // "Back to presets" link (only when showing custom pad after tapping "Custom")
                if (hasPresets) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            showCustomPad = false
                            customTipInput = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White.copy(alpha = 0.7f)
                        )
                    ) {
                        Text(text = "← Back to presets", fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Continue pill button ──────────────────────────────────────────────

        Button(
            onClick = {
                navController.navigate(
                    Screen.PaymentType.createRoute(
                        baseAmountCents = baseAmountCents,
                        cardType        = cardType,
                        surchargeCents  = surchargeCents,
                        tipCents        = computedTipCents
                    )
                )
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
                text = "Continue",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
