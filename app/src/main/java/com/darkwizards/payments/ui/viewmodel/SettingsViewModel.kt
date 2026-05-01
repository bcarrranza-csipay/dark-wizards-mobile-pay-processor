package com.darkwizards.payments.ui.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.darkwizards.payments.ui.theme.ColorTokenRepository
import com.darkwizards.payments.ui.theme.ColorTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ── State ─────────────────────────────────────────────────────────────────────

/**
 * Immutable snapshot of all settings managed by [SettingsViewModel].
 *
 * @param creditSurchargePercent  Surcharge percentage string for Credit card type (e.g. "3.0")
 * @param debitSurchargePercent   Surcharge percentage string for Debit card type (e.g. "0")
 * @param tipEnabled              Whether tip selection is shown on the Total Amount screen
 * @param tipPresets              Up to three preset tip percentage strings (e.g. ["15", "18", "20"])
 * @param avsEnabled              Whether AVS billing address fields are shown on Manual Entry screen
 * @param colorTokens             Current resolved [ColorTokens] (defaults + any saved overrides)
 * @param hexInputs               Map of token key → current text in the hex input field
 * @param hexErrors               Map of token key → validation error message, or null if valid
 * @param selectedMode            Currently active [PaymentMode]
 */
data class SettingsState(
    val creditSurchargePercent: String          = "",
    val debitSurchargePercent: String           = "",
    val tipEnabled: Boolean                     = false,
    val tipPresets: List<String>                = emptyList(),
    val avsEnabled: Boolean                     = false,
    val colorTokens: ColorTokens                = ColorTokens(),
    val hexInputs: Map<String, String>          = emptyMap(),
    val hexErrors: Map<String, String?>         = emptyMap(),
    val selectedMode: PaymentMode               = PaymentMode.SIMULATOR
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * UI-only ViewModel for the Settings screen.
 *
 * Responsibilities:
 *  - Persist and restore surcharge percentages, tip configuration, and AVS toggle
 *    via a dedicated [SharedPreferences] instance (`"settings"`).
 *  - Delegate color token persistence to [ColorTokenRepository].
 *  - Delegate payment mode changes to [PaymentViewModel.selectMode].
 *
 * **UI-only boundary:** No data/domain layer is touched. All persisted values are
 * consumed exclusively by Compose UI composables.
 *
 * @param colorTokenRepository  Repository for color token persistence and validation.
 * @param settingsPrefs         SharedPreferences instance keyed `"settings"`.
 * @param paymentViewModel      Existing ViewModel whose [PaymentViewModel.selectMode] is
 *                              called when the merchant changes the payment mode.
 */
class SettingsViewModel(
    private val colorTokenRepository: ColorTokenRepository,
    private val settingsPrefs: SharedPreferences,
    private val paymentViewModel: PaymentViewModel
) : ViewModel() {

    // ── SharedPreferences keys ────────────────────────────────────────────────

    companion object {
        private const val KEY_CREDIT_SURCHARGE  = "credit_surcharge_percent"
        private const val KEY_DEBIT_SURCHARGE   = "debit_surcharge_percent"
        private const val KEY_TIP_ENABLED       = "tip_enabled"
        private const val KEY_TIP_PRESET_0      = "tip_preset_0"
        private const val KEY_TIP_PRESET_1      = "tip_preset_1"
        private const val KEY_TIP_PRESET_2      = "tip_preset_2"
        private const val KEY_AVS_ENABLED       = "avs_enabled"

        /** All token keys in display order — mirrors [ColorTokenRepository] companion keys. */
        val TOKEN_KEYS = listOf(
            ColorTokenRepository.KEY_BASE,
            ColorTokenRepository.KEY_BUTTON1,
            ColorTokenRepository.KEY_BUTTON2,
            ColorTokenRepository.KEY_NUMBERPAD,
            ColorTokenRepository.KEY_SPINNER,
            ColorTokenRepository.KEY_TAPICON,
            ColorTokenRepository.KEY_HOMEBAR,
            ColorTokenRepository.KEY_BACKGROUND
        )
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(buildInitialState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    // ── Initialisation ────────────────────────────────────────────────────────

    private fun buildInitialState(): SettingsState {
        val tokens = colorTokenRepository.loadTokens()
        val hexInputs = buildDefaultHexInputs(tokens)
        val tipPresets = listOf(
            settingsPrefs.getString(KEY_TIP_PRESET_0, "") ?: "",
            settingsPrefs.getString(KEY_TIP_PRESET_1, "") ?: "",
            settingsPrefs.getString(KEY_TIP_PRESET_2, "") ?: ""
        )
        return SettingsState(
            creditSurchargePercent = settingsPrefs.getString(KEY_CREDIT_SURCHARGE, "") ?: "",
            debitSurchargePercent  = settingsPrefs.getString(KEY_DEBIT_SURCHARGE, "") ?: "",
            tipEnabled             = settingsPrefs.getBoolean(KEY_TIP_ENABLED, false),
            tipPresets             = tipPresets,
            avsEnabled             = settingsPrefs.getBoolean(KEY_AVS_ENABLED, false),
            colorTokens            = tokens,
            hexInputs              = hexInputs,
            hexErrors              = TOKEN_KEYS.associateWith { null },
            selectedMode           = paymentViewModel.selectedMode.value
        )
    }

    /**
     * Builds the initial hex input map from the current [ColorTokens] values,
     * converting each [androidx.compose.ui.graphics.Color] back to a `#RRGGBB` string.
     */
    private fun buildDefaultHexInputs(tokens: ColorTokens): Map<String, String> = mapOf(
        ColorTokenRepository.KEY_BASE       to tokens.baseColor.toHexString(),
        ColorTokenRepository.KEY_BUTTON1    to tokens.button1Color.toHexString(),
        ColorTokenRepository.KEY_BUTTON2    to tokens.button2Color.toHexString(),
        ColorTokenRepository.KEY_NUMBERPAD  to tokens.numberPadColor.toHexString(),
        ColorTokenRepository.KEY_SPINNER    to tokens.spinnerColor.toHexString(),
        ColorTokenRepository.KEY_TAPICON    to tokens.tapIconColor.toHexString(),
        ColorTokenRepository.KEY_HOMEBAR    to tokens.homeBarColor.toHexString(),
        ColorTokenRepository.KEY_BACKGROUND to tokens.backgroundColor.toHexString()
    )

    // ── Surcharge ─────────────────────────────────────────────────────────────

    /**
     * Updates the surcharge percentage for [cardType] ("credit" or "debit").
     *
     * Only digits and at most one decimal point are accepted; any other character is
     * silently stripped before the value is stored (Requirement 5.3).
     */
    fun updateSurcharge(cardType: String, value: String) {
        val filtered = filterNumericInput(value)
        val prefKey = if (cardType.equals("credit", ignoreCase = true)) {
            KEY_CREDIT_SURCHARGE
        } else {
            KEY_DEBIT_SURCHARGE
        }
        settingsPrefs.edit().putString(prefKey, filtered).apply()
        _state.update { current ->
            if (cardType.equals("credit", ignoreCase = true)) {
                current.copy(creditSurchargePercent = filtered)
            } else {
                current.copy(debitSurchargePercent = filtered)
            }
        }
    }

    // ── Tip ───────────────────────────────────────────────────────────────────

    /** Enables or disables tip selection on the Total Amount screen. */
    fun setTipEnabled(enabled: Boolean) {
        settingsPrefs.edit().putBoolean(KEY_TIP_ENABLED, enabled).apply()
        _state.update { it.copy(tipEnabled = enabled) }
    }

    /**
     * Updates a single tip preset at [index] (0–2).
     *
     * Only digits and at most one decimal point are accepted (same filtering as surcharge).
     */
    fun updateTipPreset(index: Int, value: String) {
        require(index in 0..2) { "Tip preset index must be 0, 1, or 2" }
        val filtered = filterNumericInput(value)
        val prefKey = when (index) {
            0    -> KEY_TIP_PRESET_0
            1    -> KEY_TIP_PRESET_1
            else -> KEY_TIP_PRESET_2
        }
        settingsPrefs.edit().putString(prefKey, filtered).apply()
        _state.update { current ->
            val updated = current.tipPresets.toMutableList().also { it[index] = filtered }
            current.copy(tipPresets = updated)
        }
    }

    // ── AVS ───────────────────────────────────────────────────────────────────

    /** Enables or disables AVS billing address fields on the Manual Entry screen. */
    fun setAvsEnabled(enabled: Boolean) {
        settingsPrefs.edit().putBoolean(KEY_AVS_ENABLED, enabled).apply()
        _state.update { it.copy(avsEnabled = enabled) }
    }

    // ── Branding colors ───────────────────────────────────────────────────────

    /**
     * Updates the in-memory hex input text for [tokenKey] without persisting or validating.
     * Call [saveHexToken] to validate and persist.
     */
    fun updateHexInput(tokenKey: String, hex: String) {
        _state.update { current ->
            current.copy(hexInputs = current.hexInputs + (tokenKey to hex))
        }
    }

    /**
     * Validates the current hex input for [tokenKey] and, if valid, persists it via
     * [ColorTokenRepository.saveToken] and clears any error. If invalid, sets the error
     * message to `"Invalid hex color"` and does not persist.
     *
     * After a successful save, [state] is updated with the new [ColorTokens] from the
     * repository and the hex input map is refreshed to reflect the saved value.
     */
    fun saveHexToken(tokenKey: String) {
        val hex = _state.value.hexInputs[tokenKey].orEmpty()
        if (colorTokenRepository.isValidHex(hex)) {
            colorTokenRepository.saveToken(tokenKey, hex)
            val updatedTokens = colorTokenRepository.loadTokens()
            _state.update { current ->
                current.copy(
                    colorTokens = updatedTokens,
                    hexInputs   = buildDefaultHexInputs(updatedTokens),
                    hexErrors   = current.hexErrors + (tokenKey to null)
                )
            }
        } else {
            _state.update { current ->
                current.copy(hexErrors = current.hexErrors + (tokenKey to "Invalid hex color"))
            }
        }
    }

    /**
     * Resets all color tokens to the Wizard palette defaults via [ColorTokenRepository.resetToDefaults].
     * Refreshes [state] with the default tokens and clears all hex errors.
     */
    fun resetBrandingToDefaults() {
        colorTokenRepository.resetToDefaults()
        val defaultTokens = ColorTokens()
        val defaultHexInputs = buildDefaultHexInputs(defaultTokens)
        _state.update { current ->
            current.copy(
                colorTokens = defaultTokens,
                hexInputs   = defaultHexInputs,
                hexErrors   = TOKEN_KEYS.associateWith { null }
            )
        }
    }

    // ── Payment mode ──────────────────────────────────────────────────────────

    /**
     * Delegates mode selection to [PaymentViewModel.selectMode] and updates [state]
     * to reflect the new selection.
     *
     * [PaymentMode.LIVE] is a no-op (disabled in the UI and in [PaymentViewModel]).
     */
    fun selectMode(mode: PaymentMode) {
        paymentViewModel.selectMode(mode)
        _state.update { it.copy(selectedMode = mode) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Strips any character that is not a digit or a decimal point, and ensures at most
     * one decimal point is present. Used for surcharge and tip preset inputs.
     */
    private fun filterNumericInput(input: String): String {
        val digitsAndDot = input.filter { it.isDigit() || it == '.' }
        val firstDot = digitsAndDot.indexOf('.')
        return if (firstDot == -1) {
            digitsAndDot
        } else {
            // Keep everything up to and including the first '.', then only digits after it
            digitsAndDot.substring(0, firstDot + 1) +
                digitsAndDot.substring(firstDot + 1).filter { it.isDigit() }
        }
    }
}

// ── Extension ─────────────────────────────────────────────────────────────────

/**
 * Converts a Compose [androidx.compose.ui.graphics.Color] to a `#RRGGBB` hex string.
 * The alpha channel is ignored since color tokens are always fully opaque.
 */
private fun androidx.compose.ui.graphics.Color.toHexString(): String {
    val r = (red   * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue  * 255).toInt()
    return "#%02X%02X%02X".format(r, g, b)
}
