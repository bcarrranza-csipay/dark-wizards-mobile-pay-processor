package com.darkwizards.payments.ui.theme

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

/**
 * Unit tests for [ColorTokenRepository].
 *
 * SharedPreferences is mocked with MockK so these run as pure JVM tests without
 * an Android device or Robolectric. Each test builds its own in-memory prefs map
 * via a helper that wires the mock editor back to the map, giving us a realistic
 * read/write/remove cycle without touching the Android framework.
 */
class ColorTokenRepositoryTest : FunSpec({

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a MockK [SharedPreferences] backed by [store]. Supports getString,
     * edit().putString(), edit().remove(), and edit().clear() — enough to exercise
     * all [ColorTokenRepository] operations.
     */
    fun buildPrefs(store: MutableMap<String, String?> = mutableMapOf()): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val prefs  = mockk<SharedPreferences>()

        // getString: return from store or null
        every { prefs.getString(any(), any()) } answers {
            val key     = firstArg<String>()
            val default = secondArg<String?>()
            store[key] ?: default
        }

        // edit() always returns the same editor
        every { prefs.edit() } returns editor

        // putString: write to store, return editor for chaining
        val putKeySlot   = slot<String>()
        val putValueSlot = slot<String>()
        every { editor.putString(capture(putKeySlot), capture(putValueSlot)) } answers {
            store[putKeySlot.captured] = putValueSlot.captured
            editor
        }

        // remove: delete from store, return editor for chaining
        val removeKeySlot = slot<String>()
        every { editor.remove(capture(removeKeySlot)) } answers {
            store.remove(removeKeySlot.captured)
            editor
        }

        // clear: wipe store, return editor for chaining
        every { editor.clear() } answers {
            store.clear()
            editor
        }

        // apply / commit: no-op (changes already applied to the in-memory map)
        every { editor.apply() } returns Unit
        every { editor.commit() } returns true

        return prefs
    }

    // -------------------------------------------------------------------------
    // loadTokens() — first run (empty prefs)
    // -------------------------------------------------------------------------

    test("loadTokens returns ColorTokens defaults when prefs are empty") {
        val repo     = ColorTokenRepository(buildPrefs())
        val defaults = ColorTokens()
        val loaded   = repo.loadTokens()

        loaded.baseColor       shouldBe defaults.baseColor
        loaded.button1Color    shouldBe defaults.button1Color
        loaded.button2Color    shouldBe defaults.button2Color
        loaded.numberPadColor  shouldBe defaults.numberPadColor
        loaded.spinnerColor    shouldBe defaults.spinnerColor
        loaded.tapIconColor    shouldBe defaults.tapIconColor
        loaded.homeBarColor    shouldBe defaults.homeBarColor
        loaded.backgroundColor shouldBe defaults.backgroundColor
    }

    test("loadTokens returns full default ColorTokens instance when prefs are empty") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.loadTokens() shouldBe ColorTokens()
    }

    // -------------------------------------------------------------------------
    // saveToken() + loadTokens() — round-trip
    // -------------------------------------------------------------------------

    test("saveToken then loadTokens returns the saved color for button1Color") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.saveToken(ColorTokenRepository.KEY_BUTTON1, "#FF0000")
        val loaded = repo.loadTokens()
        loaded.button1Color shouldBe Color(0xFFFF0000.toInt())
    }

    test("saveToken then loadTokens returns the saved color for baseColor") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.saveToken(ColorTokenRepository.KEY_BASE, "#123456")
        val loaded = repo.loadTokens()
        loaded.baseColor shouldBe Color(0xFF123456.toInt())
    }

    test("saveToken then loadTokens returns the saved color for button2Color") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.saveToken(ColorTokenRepository.KEY_BUTTON2, "#00FF00")
        val loaded = repo.loadTokens()
        loaded.button2Color shouldBe Color(0xFF00FF00.toInt())
    }

    test("saveToken then loadTokens returns the saved color for numberPadColor") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.saveToken(ColorTokenRepository.KEY_NUMBERPAD, "#0000FF")
        val loaded = repo.loadTokens()
        loaded.numberPadColor shouldBe Color(0xFF0000FF.toInt())
    }

    test("saveToken then loadTokens returns the saved color for spinnerColor") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.saveToken(ColorTokenRepository.KEY_SPINNER, "#AABBCC")
        val loaded = repo.loadTokens()
        loaded.spinnerColor shouldBe Color(0xFFAABBCC.toInt())
    }

    test("saveToken then loadTokens returns the saved color for tapIconColor") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.saveToken(ColorTokenRepository.KEY_TAPICON, "#FFFFFF")
        val loaded = repo.loadTokens()
        loaded.tapIconColor shouldBe Color(0xFFFFFFFF.toInt())
    }

    test("saveToken then loadTokens returns the saved color for homeBarColor") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.saveToken(ColorTokenRepository.KEY_HOMEBAR, "#000000")
        val loaded = repo.loadTokens()
        loaded.homeBarColor shouldBe Color(0xFF000000.toInt())
    }

    test("saveToken then loadTokens returns the saved color for backgroundColor") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.saveToken(ColorTokenRepository.KEY_BACKGROUND, "#DDEEFF")
        val loaded = repo.loadTokens()
        loaded.backgroundColor shouldBe Color(0xFFDDEEFF.toInt())
    }

    test("saveToken for one key does not affect other tokens") {
        val repo     = ColorTokenRepository(buildPrefs())
        val defaults = ColorTokens()
        repo.saveToken(ColorTokenRepository.KEY_BUTTON1, "#FF0000")
        val loaded = repo.loadTokens()

        // Only button1Color should differ from defaults
        loaded.button1Color    shouldBe Color(0xFFFF0000.toInt())
        loaded.baseColor       shouldBe defaults.baseColor
        loaded.button2Color    shouldBe defaults.button2Color
        loaded.numberPadColor  shouldBe defaults.numberPadColor
        loaded.spinnerColor    shouldBe defaults.spinnerColor
        loaded.tapIconColor    shouldBe defaults.tapIconColor
        loaded.homeBarColor    shouldBe defaults.homeBarColor
        loaded.backgroundColor shouldBe defaults.backgroundColor
    }

    test("saveToken overwrites a previously saved value") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.saveToken(ColorTokenRepository.KEY_BASE, "#FF0000")
        repo.saveToken(ColorTokenRepository.KEY_BASE, "#00FF00")
        val loaded = repo.loadTokens()
        loaded.baseColor shouldBe Color(0xFF00FF00.toInt())
    }

    test("saveToken updates tokensFlow with the new color") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.saveToken(ColorTokenRepository.KEY_BUTTON1, "#FF0000")
        repo.tokensFlow.value.button1Color shouldBe Color(0xFFFF0000.toInt())
    }

    // -------------------------------------------------------------------------
    // isValidHex() — valid patterns
    // -------------------------------------------------------------------------

    test("isValidHex accepts 6-digit uppercase hex #RRGGBB") {
        ColorTokenRepository(buildPrefs()).isValidHex("#AABBCC").shouldBeTrue()
    }

    test("isValidHex accepts 6-digit lowercase hex #rrggbb") {
        ColorTokenRepository(buildPrefs()).isValidHex("#aabbcc").shouldBeTrue()
    }

    test("isValidHex accepts 6-digit mixed-case hex") {
        ColorTokenRepository(buildPrefs()).isValidHex("#AaBbCc").shouldBeTrue()
    }

    test("isValidHex accepts 3-digit shorthand #RGB uppercase") {
        ColorTokenRepository(buildPrefs()).isValidHex("#ABC").shouldBeTrue()
    }

    test("isValidHex accepts 3-digit shorthand #RGB lowercase") {
        ColorTokenRepository(buildPrefs()).isValidHex("#abc").shouldBeTrue()
    }

    test("isValidHex accepts 3-digit shorthand #RGB mixed-case") {
        ColorTokenRepository(buildPrefs()).isValidHex("#aBc").shouldBeTrue()
    }

    test("isValidHex accepts all-zeros #000000") {
        ColorTokenRepository(buildPrefs()).isValidHex("#000000").shouldBeTrue()
    }

    test("isValidHex accepts all-Fs #FFFFFF") {
        ColorTokenRepository(buildPrefs()).isValidHex("#FFFFFF").shouldBeTrue()
    }

    test("isValidHex accepts 3-digit all-zeros #000") {
        ColorTokenRepository(buildPrefs()).isValidHex("#000").shouldBeTrue()
    }

    test("isValidHex accepts 3-digit all-Fs #FFF") {
        ColorTokenRepository(buildPrefs()).isValidHex("#FFF").shouldBeTrue()
    }

    // -------------------------------------------------------------------------
    // isValidHex() — invalid patterns
    // -------------------------------------------------------------------------

    test("isValidHex rejects empty string") {
        ColorTokenRepository(buildPrefs()).isValidHex("").shouldBeFalse()
    }

    test("isValidHex rejects string without leading hash") {
        ColorTokenRepository(buildPrefs()).isValidHex("AABBCC").shouldBeFalse()
    }

    test("isValidHex rejects 4-digit hex") {
        ColorTokenRepository(buildPrefs()).isValidHex("#AABB").shouldBeFalse()
    }

    test("isValidHex rejects 5-digit hex") {
        ColorTokenRepository(buildPrefs()).isValidHex("#AABBC").shouldBeFalse()
    }

    test("isValidHex rejects 7-digit hex") {
        ColorTokenRepository(buildPrefs()).isValidHex("#AABBCCD").shouldBeFalse()
    }

    test("isValidHex rejects 8-digit hex (ARGB format)") {
        ColorTokenRepository(buildPrefs()).isValidHex("#FFAABBCC").shouldBeFalse()
    }

    test("isValidHex rejects non-hex characters in 6-digit string") {
        ColorTokenRepository(buildPrefs()).isValidHex("#GGHHII").shouldBeFalse()
    }

    test("isValidHex rejects non-hex characters in 3-digit string") {
        ColorTokenRepository(buildPrefs()).isValidHex("#GHI").shouldBeFalse()
    }

    test("isValidHex rejects plain text") {
        ColorTokenRepository(buildPrefs()).isValidHex("red").shouldBeFalse()
    }

    test("isValidHex rejects hash only") {
        ColorTokenRepository(buildPrefs()).isValidHex("#").shouldBeFalse()
    }

    test("isValidHex rejects string with spaces") {
        ColorTokenRepository(buildPrefs()).isValidHex("#AA BB CC").shouldBeFalse()
    }

    test("isValidHex rejects null-like empty input") {
        ColorTokenRepository(buildPrefs()).isValidHex("null").shouldBeFalse()
    }

    // -------------------------------------------------------------------------
    // resetToDefaults()
    // -------------------------------------------------------------------------

    test("resetToDefaults after saving all tokens returns default ColorTokens") {
        val repo = ColorTokenRepository(buildPrefs())

        // Override every token
        repo.saveToken(ColorTokenRepository.KEY_BASE,       "#FF0000")
        repo.saveToken(ColorTokenRepository.KEY_BUTTON1,    "#FF0001")
        repo.saveToken(ColorTokenRepository.KEY_BUTTON2,    "#FF0002")
        repo.saveToken(ColorTokenRepository.KEY_NUMBERPAD,  "#FF0003")
        repo.saveToken(ColorTokenRepository.KEY_SPINNER,    "#FF0004")
        repo.saveToken(ColorTokenRepository.KEY_TAPICON,    "#FF0005")
        repo.saveToken(ColorTokenRepository.KEY_HOMEBAR,    "#FF0006")
        repo.saveToken(ColorTokenRepository.KEY_BACKGROUND, "#FF0007")

        repo.resetToDefaults()

        repo.loadTokens() shouldBe ColorTokens()
    }

    test("resetToDefaults on empty prefs still returns default ColorTokens") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.resetToDefaults()
        repo.loadTokens() shouldBe ColorTokens()
    }

    test("resetToDefaults updates tokensFlow to default ColorTokens") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.saveToken(ColorTokenRepository.KEY_BASE, "#FF0000")
        repo.resetToDefaults()
        repo.tokensFlow.value shouldBe ColorTokens()
    }

    test("resetToDefaults clears individual token overrides so each field matches default") {
        val repo     = ColorTokenRepository(buildPrefs())
        val defaults = ColorTokens()

        repo.saveToken(ColorTokenRepository.KEY_BUTTON1,    "#FF0000")
        repo.saveToken(ColorTokenRepository.KEY_HOMEBAR,    "#00FF00")
        repo.saveToken(ColorTokenRepository.KEY_BACKGROUND, "#0000FF")

        repo.resetToDefaults()
        val loaded = repo.loadTokens()

        loaded.button1Color    shouldBe defaults.button1Color
        loaded.homeBarColor    shouldBe defaults.homeBarColor
        loaded.backgroundColor shouldBe defaults.backgroundColor
    }

    test("saveToken after resetToDefaults applies the new override correctly") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.saveToken(ColorTokenRepository.KEY_BASE, "#FF0000")
        repo.resetToDefaults()
        repo.saveToken(ColorTokenRepository.KEY_BASE, "#00FF00")
        repo.loadTokens().baseColor shouldBe Color(0xFF00FF00.toInt())
    }

    // -------------------------------------------------------------------------
    // parseHex() — color conversion
    // -------------------------------------------------------------------------

    test("parseHex converts 6-digit hex to correct Color") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.parseHex("#FF0000") shouldBe Color(0xFFFF0000.toInt())
    }

    test("parseHex converts 3-digit shorthand by expanding each nibble") {
        val repo = ColorTokenRepository(buildPrefs())
        // #F00 → #FF0000
        repo.parseHex("#F00") shouldBe Color(0xFFFF0000.toInt())
    }

    test("parseHex converts #ABC to #AABBCC") {
        val repo = ColorTokenRepository(buildPrefs())
        repo.parseHex("#ABC") shouldBe Color(0xFFAABBCC.toInt())
    }

    test("parseHex always sets full alpha (0xFF)") {
        val repo = ColorTokenRepository(buildPrefs())
        val color = repo.parseHex("#123456")
        // Alpha component should be 1.0f (fully opaque)
        color.alpha shouldBe 1.0f
    }
})
