package com.darkwizards.payments.ui.viewmodel

import android.content.SharedPreferences
import com.darkwizards.payments.ui.theme.ColorTokenRepository
import com.darkwizards.payments.ui.theme.ColorTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Integration tests for [SettingsViewModel] covering all five Settings screen sections.
 *
 * These are pure JVM unit tests — no Android device or Robolectric required.
 * SharedPreferences and ColorTokenRepository are mocked with MockK.
 *
 * Task 21.4 — Requirements: 5.1, 6.1, 7.1, 8.1, 9.1–9.4
 */
class SettingsScreenIntegrationTest : FunSpec({

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds a MockK [SharedPreferences] backed by [store].
     * Supports getString, getBoolean, edit().putString(), edit().putBoolean(), apply().
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
     * Builds a [ColorTokenRepository] mock that returns default [ColorTokens] and
     * validates hex strings using the real pattern (#RRGGBB or #RGB).
     */
    fun buildColorRepo(
        validHex: String? = null,
        defaultTokens: ColorTokens = ColorTokens()
    ): ColorTokenRepository {
        val repo = mockk<ColorTokenRepository>(relaxed = true)
        every { repo.loadTokens() } returns defaultTokens
        every { repo.isValidHex(any()) } answers {
            val input = firstArg<String>()
            if (validHex != null) input == validHex
            else Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$").matches(input)
        }
        return repo
    }

    /**
     * Builds a mock [PaymentViewModel] with a stable [selectedMode] StateFlow.
     */
    fun buildPaymentViewModel(
        initialMode: PaymentMode = PaymentMode.SIMULATOR
    ): PaymentViewModel {
        val vm = mockk<PaymentViewModel>(relaxed = true)
        val modeFlow = MutableStateFlow(initialMode)
        every { vm.selectedMode } returns modeFlow
        every { vm.selectMode(any()) } answers {
            val mode = firstArg<PaymentMode>()
            if (mode != PaymentMode.LIVE) modeFlow.value = mode
        }
        return vm
    }

    /**
     * Convenience factory for [SettingsViewModel].
     */
    fun buildViewModel(
        store: MutableMap<String, Any?> = mutableMapOf(),
        colorRepo: ColorTokenRepository = buildColorRepo(),
        paymentVm: PaymentViewModel = buildPaymentViewModel()
    ): Triple<SettingsViewModel, MutableMap<String, Any?>, PaymentViewModel> {
        val prefs = buildPrefs(store)
        val vm = SettingsViewModel(colorRepo, prefs, paymentVm)
        return Triple(vm, store, paymentVm)
    }

    // =========================================================================
    // Section 1: Surcharges — Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6
    // =========================================================================

    test("Section 1 - Surcharges: initial state has empty surcharge fields") {
        val (vm) = buildViewModel()
        vm.state.value.creditSurchargePercent shouldBe ""
        vm.state.value.debitSurchargePercent shouldBe ""
    }

    test("Section 1 - Surcharges: credit surcharge field accepts numeric input") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("credit", "3.5")
        vm.state.value.creditSurchargePercent shouldBe "3.5"
    }

    test("Section 1 - Surcharges: debit surcharge field accepts numeric input") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("debit", "1.5")
        vm.state.value.debitSurchargePercent shouldBe "1.5"
    }

    test("Section 1 - Surcharges: non-numeric characters are rejected from credit surcharge") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("credit", "3%")
        vm.state.value.creditSurchargePercent shouldBe "3"
    }

    test("Section 1 - Surcharges: non-numeric characters are rejected from debit surcharge") {
        val (vm) = buildViewModel()
        vm.updateSurcharge("debit", "1abc")
        vm.state.value.debitSurchargePercent shouldBe "1"
    }

    test("Section 1 - Surcharges: surcharge values are persisted to SharedPreferences") {
        val store = mutableMapOf<String, Any?>()
        val (vm) = buildViewModel(store)
        vm.updateSurcharge("credit", "3")
        vm.updateSurcharge("debit", "0")
        store["credit_surcharge_percent"] shouldBe "3"
        store["debit_surcharge_percent"] shouldBe "0"
    }

    test("Section 1 - Surcharges: surcharge values are loaded from SharedPreferences on init") {
        val store = mutableMapOf<String, Any?>(
            "credit_surcharge_percent" to "3.5",
            "debit_surcharge_percent"  to "1.0"
        )
        val (vm) = buildViewModel(store)
        vm.state.value.creditSurchargePercent shouldBe "3.5"
        vm.state.value.debitSurchargePercent shouldBe "1.0"
    }

    // =========================================================================
    // Section 2: Tip Enablement — Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6
    // =========================================================================

    test("Section 2 - Tip Enablement: initial state has tip disabled") {
        val (vm) = buildViewModel()
        vm.state.value.tipEnabled.shouldBeFalse()
    }

    test("Section 2 - Tip Enablement: tip toggle can be enabled") {
        val (vm) = buildViewModel()
        vm.setTipEnabled(true)
        vm.state.value.tipEnabled.shouldBeTrue()
    }

    test("Section 2 - Tip Enablement: tip toggle can be disabled after being enabled") {
        val (vm) = buildViewModel()
        vm.setTipEnabled(true)
        vm.setTipEnabled(false)
        vm.state.value.tipEnabled.shouldBeFalse()
    }

    test("Section 2 - Tip Enablement: initial state has three empty tip preset slots") {
        val (vm) = buildViewModel()
        vm.state.value.tipPresets.size shouldBe 3
        vm.state.value.tipPresets.all { it.isEmpty() }.shouldBeTrue()
    }

    test("Section 2 - Tip Enablement: tip presets can be set when tip is enabled") {
        val (vm) = buildViewModel()
        vm.setTipEnabled(true)
        vm.updateTipPreset(0, "15")
        vm.updateTipPreset(1, "18")
        vm.updateTipPreset(2, "20")
        vm.state.value.tipPresets[0] shouldBe "15"
        vm.state.value.tipPresets[1] shouldBe "18"
        vm.state.value.tipPresets[2] shouldBe "20"
    }

    test("Section 2 - Tip Enablement: tip configuration is persisted to SharedPreferences") {
        val store = mutableMapOf<String, Any?>()
        val (vm) = buildViewModel(store)
        vm.setTipEnabled(true)
        vm.updateTipPreset(0, "15")
        store["tip_enabled"] shouldBe true
        store["tip_preset_0"] shouldBe "15"
    }

    test("Section 2 - Tip Enablement: tip configuration is loaded from SharedPreferences on init") {
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
    // Section 3: AVS Toggle — Requirements 7.1, 7.2, 7.3, 7.4, 7.5
    // =========================================================================

    test("Section 3 - AVS Toggle: initial state has AVS disabled") {
        val (vm) = buildViewModel()
        vm.state.value.avsEnabled.shouldBeFalse()
    }

    test("Section 3 - AVS Toggle: AVS toggle can be enabled") {
        val (vm) = buildViewModel()
        vm.setAvsEnabled(true)
        vm.state.value.avsEnabled.shouldBeTrue()
    }

    test("Section 3 - AVS Toggle: AVS toggle can be disabled after being enabled") {
        val (vm) = buildViewModel()
        vm.setAvsEnabled(true)
        vm.setAvsEnabled(false)
        vm.state.value.avsEnabled.shouldBeFalse()
    }

    test("Section 3 - AVS Toggle: AVS state is persisted to SharedPreferences") {
        val store = mutableMapOf<String, Any?>()
        val (vm) = buildViewModel(store)
        vm.setAvsEnabled(true)
        store["avs_enabled"] shouldBe true
    }

    test("Section 3 - AVS Toggle: AVS state is loaded from SharedPreferences on init") {
        val store = mutableMapOf<String, Any?>("avs_enabled" to true)
        val (vm) = buildViewModel(store)
        vm.state.value.avsEnabled.shouldBeTrue()
    }

    // =========================================================================
    // Section 4: Branding Colors — Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6
    // =========================================================================

    test("Section 4 - Branding Colors: initial state has hex inputs for all 8 token keys") {
        val (vm) = buildViewModel()
        val hexInputs = vm.state.value.hexInputs
        SettingsViewModel.TOKEN_KEYS.forEach { key ->
            hexInputs shouldContainKey key
        }
    }

    test("Section 4 - Branding Colors: initial state has 8 token keys") {
        val (vm) = buildViewModel()
        vm.state.value.hexInputs.size shouldBe 8
    }

    test("Section 4 - Branding Colors: initial hex inputs are non-empty strings") {
        val (vm) = buildViewModel()
        vm.state.value.hexInputs.values.forEach { hexValue ->
            hexValue.isNotEmpty().shouldBeTrue()
        }
    }

    test("Section 4 - Branding Colors: initial hex errors are all null") {
        val (vm) = buildViewModel()
        vm.state.value.hexErrors.values.forEach { error ->
            error.shouldBeNull()
        }
    }

    test("Section 4 - Branding Colors: updateHexInput updates the hex input for a token key") {
        val (vm) = buildViewModel()
        vm.updateHexInput(ColorTokenRepository.KEY_BASE, "#FF0000")
        vm.state.value.hexInputs[ColorTokenRepository.KEY_BASE] shouldBe "#FF0000"
    }

    test("Section 4 - Branding Colors: saveHexToken with valid hex clears the error for that key") {
        val validHex = "#FF0000"
        val colorRepo = buildColorRepo(validHex = validHex)
        val (vm) = buildViewModel(colorRepo = colorRepo)

        // First set an error
        vm.updateHexInput(ColorTokenRepository.KEY_BASE, "bad")
        vm.saveHexToken(ColorTokenRepository.KEY_BASE)
        vm.state.value.hexErrors[ColorTokenRepository.KEY_BASE] shouldBe "Invalid hex color"

        // Now save valid hex — error should clear
        vm.updateHexInput(ColorTokenRepository.KEY_BASE, validHex)
        vm.saveHexToken(ColorTokenRepository.KEY_BASE)
        vm.state.value.hexErrors[ColorTokenRepository.KEY_BASE].shouldBeNull()
    }

    test("Section 4 - Branding Colors: saveHexToken with invalid hex sets error message") {
        val colorRepo = buildColorRepo(validHex = null)
        val (vm) = buildViewModel(colorRepo = colorRepo)

        vm.updateHexInput(ColorTokenRepository.KEY_BUTTON1, "not-a-hex")
        vm.saveHexToken(ColorTokenRepository.KEY_BUTTON1)

        vm.state.value.hexErrors[ColorTokenRepository.KEY_BUTTON1] shouldBe "Invalid hex color"
    }

    test("Section 4 - Branding Colors: saveHexToken with invalid hex does not call saveToken") {
        val colorRepo = buildColorRepo(validHex = null)
        val (vm) = buildViewModel(colorRepo = colorRepo)

        vm.updateHexInput(ColorTokenRepository.KEY_BASE, "invalid")
        vm.saveHexToken(ColorTokenRepository.KEY_BASE)

        verify(exactly = 0) { colorRepo.saveToken(any(), any()) }
    }

    test("Section 4 - Branding Colors: saveHexToken with valid hex calls saveToken on repository") {
        val validHex = "#AABBCC"
        val colorRepo = buildColorRepo(validHex = validHex)
        val (vm) = buildViewModel(colorRepo = colorRepo)

        vm.updateHexInput(ColorTokenRepository.KEY_HOMEBAR, validHex)
        vm.saveHexToken(ColorTokenRepository.KEY_HOMEBAR)

        verify(exactly = 1) { colorRepo.saveToken(ColorTokenRepository.KEY_HOMEBAR, validHex) }
    }

    /**
     * Verify "Reset to Defaults" restores default hex values in the branding fields.
     * Requirements: 1.7, 8.6
     */
    test("Section 4 - Branding Colors: resetBrandingToDefaults restores default hex values") {
        val defaultTokens = ColorTokens()
        val colorRepo = buildColorRepo(defaultTokens = defaultTokens)
        val (vm) = buildViewModel(colorRepo = colorRepo)

        // Simulate user changing a hex input
        vm.updateHexInput(ColorTokenRepository.KEY_BASE, "#123456")
        vm.state.value.hexInputs[ColorTokenRepository.KEY_BASE] shouldBe "#123456"

        // Reset to defaults
        vm.resetBrandingToDefaults()

        // After reset, colorTokens should be the defaults
        vm.state.value.colorTokens shouldBe defaultTokens
    }

    test("Section 4 - Branding Colors: resetBrandingToDefaults clears all hex errors") {
        val colorRepo = buildColorRepo(validHex = null)
        val (vm) = buildViewModel(colorRepo = colorRepo)

        // Set errors on multiple keys
        listOf(ColorTokenRepository.KEY_BASE, ColorTokenRepository.KEY_BUTTON1).forEach { key ->
            vm.updateHexInput(key, "bad")
            vm.saveHexToken(key)
        }

        // Verify errors are set
        vm.state.value.hexErrors[ColorTokenRepository.KEY_BASE] shouldBe "Invalid hex color"

        // Reset to defaults
        vm.resetBrandingToDefaults()

        // All errors should be cleared
        vm.state.value.hexErrors.values.forEach { error ->
            error.shouldBeNull()
        }
    }

    test("Section 4 - Branding Colors: resetBrandingToDefaults calls resetToDefaults on repository") {
        val colorRepo = buildColorRepo()
        val (vm) = buildViewModel(colorRepo = colorRepo)

        vm.resetBrandingToDefaults()

        verify(exactly = 1) { colorRepo.resetToDefaults() }
    }

    // =========================================================================
    // Section 5: Payment Mode — Requirements 9.1, 9.2, 9.3, 9.4
    // =========================================================================

    test("Section 5 - Payment Mode: initial selected mode is SIMULATOR") {
        val (vm) = buildViewModel()
        vm.state.value.selectedMode shouldBe PaymentMode.SIMULATOR
    }

    test("Section 5 - Payment Mode: selectMode with SIMULATOR calls PaymentViewModel.selectMode") {
        val (vm, _, paymentVm) = buildViewModel()
        vm.selectMode(PaymentMode.SIMULATOR)
        verify { paymentVm.selectMode(PaymentMode.SIMULATOR) }
    }

    test("Section 5 - Payment Mode: selectMode with MOCK calls PaymentViewModel.selectMode with MOCK") {
        val (vm, _, paymentVm) = buildViewModel()
        vm.selectMode(PaymentMode.MOCK)
        verify { paymentVm.selectMode(PaymentMode.MOCK) }
    }

    test("Section 5 - Payment Mode: selectMode updates selectedMode in state to MOCK") {
        val (vm) = buildViewModel()
        vm.selectMode(PaymentMode.MOCK)
        vm.state.value.selectedMode shouldBe PaymentMode.MOCK
    }

    test("Section 5 - Payment Mode: selectMode updates selectedMode in state to SIMULATOR") {
        val (vm) = buildViewModel()
        vm.selectMode(PaymentMode.MOCK)
        vm.selectMode(PaymentMode.SIMULATOR)
        vm.state.value.selectedMode shouldBe PaymentMode.SIMULATOR
    }

    test("Section 5 - Payment Mode: selectMode with LIVE calls PaymentViewModel.selectMode with LIVE") {
        // PaymentViewModel.selectMode ignores LIVE (no-op), but SettingsViewModel still delegates
        val (vm, _, paymentVm) = buildViewModel()
        vm.selectMode(PaymentMode.LIVE)
        verify { paymentVm.selectMode(PaymentMode.LIVE) }
    }

    test("Section 5 - Payment Mode: all PaymentMode entries are available for selection") {
        // Verify all three modes exist (Tony MCP / Simulator, Sandbox / Mock, Live)
        val modes = PaymentMode.entries
        modes.size shouldBe 3
        modes.map { it.key } shouldBe listOf("simulator", "mock", "live")
    }

    test("Section 5 - Payment Mode: LIVE mode has 'Coming soon' label") {
        PaymentMode.LIVE.label shouldBe "Live (Coming soon)"
    }

    test("Section 5 - Payment Mode: SIMULATOR mode has correct label") {
        PaymentMode.SIMULATOR.label shouldBe "Tony MCP"
    }

    test("Section 5 - Payment Mode: MOCK mode has correct label") {
        PaymentMode.MOCK.label shouldBe "Sandbox"
    }

    test("Section 5 - Payment Mode: initial mode reflects PaymentViewModel selectedMode") {
        val paymentVm = buildPaymentViewModel(initialMode = PaymentMode.MOCK)
        val prefs = buildPrefs()
        val colorRepo = buildColorRepo()
        val vm = SettingsViewModel(colorRepo, prefs, paymentVm)
        vm.state.value.selectedMode shouldBe PaymentMode.MOCK
    }

    // =========================================================================
    // Cross-section: All five sections are initialized in SettingsState
    // =========================================================================

    test("All five sections are represented in the initial SettingsState") {
        val (vm) = buildViewModel()
        val state = vm.state.value

        // Section 1: Surcharges
        state.creditSurchargePercent shouldNotBe null
        state.debitSurchargePercent shouldNotBe null

        // Section 2: Tip Enablement
        state.tipEnabled shouldNotBe null
        state.tipPresets shouldNotBe null

        // Section 3: AVS Toggle
        state.avsEnabled shouldNotBe null

        // Section 4: Branding Colors
        state.colorTokens shouldNotBe null
        state.hexInputs shouldNotBe null
        state.hexErrors shouldNotBe null

        // Section 5: Payment Mode
        state.selectedMode shouldNotBe null
    }
})
