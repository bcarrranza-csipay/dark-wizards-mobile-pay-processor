package com.darkwizards.payments.ui.theme

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists and loads [ColorTokens] overrides using [SharedPreferences].
 *
 * Each token is stored individually under its own key so that a single override
 * does not require re-saving the entire token set. On first run (empty prefs) all
 * tokens fall back to the [ColorTokens] defaults, preserving the existing Wizard palette.
 *
 * Usage in MainActivity:
 *   val repo = ColorTokenRepository(getSharedPreferences("color_tokens", Context.MODE_PRIVATE))
 *   val tokens by repo.tokensFlow.collectAsState(initial = repo.loadTokens())
 */
class ColorTokenRepository(private val prefs: SharedPreferences) {

    // -------------------------------------------------------------------------
    // SharedPreferences key constants
    // -------------------------------------------------------------------------

    companion object {
        const val KEY_BASE        = "token_base"
        const val KEY_BUTTON1     = "token_button1"
        const val KEY_BUTTON2     = "token_button2"
        const val KEY_NUMBERPAD   = "token_numberpad"
        const val KEY_SPINNER     = "token_spinner"
        const val KEY_TAPICON     = "token_tapicon"
        const val KEY_HOMEBAR     = "token_homebar"
        const val KEY_BACKGROUND  = "token_background"

        private val hexPattern = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$")
    }

    // -------------------------------------------------------------------------
    // StateFlow — emits a new ColorTokens whenever any token is saved or reset
    // -------------------------------------------------------------------------

    private val _tokensFlow = MutableStateFlow(loadTokens())

    /**
     * Hot flow of the current [ColorTokens]. Collect this in MainActivity so that
     * the entire composition tree re-renders whenever a token changes.
     */
    val tokensFlow: StateFlow<ColorTokens> = _tokensFlow.asStateFlow()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Reads all token overrides from [SharedPreferences] and returns a [ColorTokens]
     * instance. Any key that has not been saved falls back to the default value
     * defined in [ColorTokens].
     */
    fun loadTokens(): ColorTokens {
        val defaults = ColorTokens()
        return ColorTokens(
            baseColor       = readColor(KEY_BASE,       defaults.baseColor),
            button1Color    = readColor(KEY_BUTTON1,    defaults.button1Color),
            button2Color    = readColor(KEY_BUTTON2,    defaults.button2Color),
            numberPadColor  = readColor(KEY_NUMBERPAD,  defaults.numberPadColor),
            spinnerColor    = readColor(KEY_SPINNER,    defaults.spinnerColor),
            tapIconColor    = readColor(KEY_TAPICON,    defaults.tapIconColor),
            homeBarColor    = readColor(KEY_HOMEBAR,    defaults.homeBarColor),
            backgroundColor = readColor(KEY_BACKGROUND, defaults.backgroundColor)
        )
    }

    /**
     * Persists a single token override. [hexValue] must be a valid hex color string
     * (validated by [isValidHex]); callers should validate before calling this method.
     *
     * After saving, [tokensFlow] emits the updated [ColorTokens].
     */
    fun saveToken(key: String, hexValue: String) {
        prefs.edit().putString(key, hexValue).apply()
        _tokensFlow.value = loadTokens()
    }

    /**
     * Clears all token overrides from [SharedPreferences], restoring every token to
     * its default Wizard-palette value. [tokensFlow] emits the default [ColorTokens].
     */
    fun resetToDefaults() {
        prefs.edit()
            .remove(KEY_BASE)
            .remove(KEY_BUTTON1)
            .remove(KEY_BUTTON2)
            .remove(KEY_NUMBERPAD)
            .remove(KEY_SPINNER)
            .remove(KEY_TAPICON)
            .remove(KEY_HOMEBAR)
            .remove(KEY_BACKGROUND)
            .apply()
        _tokensFlow.value = ColorTokens()
    }

    /**
     * Returns `true` if [input] matches the pattern `#RRGGBB` or `#RGB` (case-insensitive).
     * Returns `false` for any other string, including empty strings and strings without
     * a leading `#`.
     */
    fun isValidHex(input: String): Boolean = hexPattern.matches(input)

    /**
     * Converts a validated hex color string (`#RRGGBB` or `#RGB`) to a Compose [Color].
     *
     * Callers must ensure [input] passes [isValidHex] before calling this method.
     * For `#RGB` shorthand, each nibble is expanded to `RR GG BB` (e.g. `#ABC` → `#AABBCC`).
     */
    fun parseHex(input: String): Color {
        val hex = input.trimStart('#')
        val expanded = if (hex.length == 3) {
            // Expand shorthand: #RGB → #RRGGBB
            "${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}"
        } else {
            hex
        }
        val argb = expanded.toLong(16) or 0xFF000000L
        return Color(argb.toInt())
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Reads a hex string from [SharedPreferences] for [key] and converts it to a [Color].
     * Returns [default] if the key is absent or the stored value is not a valid hex string.
     */
    private fun readColor(key: String, default: Color): Color {
        val stored = prefs.getString(key, null) ?: return default
        return if (isValidHex(stored)) parseHex(stored) else default
    }
}
