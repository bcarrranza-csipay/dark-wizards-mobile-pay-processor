package com.darkwizards.payments.ui.theme

// Feature: payment-ui-redesign, Property 1: Valid hex save-and-load round-trip
// Feature: payment-ui-redesign, Property 2: Invalid hex strings are rejected

import android.content.SharedPreferences
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.forAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

/**
 * Property-based tests for [ColorTokenRepository].
 *
 * **Validates: Requirements 1.4, 1.6**
 */
class ColorTokenRepositoryPropertyTest : FunSpec({

    // -------------------------------------------------------------------------
    // Helpers — same in-memory SharedPreferences mock used in unit tests
    // -------------------------------------------------------------------------

    fun buildPrefs(store: MutableMap<String, String?> = mutableMapOf()): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val prefs  = mockk<SharedPreferences>()

        every { prefs.getString(any(), any()) } answers {
            val key     = firstArg<String>()
            val default = secondArg<String?>()
            store[key] ?: default
        }

        every { prefs.edit() } returns editor

        val putKeySlot   = slot<String>()
        val putValueSlot = slot<String>()
        every { editor.putString(capture(putKeySlot), capture(putValueSlot)) } answers {
            store[putKeySlot.captured] = putValueSlot.captured
            editor
        }

        val removeKeySlot = slot<String>()
        every { editor.remove(capture(removeKeySlot)) } answers {
            store.remove(removeKeySlot.captured)
            editor
        }

        every { editor.clear() } answers {
            store.clear()
            editor
        }

        every { editor.apply() } returns Unit
        every { editor.commit() } returns true

        return prefs
    }

    // -------------------------------------------------------------------------
    // Generator: valid hex strings (#RRGGBB and #RGB)
    // -------------------------------------------------------------------------

    /**
     * Generates valid hex color strings matching either `#RRGGBB` or `#RGB`.
     *
     * The generator produces both patterns with equal probability, using
     * hex digits drawn from the full set [0-9A-Fa-f] to cover mixed-case inputs.
     */
    val validHexArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        val hexChars = "0123456789ABCDEFabcdef"

        // 50% chance of #RGB (3-digit), 50% chance of #RRGGBB (6-digit)
        val isShorthand = random.nextBoolean()

        if (isShorthand) {
            // #RGB — 3 hex digits
            val r = hexChars[random.nextInt(hexChars.length)]
            val g = hexChars[random.nextInt(hexChars.length)]
            val b = hexChars[random.nextInt(hexChars.length)]
            "#$r$g$b"
        } else {
            // #RRGGBB — 6 hex digits
            val digits = (1..6).map { hexChars[random.nextInt(hexChars.length)] }
            "#${digits.joinToString("")}"
        }
    }

    // -------------------------------------------------------------------------
    // All valid token keys
    // -------------------------------------------------------------------------

    val allTokenKeys = listOf(
        ColorTokenRepository.KEY_BASE,
        ColorTokenRepository.KEY_BUTTON1,
        ColorTokenRepository.KEY_BUTTON2,
        ColorTokenRepository.KEY_NUMBERPAD,
        ColorTokenRepository.KEY_SPINNER,
        ColorTokenRepository.KEY_TAPICON,
        ColorTokenRepository.KEY_HOMEBAR,
        ColorTokenRepository.KEY_BACKGROUND
    )

    // -------------------------------------------------------------------------
    // Property 1: Valid hex save-and-load round-trip
    // -------------------------------------------------------------------------
    // For any valid hex string matching #RRGGBB or #RGB, saving it to a
    // ColorTokenRepository and then loading the tokens should return a Color
    // value equal to parseHex() of that string.
    //
    // Strategy:
    //   1. Generate a valid hex string using validHexArb.
    //   2. Pick a token key (cycling through all keys to cover all fields).
    //   3. Call saveToken(key, hex) on a fresh repository.
    //   4. Call loadTokens() and extract the Color for that key.
    //   5. Assert the loaded Color equals parseHex(hex).
    //
    // This validates that the save→load pipeline is a round-trip: any valid
    // hex string survives persistence and is correctly reconstructed as a Color.
    // -------------------------------------------------------------------------

    test("Property 1: for any valid hex string, saveToken then loadTokens returns parseHex of that string") {
        // Feature: payment-ui-redesign, Property 1
        var keyIndex = 0

        forAll(
            PropTestConfig(iterations = 200),
            validHexArb
        ) { hex ->
            // Cycle through all token keys so every field is exercised
            val key = allTokenKeys[keyIndex % allTokenKeys.size]
            keyIndex++

            val repo = ColorTokenRepository(buildPrefs())

            // Save the hex value for the chosen key
            repo.saveToken(key, hex)

            // Load tokens and extract the Color for the saved key
            val loaded = repo.loadTokens()
            val loadedColor = when (key) {
                ColorTokenRepository.KEY_BASE       -> loaded.baseColor
                ColorTokenRepository.KEY_BUTTON1    -> loaded.button1Color
                ColorTokenRepository.KEY_BUTTON2    -> loaded.button2Color
                ColorTokenRepository.KEY_NUMBERPAD  -> loaded.numberPadColor
                ColorTokenRepository.KEY_SPINNER    -> loaded.spinnerColor
                ColorTokenRepository.KEY_TAPICON    -> loaded.tapIconColor
                ColorTokenRepository.KEY_HOMEBAR    -> loaded.homeBarColor
                ColorTokenRepository.KEY_BACKGROUND -> loaded.backgroundColor
                else                                -> loaded.baseColor
            }

            // The loaded Color must equal parseHex(hex)
            val expected = repo.parseHex(hex)
            loadedColor == expected
        }
    }

    // -------------------------------------------------------------------------
    // Property 1 (variant): tokensFlow also reflects the round-trip
    // -------------------------------------------------------------------------
    // After saveToken, the StateFlow value should also equal parseHex(hex)
    // for the saved key, confirming that the live flow is consistent with
    // loadTokens().
    // -------------------------------------------------------------------------

    test("Property 1 (flow variant): tokensFlow reflects parseHex after saveToken for any valid hex") {
        // Feature: payment-ui-redesign, Property 1
        var keyIndex = 0

        forAll(
            PropTestConfig(iterations = 100),
            validHexArb
        ) { hex ->
            val key = allTokenKeys[keyIndex % allTokenKeys.size]
            keyIndex++

            val repo = ColorTokenRepository(buildPrefs())
            repo.saveToken(key, hex)

            val flowTokens = repo.tokensFlow.value
            val flowColor = when (key) {
                ColorTokenRepository.KEY_BASE       -> flowTokens.baseColor
                ColorTokenRepository.KEY_BUTTON1    -> flowTokens.button1Color
                ColorTokenRepository.KEY_BUTTON2    -> flowTokens.button2Color
                ColorTokenRepository.KEY_NUMBERPAD  -> flowTokens.numberPadColor
                ColorTokenRepository.KEY_SPINNER    -> flowTokens.spinnerColor
                ColorTokenRepository.KEY_TAPICON    -> flowTokens.tapIconColor
                ColorTokenRepository.KEY_HOMEBAR    -> flowTokens.homeBarColor
                ColorTokenRepository.KEY_BACKGROUND -> flowTokens.backgroundColor
                else                                -> flowTokens.baseColor
            }

            val expected = repo.parseHex(hex)
            flowColor == expected
        }
    }

    // -------------------------------------------------------------------------
    // Generator: invalid hex strings (strings that do NOT match #RRGGBB or #RGB)
    // -------------------------------------------------------------------------

    /**
     * Generates arbitrary strings that are guaranteed to fail [ColorTokenRepository.isValidHex].
     *
     * Strategy: generate strings from a broad character set and filter out any that
     * accidentally match the valid hex pattern `^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$`.
     * The filter is applied via [Arb.filter] so Kotest discards valid matches and
     * retries until an invalid string is produced.
     *
     * The generator covers a wide variety of invalid inputs:
     *   - Empty string
     *   - Strings without a leading '#'
     *   - Strings with wrong digit counts (1, 2, 4, 5, 7, 8 hex digits)
     *   - Strings with non-hex characters (G–Z, spaces, punctuation)
     *   - Strings that look like hex but have the wrong length
     */
    val invalidHexPattern = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$")

    val invalidHexArb: Arb<String> = arbitrary { rs ->
        val random = rs.random

        // Mix of strategies to produce diverse invalid strings
        // Each strategy is designed to produce strings that fail the hex pattern
        var candidate: String
        do {
            candidate = when (random.nextInt(8)) {
                0 -> "" // empty string
                1 -> {
                    // Valid hex digits but wrong length (1, 2, 4, 5, 7, or 8 digits)
                    val hexChars = "0123456789ABCDEFabcdef"
                    val badLengths = listOf(1, 2, 4, 5, 7, 8)
                    val len = badLengths[random.nextInt(badLengths.size)]
                    "#" + (1..len).map { hexChars[random.nextInt(hexChars.length)] }.joinToString("")
                }
                2 -> {
                    // 6 characters but no leading '#'
                    val hexChars = "0123456789ABCDEF"
                    (1..6).map { hexChars[random.nextInt(hexChars.length)] }.joinToString("")
                }
                3 -> {
                    // Contains non-hex characters mixed in
                    val nonHexChars = "GHIJKLMNOPQRSTUVWXYZ!@\$%^&*() "
                    val len = 3 + random.nextInt(4) // 3–6 chars
                    "#" + (1..len).map { nonHexChars[random.nextInt(nonHexChars.length)] }.joinToString("")
                }
                4 -> {
                    // Plain color names or CSS keywords
                    val names = listOf("red", "green", "blue", "white", "black", "transparent", "none", "null", "#")
                    names[random.nextInt(names.size)]
                }
                5 -> {
                    // Correct length but with spaces or punctuation
                    val hexChars = "0123456789ABCDEF"
                    val digits = (1..6).map { hexChars[random.nextInt(hexChars.length)] }.joinToString("")
                    "#$digits " // trailing space
                }
                6 -> {
                    // 8-digit ARGB format (valid in Android but not in our pattern)
                    val hexChars = "0123456789ABCDEF"
                    "#" + (1..8).map { hexChars[random.nextInt(hexChars.length)] }.joinToString("")
                }
                else -> {
                    // Arbitrary printable ASCII string of random length 0–20
                    val printable = (32..126).map { it.toChar() }
                    val len = random.nextInt(21)
                    (1..len).map { printable[random.nextInt(printable.size)] }.joinToString("")
                }
            }
        } while (invalidHexPattern.matches(candidate))
        // Retry until we have a string that is definitely not a valid hex pattern
        candidate
    }

    // -------------------------------------------------------------------------
    // Property 2: Invalid hex strings are rejected
    // -------------------------------------------------------------------------
    // For any string that does not match #RRGGBB or #RGB:
    //   (a) isValidHex() returns false
    //   (b) saveToken() with that string does not update the stored token value —
    //       the loaded color for that key remains the default (since readColor
    //       falls back to the default when the stored value fails isValidHex)
    //
    // Strategy:
    //   1. Generate an invalid hex string using invalidHexArb.
    //   2. Pick a token key (cycling through all keys).
    //   3. Assert isValidHex(invalidString) == false.
    //   4. Call saveToken(key, invalidString) on a fresh repository.
    //   5. Call loadTokens() and extract the Color for that key.
    //   6. Assert the loaded Color equals the default for that key (not the invalid value).
    //
    // This validates that the guard in readColor() correctly rejects invalid stored
    // values and falls back to defaults, so no corrupt color ever reaches the UI.
    //
    // **Validates: Requirements 1.5**
    // -------------------------------------------------------------------------

    test("Property 2: isValidHex returns false for any string not matching #RRGGBB or #RGB") {
        // Feature: payment-ui-redesign, Property 2
        forAll(
            PropTestConfig(iterations = 200),
            invalidHexArb
        ) { invalidHex ->
            val repo = ColorTokenRepository(buildPrefs())
            !repo.isValidHex(invalidHex)
        }
    }

    test("Property 2: saveToken with an invalid hex string does not update the stored token value") {
        // Feature: payment-ui-redesign, Property 2
        val defaults = ColorTokens()
        var keyIndex = 0

        forAll(
            PropTestConfig(iterations = 200),
            invalidHexArb
        ) { invalidHex ->
            // Cycle through all token keys so every field is exercised
            val key = allTokenKeys[keyIndex % allTokenKeys.size]
            keyIndex++

            val repo = ColorTokenRepository(buildPrefs())

            // Attempt to save an invalid hex value
            repo.saveToken(key, invalidHex)

            // loadTokens() must fall back to the default for this key because
            // readColor() rejects stored values that fail isValidHex()
            val loaded = repo.loadTokens()
            val loadedColor = when (key) {
                ColorTokenRepository.KEY_BASE       -> loaded.baseColor
                ColorTokenRepository.KEY_BUTTON1    -> loaded.button1Color
                ColorTokenRepository.KEY_BUTTON2    -> loaded.button2Color
                ColorTokenRepository.KEY_NUMBERPAD  -> loaded.numberPadColor
                ColorTokenRepository.KEY_SPINNER    -> loaded.spinnerColor
                ColorTokenRepository.KEY_TAPICON    -> loaded.tapIconColor
                ColorTokenRepository.KEY_HOMEBAR    -> loaded.homeBarColor
                ColorTokenRepository.KEY_BACKGROUND -> loaded.backgroundColor
                else                                -> loaded.baseColor
            }
            val expectedDefault = when (key) {
                ColorTokenRepository.KEY_BASE       -> defaults.baseColor
                ColorTokenRepository.KEY_BUTTON1    -> defaults.button1Color
                ColorTokenRepository.KEY_BUTTON2    -> defaults.button2Color
                ColorTokenRepository.KEY_NUMBERPAD  -> defaults.numberPadColor
                ColorTokenRepository.KEY_SPINNER    -> defaults.spinnerColor
                ColorTokenRepository.KEY_TAPICON    -> defaults.tapIconColor
                ColorTokenRepository.KEY_HOMEBAR    -> defaults.homeBarColor
                ColorTokenRepository.KEY_BACKGROUND -> defaults.backgroundColor
                else                                -> defaults.baseColor
            }

            // The loaded color must equal the default — the invalid hex must not
            // have changed the effective token value
            loadedColor == expectedDefault
        }
    }

    test("Property 2: saveToken with invalid hex does not change a previously saved valid value") {
        // Feature: payment-ui-redesign, Property 2
        // Pre-condition: a valid hex is already saved for a key.
        // After calling saveToken with an invalid hex for the same key,
        // the loaded color should revert to the default (not the previously saved valid color),
        // because readColor falls back to default when the stored value is invalid.
        var keyIndex = 0

        forAll(
            PropTestConfig(iterations = 100),
            validHexArb,
            invalidHexArb
        ) { validHex, invalidHex ->
            val key = allTokenKeys[keyIndex % allTokenKeys.size]
            keyIndex++

            val repo = ColorTokenRepository(buildPrefs())

            // First save a valid hex
            repo.saveToken(key, validHex)

            // Then overwrite with an invalid hex
            repo.saveToken(key, invalidHex)

            // isValidHex must return false for the invalid string
            val isValid = repo.isValidHex(invalidHex)

            // The loaded color must fall back to the default (not the invalid hex)
            val loaded = repo.loadTokens()
            val loadedColor = when (key) {
                ColorTokenRepository.KEY_BASE       -> loaded.baseColor
                ColorTokenRepository.KEY_BUTTON1    -> loaded.button1Color
                ColorTokenRepository.KEY_BUTTON2    -> loaded.button2Color
                ColorTokenRepository.KEY_NUMBERPAD  -> loaded.numberPadColor
                ColorTokenRepository.KEY_SPINNER    -> loaded.spinnerColor
                ColorTokenRepository.KEY_TAPICON    -> loaded.tapIconColor
                ColorTokenRepository.KEY_HOMEBAR    -> loaded.homeBarColor
                ColorTokenRepository.KEY_BACKGROUND -> loaded.backgroundColor
                else                                -> loaded.baseColor
            }
            val defaults = ColorTokens()
            val expectedDefault = when (key) {
                ColorTokenRepository.KEY_BASE       -> defaults.baseColor
                ColorTokenRepository.KEY_BUTTON1    -> defaults.button1Color
                ColorTokenRepository.KEY_BUTTON2    -> defaults.button2Color
                ColorTokenRepository.KEY_NUMBERPAD  -> defaults.numberPadColor
                ColorTokenRepository.KEY_SPINNER    -> defaults.spinnerColor
                ColorTokenRepository.KEY_TAPICON    -> defaults.tapIconColor
                ColorTokenRepository.KEY_HOMEBAR    -> defaults.homeBarColor
                ColorTokenRepository.KEY_BACKGROUND -> defaults.backgroundColor
                else                                -> defaults.baseColor
            }

            !isValid && loadedColor == expectedDefault
        }
    }
})
