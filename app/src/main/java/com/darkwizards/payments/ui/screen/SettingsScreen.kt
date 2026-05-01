package com.darkwizards.payments.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.darkwizards.payments.ui.theme.ColorTokenRepository
import com.darkwizards.payments.ui.theme.LocalColorTokens
import com.darkwizards.payments.ui.viewmodel.PaymentMode
import com.darkwizards.payments.ui.viewmodel.SettingsViewModel

// ── Token display names ───────────────────────────────────────────────────────

private val TOKEN_DISPLAY_NAMES = mapOf(
    ColorTokenRepository.KEY_BASE       to "Base Color",
    ColorTokenRepository.KEY_BUTTON1    to "Button 1 Color",
    ColorTokenRepository.KEY_BUTTON2    to "Button 2 Color",
    ColorTokenRepository.KEY_NUMBERPAD  to "Number Pad Color",
    ColorTokenRepository.KEY_SPINNER    to "Spinner Color",
    ColorTokenRepository.KEY_TAPICON    to "Tap Icon Color",
    ColorTokenRepository.KEY_HOMEBAR    to "Home Bar Color",
    ColorTokenRepository.KEY_BACKGROUND to "Background Color"
)

// ── SettingsScreen ────────────────────────────────────────────────────────────

/**
 * Merchant-facing settings screen.
 *
 * Scrollable screen with five sections:
 *  1. Surcharges — per-card-type percentage inputs
 *  2. Tip Enablement — toggle + up to three preset percentage fields
 *  3. Ask for Billing Address (AVS) — toggle
 *  4. Branding Colors — hex input per color token + color swatch + inline errors
 *  5. Payment Mode — radio button group
 *
 * @param settingsViewModel  Provides and persists all settings state.
 */
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel
) {
    val state by settingsViewModel.state.collectAsState()
    val tokens = LocalColorTokens.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {

        // ── Screen title ──────────────────────────────────────────────────────

        Text(
            text  = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ── Section 1: Surcharges ─────────────────────────────────────────────

        SectionHeader(title = "Surcharges")

        OutlinedTextField(
            value         = state.creditSurchargePercent,
            onValueChange = { value ->
                settingsViewModel.updateSurcharge("credit", value)
            },
            label         = { Text("Credit Surcharge (%)") },
            singleLine    = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction    = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value         = state.debitSurchargePercent,
            onValueChange = { value ->
                settingsViewModel.updateSurcharge("debit", value)
            },
            label         = { Text("Debit Surcharge (%)") },
            singleLine    = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction    = ImeAction.Done
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        SectionDivider()

        // ── Section 2: Tip Enablement ─────────────────────────────────────────

        SectionHeader(title = "Tip Enablement")

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text  = "Enable Tips",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Switch(
                checked         = state.tipEnabled,
                onCheckedChange = { enabled -> settingsViewModel.setTipEnabled(enabled) }
            )
        }

        if (state.tipEnabled) {
            Text(
                text     = "Preset Tip Percentages",
                style    = MaterialTheme.typography.bodyMedium,
                color    = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            val presets = state.tipPresets.let { list ->
                // Ensure we always have exactly 3 entries (pad with empty strings if needed)
                List(3) { index -> list.getOrElse(index) { "" } }
            }

            presets.forEachIndexed { index, preset ->
                OutlinedTextField(
                    value         = preset,
                    onValueChange = { value ->
                        settingsViewModel.updateTipPreset(index, value)
                    },
                    label         = { Text("Preset ${index + 1} (%)") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction    = if (index < 2) ImeAction.Next else ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        SectionDivider()

        // ── Section 3: Ask for Billing Address (AVS) ──────────────────────────

        SectionHeader(title = "Ask for Billing Address (AVS)")

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text  = "Request Billing Address",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Switch(
                checked         = state.avsEnabled,
                onCheckedChange = { enabled -> settingsViewModel.setAvsEnabled(enabled) }
            )
        }

        SectionDivider()

        // ── Section 4: Branding Colors ────────────────────────────────────────

        SectionHeader(title = "Branding Colors")

        SettingsViewModel.TOKEN_KEYS.forEach { tokenKey ->
            val displayName  = TOKEN_DISPLAY_NAMES[tokenKey] ?: tokenKey
            val hexInput     = state.hexInputs[tokenKey].orEmpty()
            val hexError     = state.hexErrors[tokenKey]
            val currentColor = getTokenColor(tokenKey, state.colorTokens)

            ColorTokenRow(
                displayName  = displayName,
                tokenKey     = tokenKey,
                hexInput     = hexInput,
                hexError     = hexError,
                currentColor = currentColor,
                onValueChange = { hex ->
                    settingsViewModel.updateHexInput(tokenKey, hex)
                },
                onSave = {
                    settingsViewModel.saveHexToken(tokenKey)
                }
            )
        }

        OutlinedButton(
            onClick  = { settingsViewModel.resetBrandingToDefaults() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Reset to Defaults")
        }

        SectionDivider()

        // ── Section 5: Payment Mode ───────────────────────────────────────────

        SectionHeader(title = "Payment Mode")

        PaymentMode.entries.forEach { mode ->
            val isLive     = mode == PaymentMode.LIVE
            val isSelected = state.selectedMode == mode

            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick  = {
                        if (!isLive) {
                            settingsViewModel.selectMode(mode)
                        }
                    },
                    enabled  = !isLive
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text  = if (isLive) "${mode.label} — Coming soon" else mode.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isLive) Color.White.copy(alpha = 0.4f) else Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Color token row ───────────────────────────────────────────────────────────

@Composable
private fun ColorTokenRow(
    displayName  : String,
    tokenKey     : String,
    hexInput     : String,
    hexError     : String?,
    currentColor : Color,
    onValueChange: (String) -> Unit,
    onSave       : () -> Unit
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp)
    ) {
        Text(
            text     = displayName,
            style    = MaterialTheme.typography.bodySmall,
            color    = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Color swatch
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(currentColor, RoundedCornerShape(4.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            )

            // Hex input field
            OutlinedTextField(
                value         = hexInput,
                onValueChange = onValueChange,
                label         = { Text("Hex") },
                singleLine    = true,
                isError       = hexError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSave() }
                ),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            onSave()
                        }
                    }
            )
        }

        // Inline error text
        if (hexError != null) {
            Text(
                text     = hexError,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 40.dp, top = 2.dp)
            )
        }
    }
}

// ── Section helpers ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color    = Color.White,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(vertical = 4.dp),
        color     = Color.White.copy(alpha = 0.2f),
        thickness = 1.dp
    )
}

// ── Helper: map token key → current Color from ColorTokens ───────────────────

private fun getTokenColor(
    key    : String,
    tokens : com.darkwizards.payments.ui.theme.ColorTokens
): Color = when (key) {
    ColorTokenRepository.KEY_BASE       -> tokens.baseColor
    ColorTokenRepository.KEY_BUTTON1    -> tokens.button1Color
    ColorTokenRepository.KEY_BUTTON2    -> tokens.button2Color
    ColorTokenRepository.KEY_NUMBERPAD  -> tokens.numberPadColor
    ColorTokenRepository.KEY_SPINNER    -> tokens.spinnerColor
    ColorTokenRepository.KEY_TAPICON    -> tokens.tapIconColor
    ColorTokenRepository.KEY_HOMEBAR    -> tokens.homeBarColor
    ColorTokenRepository.KEY_BACKGROUND -> tokens.backgroundColor
    else                                -> Color.Gray
}
