package com.darkwizards.payments.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Named color tokens for the Dark Wizards payment app.
 *
 * Each token maps to a specific UI role and is applied consistently across all screens.
 * Defaults reflect the existing Wizard palette so the app appearance is unchanged until
 * a merchant explicitly overrides a token via the Settings screen.
 *
 * Token → UI role mapping:
 *  - baseColor       : general brand accent (reserved for future use / tinting)
 *  - button1Color    : primary action Pill_Buttons (e.g. "Proceed to Payment", "Submit")
 *  - button2Color    : secondary action buttons (e.g. "Debit", "Skip")
 *  - numberPadColor  : numeric keypad digit keys on the Pay screen
 *  - spinnerColor    : CircularProgressIndicator / loading indicators
 *  - tapIconColor    : NFC contactless icon on the Card Present screen
 *  - homeBarColor    : bottom navigation bar background
 *  - backgroundColor : screen background color applied to all composable roots
 */
data class ColorTokens(
    val baseColor: Color       = Color(0xFF8A2CE2),  // primary purple
    val button1Color: Color    = Color(0xFF8A2CE2),  // primary action buttons
    val button2Color: Color    = Color(0xFF7C68EE),  // secondary action buttons
    val numberPadColor: Color  = Color(0xFF6A5BCD),  // numpad digit keys
    val spinnerColor: Color    = Color(0xFF8A2CE2),  // loading indicators
    val tapIconColor: Color    = Color(0xFF8A2CE2),  // NFC contactless icon
    val homeBarColor: Color    = Color(0xFF5E4B8B),  // bottom navigation bar
    val backgroundColor: Color = Color(0xFF4A0080)   // screen backgrounds
)

/**
 * CompositionLocal that provides [ColorTokens] to the entire composition tree.
 *
 * Provided at the root in MainActivity via:
 *   CompositionLocalProvider(LocalColorTokens provides tokens) { ... }
 *
 * Consumed in any composable via:
 *   val tokens = LocalColorTokens.current
 */
val LocalColorTokens = staticCompositionLocalOf { ColorTokens() }
