package com.darkwizards.payments.ui.viewmodel

import android.content.SharedPreferences
import com.darkwizards.payments.ui.theme.ColorTokenRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

/**
 * Unit tests for [SettingsViewModel].
 *
 * SharedPreferences and ColorTokenRepository are mocked with MockK so these run
 * as pure JVM tests without an Android device or Robolectric. The same in-memory
 * prefs helper pattern used in ColorTokenRepositoryTest is applied here.
 *
 * Requirements covered: 5.2, 5.3, 5.4, 6.2, 6.3, 6.6, 7.4
 */
class SettingsViewModelTest : FunSpec({

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a MockK [SharedPreferences] backed by [store]. Supports getString,
     * getBoolean, edit().putString(), edit().putBoolean(), and edit().apply() —
     * enough to exercise all [SettingsViewModel] persistence operations.
     */
    fun buildPrefs(store: MutableMap<String, Any?> = mutableMapOf()): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val prefs  = mockk<SharedPreferences>()

        every { prefs.getString(any(), any()) } answers {
            val key     = firstArg<String>()
            val default = secondArg<String?>()
            (store[key] as? String) ?: default
        }

        every { prefs.getBoolean(any(), any()) } answers {
            val key     = firstArg<String>()
            val default = secondArg<Boolean>()
            (store[key] as? Boolean) ?: default
        }

        every { prefs.edit() } returns editor

        val putStringKeySlot   = slot<String>()
        val putStringValueSlot = slot<String>()
        every { editor.putString(capture(putStringKeySlot), capture(putStringValueSlot)) } answers {
            store[putStringKeySlot.captured] = putStringValueSlot.captured
            editor
        }

        val putBoolKeySlot   = slot<String>()
        val putBoolValueSlot = slot<Boolean>()
        every { editor.putBoolean(capture(putBoolKeySlot), capture(putBoolValueSlot)) } answers {
            store[putBoolKeySlot.captured] = putBoolValueSlot.captured
            editor
        }

        every { editor.apply() } returns Unit
        every { editor.commit() } returns true

        return prefs
    }

    /**
     * Builds a mock [ColorTokenRepository] that reports all hex strings as invalid
     * by default. Pass [validHex] to make [isValidHex] return true for that string.
     */
    fun buildColorRepo(validHex: String? = null): ColorTokenRepository {
        val repo = mockk<ColorTokenRepository>(relaxed = true)
        every { repo.loadTokens() } returns com.darkwizards.payments.ui.theme.ColorTokens()
        every { repo.isValidHex(any()) } answers {
            val input = firstArg<String>()
            validHex != null && input == validHex
        }
        return repo
    }

    /**
     * Builds a mock [PaymentViewModel] with a stable [selectedMode] StateFlow.
     */
    fun buildPaymentViewModel(): PaymentViewModel {
        val vm = mockk<PaymentViewModel>(relaxed = true)
        every { vm.selectedMode } returns kotlinx.coroutines.flow.MutableStateFlow(PaymentMode.SIMULATOR)
        return vm
    }

    /**
     * Convenience factory that wires up a [SettingsViewModel] with the given prefs store.
     */
    fun buildViewModel(
        store: MutableMap<String, Any?> = mutableMapOf(),
        colorRepo: ColorTokenRepository = buildColorRepo(),
        paymentVm: PaymentViewModel = buildPaymentViewModel()
    ): Pair<SettingsViewModel, MutableMap<String, Any?>> {
        val prefs = buildPrefs(store)
        val vm = SettingsViewModel(colorRepo, prefs, paymentVm)
        return vm to store
    }

    // =========================================================================
    // 1. Surcharge input filtering — Requirements 5.2, 5.3
    // =========================================================================

    test("updateSurcharge with digits only is accepted unchanged for credit") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("credit", "3")
        vm.state.value.creditSurchargePercent shouldBe "3"
    }

    test("updateSurcharge with digits only is accepted unchanged for debit") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("debit", "15")
        vm.state.value.debitSurchargePercent shouldBe "15"
    }

    test("updateSurcharge with a single decimal point is accepted") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("credit", "3.5")
        vm.state.value.creditSurchargePercent shouldBe "3.5"
    }

    test("updateSurcharge strips alphabetic characters") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("credit", "3a5")
        vm.state.value.creditSurchargePercent shouldBe "35"
    }

    test("updateSurcharge strips special characters") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("credit", "3%5")
        vm.state.value.creditSurchargePercent shouldBe "35"
    }

    test("updateSurcharge strips spaces") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("credit", "3 5")
        vm.state.value.creditSurchargePercent shouldBe "35"
    }

    test("updateSurcharge with only non-numeric characters results in empty string") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("credit", "abc!@#")
        vm.state.value.creditSurchargePercent shouldBe ""
    }

    test("updateSurcharge keeps only the first decimal point when multiple are entered") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("credit", "3.5.2")
        vm.state.value.creditSurchargePercent shouldBe "3.52"
    }

    test("updateSurcharge with two decimal points strips the second") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("debit", "1..5")
        vm.state.value.debitSurchargePercent shouldBe "1.5"
    }

    test("updateSurcharge with mixed valid and invalid characters strips invalid ones") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("credit", "2a.b5c")
        vm.state.value.creditSurchargePercent shouldBe "2.5"
    }

    test("updateSurcharge persists filtered value to SharedPreferences") {
        val store = mutableMapOf<String, Any?>()
        val (vm) = buildViewModel(store)
        vm.updateSurcharge("credit", "3a")
        store["credit_surcharge_percent"] shouldBe "3"
    }

    test("updateSurcharge case-insensitive for credit card type") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("CREDIT", "5")
        vm.state.value.creditSurchargePercent shouldBe "5"
    }

    test("updateSurcharge case-insensitive for debit card type") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("DEBIT", "0")
        vm.state.value.debitSurchargePercent shouldBe "0"
    }

    // =========================================================================
    // 2. Tip preset state transitions — Requirements 6.2, 6.3, 6.6
    // =========================================================================

    test("initial state has tipEnabled false") {
        val (vm) = buildViewModel()
        vm.state.value.tipEnabled.shouldBeFalse()
    }

    test("setTipEnabled true sets tipEnabled to true in state") {
        val (vm) = buildViewModel()
        vm.setTipEnabled(true)
        vm.state.value.tipEnabled.shouldBeTrue()
    }

    test("setTipEnabled false sets tipEnabled to false in state") {
        val (vm) = buildViewModel()
        vm.setTipEnabled(true)
        vm.setTipEnabled(false)
        vm.state.value.tipEnabled.shouldBeFalse()
    }

    test("setTipEnabled true persists to SharedPreferences") {
        val store = mutableMapOf<String, Any?>()
        val (vm) = buildViewModel(store)
        vm.setTipEnabled(true)
        store["tip_enabled"] shouldBe true
    }

    test("setTipEnabled false persists to SharedPreferences") {
        val store = mutableMapOf<String, Any?>()
        val (vm) = buildViewModel(store)
        vm.setTipEnabled(true)
        vm.setTipEnabled(false)
        store["tip_enabled"] shouldBe false
    }

    test("updateTipPreset at index 0 updates tipPresets[0]") {
        val (vm) = buildViewModel()
        vm.setTipEnabled(true)
        vm.updateTipPreset(0, "15")
        vm.state.value.tipPresets[0] shouldBe "15"
    }

    test("updateTipPreset at index 1 updates tipPresets[1]") {
        val (vm) = buildViewModel()
        vm.setTipEnabled(true)
        vm.updateTipPreset(1, "18")
        vm.state.value.tipPresets[1] shouldBe "18"
    }

    test("updateTipPreset at index 2 updates tipPresets[2]") {
        val (vm) = buildViewModel()
        vm.setTipEnabled(true)
        vm.updateTipPreset(2, "20")
        vm.state.value.tipPresets[2] shouldBe "20"
    }

    test("updateTipPreset filters non-numeric characters from preset value") {
        val (vm) = buildViewModel()
        vm.setTipEnabled(true)
        vm.updateTipPreset(0, "15%")
        vm.state.value.tipPresets[0] shouldBe "15"
    }

    test("tip presets are empty strings by default") {
        val (vm) = buildViewModel()
        vm.state.value.tipPresets.all { it.isEmpty() }.shouldBeTrue()
    }

    test("tip presets list has exactly 3 entries") {
        val (vm) = buildViewModel()
        vm.state.value.tipPresets.size shouldBe 3
    }

    test("setTipEnabled false does not clear existing preset values from state") {
        // The toggle controls visibility in the UI; the preset values remain stored
        val (vm) = buildViewModel()
        vm.setTipEnabled(true)
        vm.updateTipPreset(0, "15")
        vm.setTipEnabled(false)
        // tipEnabled is false (UI hides presets), but preset data is still in state
        vm.state.value.tipEnabled.shouldBeFalse()
        vm.state.value.tipPresets[0] shouldBe "15"
    }

    test("tip presets are loaded from SharedPreferences on init") {
        val store = mutableMapOf<String, Any?>(
            "tip_enabled"  to true,
            "tip_preset_0" to "15",
            "tip_preset_1" to "18",
            "tip_preset_2" to "20"
        )
        val (vm) = buildViewModel(store)
        vm.state.value.tipEnabled.shouldBeTrue()
        vm.state.value.tipPresets[0] shouldBe "15"
        vm.state.value.tipPresets[1] shouldBe "18"
        vm.state.value.tipPresets[2] shouldBe "20"
    }

    // =========================================================================
    // 3. AVS toggle persistence — Requirements 7.4
    // =========================================================================

    test("initial state has avsEnabled false") {
        val (vm) = buildViewModel()
        vm.state.value.avsEnabled.shouldBeFalse()
    }

    test("setAvsEnabled true sets avsEnabled to true in state") {
        val (vm) = buildViewModel()
        vm.setAvsEnabled(true)
        vm.state.value.avsEnabled.shouldBeTrue()
    }

    test("setAvsEnabled false sets avsEnabled to false in state") {
        val (vm) = buildViewModel()
        vm.setAvsEnabled(true)
        vm.setAvsEnabled(false)
        vm.state.value.avsEnabled.shouldBeFalse()
    }

    test("setAvsEnabled true persists to SharedPreferences") {
        val store = mutableMapOf<String, Any?>()
        val (vm) = buildViewModel(store)
        vm.setAvsEnabled(true)
        store["avs_enabled"] shouldBe true
    }

    test("setAvsEnabled false persists to SharedPreferences") {
        val store = mutableMapOf<String, Any?>()
        val (vm) = buildViewModel(store)
        vm.setAvsEnabled(true)
        vm.setAvsEnabled(false)
        store["avs_enabled"] shouldBe false
    }

    test("AVS toggle state is reloaded correctly from SharedPreferences on init when true") {
        val store = mutableMapOf<String, Any?>("avs_enabled" to true)
        val (vm) = buildViewModel(store)
        vm.state.value.avsEnabled.shouldBeTrue()
    }

    test("AVS toggle state is reloaded correctly from SharedPreferences on init when false") {
        val store = mutableMapOf<String, Any?>("avs_enabled" to false)
        val (vm) = buildViewModel(store)
        vm.state.value.avsEnabled.shouldBeFalse()
    }

    test("AVS toggle defaults to false when not present in SharedPreferences") {
        val (vm) = buildViewModel()
        vm.state.value.avsEnabled.shouldBeFalse()
    }

    test("AVS toggle persists and reloads correctly across ViewModel instances") {
        // Simulate saving in one ViewModel instance and reloading in another
        val store = mutableMapOf<String, Any?>()
        val (vm1) = buildViewModel(store)
        vm1.setAvsEnabled(true)

        // Create a second ViewModel backed by the same store
        val (vm2) = buildViewModel(store)
        vm2.state.value.avsEnabled.shouldBeTrue()
    }

    // =========================================================================
    // 4. saveHexToken with invalid hex — Requirements 6.6 (via 1.5, 8.4)
    // =========================================================================

    test("saveHexToken with invalid hex sets hexErrors[key] to 'Invalid hex color'") {
        val colorRepo = buildColorRepo(validHex = null) // all hex strings invalid
        val (vm) = buildViewModel(colorRepo = colorRepo)

        // Set an invalid hex input for the base token
        vm.updateHexInput(ColorTokenRepository.KEY_BASE, "not-a-hex")
        vm.saveHexToken(ColorTokenRepository.KEY_BASE)

        vm.state.value.hexErrors[ColorTokenRepository.KEY_BASE] shouldBe "Invalid hex color"
    }

    test("saveHexToken with invalid hex does not call saveToken on the repository") {
        val colorRepo = buildColorRepo(validHex = null)
        val (vm) = buildViewModel(colorRepo = colorRepo)

        vm.updateHexInput(ColorTokenRepository.KEY_BASE, "bad-value")
        vm.saveHexToken(ColorTokenRepository.KEY_BASE)

        verify(exactly = 0) { colorRepo.saveToken(any(), any()) }
    }

    test("saveHexToken with invalid hex does not call saveToken for any key") {
        val colorRepo = buildColorRepo(validHex = null)
        val (vm) = buildViewModel(colorRepo = colorRepo)

        // Try saving invalid hex for multiple keys
        listOf(
            ColorTokenRepository.KEY_BASE,
            ColorTokenRepository.KEY_BUTTON1,
            ColorTokenRepository.KEY_BUTTON2
        ).forEach { key ->
            vm.updateHexInput(key, "invalid")
            vm.saveHexToken(key)
        }

        verify(exactly = 0) { colorRepo.saveToken(any(), any()) }
    }

    test("saveHexToken with valid hex clears hexErrors[key]") {
        val validHex = "#FF0000"
        val colorRepo = buildColorRepo(validHex = validHex)
        val (vm) = buildViewModel(colorRepo = colorRepo)

        // First set an error by saving invalid hex
        vm.updateHexInput(ColorTokenRepository.KEY_BASE, "bad")
        vm.saveHexToken(ColorTokenRepository.KEY_BASE)
        vm.state.value.hexErrors[ColorTokenRepository.KEY_BASE] shouldBe "Invalid hex color"

        // Now save a valid hex — error should be cleared
        vm.updateHexInput(ColorTokenRepository.KEY_BASE, validHex)
        vm.saveHexToken(ColorTokenRepository.KEY_BASE)
        vm.state.value.hexErrors[ColorTokenRepository.KEY_BASE] shouldBe null
    }

    test("saveHexToken with valid hex calls saveToken on the repository") {
        val validHex = "#AABBCC"
        val colorRepo = buildColorRepo(validHex = validHex)
        val (vm) = buildViewModel(colorRepo = colorRepo)

        vm.updateHexInput(ColorTokenRepository.KEY_BUTTON1, validHex)
        vm.saveHexToken(ColorTokenRepository.KEY_BUTTON1)

        verify(exactly = 1) { colorRepo.saveToken(ColorTokenRepository.KEY_BUTTON1, validHex) }
    }

    test("saveHexToken with invalid hex does not change hexErrors for other keys") {
        val colorRepo = buildColorRepo(validHex = null)
        val (vm) = buildViewModel(colorRepo = colorRepo)

        vm.updateHexInput(ColorTokenRepository.KEY_BASE, "bad")
        vm.saveHexToken(ColorTokenRepository.KEY_BASE)

        // Other keys should still have null errors
        vm.state.value.hexErrors[ColorTokenRepository.KEY_BUTTON1] shouldBe null
        vm.state.value.hexErrors[ColorTokenRepository.KEY_BUTTON2] shouldBe null
    }

    test("saveHexToken with empty string sets hexErrors[key] to 'Invalid hex color'") {
        val colorRepo = buildColorRepo(validHex = null)
        val (vm) = buildViewModel(colorRepo = colorRepo)

        vm.updateHexInput(ColorTokenRepository.KEY_SPINNER, "")
        vm.saveHexToken(ColorTokenRepository.KEY_SPINNER)

        vm.state.value.hexErrors[ColorTokenRepository.KEY_SPINNER] shouldBe "Invalid hex color"
    }

    test("saveHexToken with hex missing leading hash sets error and does not save") {
        val colorRepo = buildColorRepo(validHex = null) // "AABBCC" without # is invalid
        val (vm) = buildViewModel(colorRepo = colorRepo)

        vm.updateHexInput(ColorTokenRepository.KEY_HOMEBAR, "AABBCC")
        vm.saveHexToken(ColorTokenRepository.KEY_HOMEBAR)

        vm.state.value.hexErrors[ColorTokenRepository.KEY_HOMEBAR] shouldBe "Invalid hex color"
        verify(exactly = 0) { colorRepo.saveToken(any(), any()) }
    }

    // =========================================================================
    // 5. Property 6: Surcharge input rejects non-numeric characters
    //    Validates: Requirements 5.3
    // =========================================================================

    // Feature: payment-ui-redesign, Property 6
    test("Property 6 - updateSurcharge strips non-numeric characters for any input containing them") {
        /**
         * Validates: Requirements 5.3
         *
         * Generator: produces strings that contain at least one character that is
         * neither a digit ('0'..'9') nor a decimal point ('.').  The generator
         * builds a list of characters where at least one is a "dirty" character
         * (letter, symbol, whitespace, etc.) and the rest may be any printable
         * ASCII character.  This guarantees the precondition of the property.
         *
         * Property: after calling updateSurcharge with such a string, the resulting
         * state value must contain only digits and at most one decimal point.
         */

        // Generator for a single non-digit, non-dot character
        val dirtyChar: Arb<Char> = Arb.char(
            ' '..'~'   // printable ASCII range
        ).filter { it != '.' && !it.isDigit() }

        // Generator for a string that contains at least one dirty character
        val inputWithDirtyChar: Arb<String> = arbitrary { rs ->
            val allChars = Arb.char(' '..'~')
            val length = (1..20).random(rs.random)
            val chars = MutableList(length) { allChars.sample(rs).value }
            // Inject at least one dirty character at a random position
            val insertPos = (0 until length).random(rs.random)
            chars[insertPos] = dirtyChar.sample(rs).value
            chars.joinToString("")
        }

        forAll(iterations = 100, inputWithDirtyChar) { input ->
            val (vm) = buildViewModel()
            vm.updateSurcharge("credit", input)
            val result = vm.state.value.creditSurchargePercent

            // All characters in the result must be digits or '.'
            val allCharsValid = result.all { it.isDigit() || it == '.' }

            // At most one decimal point is present
            val dotCount = result.count { it == '.' }
            val atMostOneDot = dotCount <= 1

            allCharsValid && atMostOneDot
        }
    }
})
