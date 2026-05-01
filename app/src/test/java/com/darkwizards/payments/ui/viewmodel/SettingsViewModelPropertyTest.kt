package com.darkwizards.payments.ui.viewmodel

// Feature: payment-ui-redesign, Property 7

import android.content.SharedPreferences
import com.darkwizards.payments.ui.theme.ColorTokenRepository
import com.darkwizards.payments.ui.theme.ColorTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.forAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

/**
 * Property-based tests for [SettingsViewModel] settings persistence round-trip.
 *
 * **Validates: Requirements 5.4, 6.6, 7.4**
 *
 * Property 7: For any combination of surcharge values, tip toggle state, tip presets,
 * and AVS toggle state saved via [SettingsViewModel], reloading the [SettingsViewModel]
 * from the same [SharedPreferences] instance should produce an identical [SettingsState].
 */
class SettingsViewModelPropertyTest : FunSpec({

    // -------------------------------------------------------------------------
    // Helpers — in-memory SharedPreferences mock (same pattern as SettingsViewModelTest)
    // -------------------------------------------------------------------------

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

    fun buildColorRepo(): ColorTokenRepository {
        val repo = mockk<ColorTokenRepository>(relaxed = true)
        every { repo.loadTokens() } returns ColorTokens()
        every { repo.isValidHex(any()) } returns false
        return repo
    }

    fun buildPaymentViewModel(): PaymentViewModel {
        val vm = mockk<PaymentViewModel>(relaxed = true)
        every { vm.selectedMode } returns kotlinx.coroutines.flow.MutableStateFlow(PaymentMode.SIMULATOR)
        return vm
    }

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    /**
     * Generates valid surcharge/tip-preset strings: digits only, or digits with exactly
     * one decimal point. These strings pass through [SettingsViewModel]'s
     * `filterNumericInput` unchanged, so the saved value equals the generated value.
     *
     * Examples: "", "0", "3", "15", "3.5", "15.25", "100.0"
     */
    val validNumericArb: Arb<String> = arbitrary { rs ->
        val random = rs.random
        // 20% chance of empty string
        if (random.nextInt(5) == 0) return@arbitrary ""

        val integerPart = buildString {
            val intLen = random.nextInt(4) // 0–3 digits before decimal
            repeat(intLen) { append(random.nextInt(10)) }
        }

        // 50% chance of adding a decimal part
        val hasDecimal = random.nextBoolean()
        if (!hasDecimal) {
            // Digits only — ensure at least one digit if integerPart is empty
            if (integerPart.isEmpty()) random.nextInt(10).toString() else integerPart
        } else {
            val decimalPart = buildString {
                val decLen = 1 + random.nextInt(3) // 1–3 digits after decimal
                repeat(decLen) { append(random.nextInt(10)) }
            }
            val base = if (integerPart.isEmpty()) "" else integerPart
            "${base}.${decimalPart}"
        }
    }

    // -------------------------------------------------------------------------
    // Property 7 (full round-trip): all persisted settings fields
    // -------------------------------------------------------------------------

    /**
     * For any combination of surcharge values, tip toggle, tip presets, and AVS toggle
     * saved via [SettingsViewModel], reloading from the same [SharedPreferences] instance
     * produces identical [SettingsState] for all persisted fields.
     *
     * **Validates: Requirements 5.4, 6.6, 7.4**
     */
    test("Property 7: full settings round-trip — reloading from same store produces identical state") {
        // Feature: payment-ui-redesign, Property 7
        forAll(
            PropTestConfig(iterations = 100),
            validNumericArb,  // creditSurcharge
            validNumericArb,  // debitSurcharge
            Arb.boolean(),    // tipEnabled
            validNumericArb,  // tipPreset0
            validNumericArb,  // tipPreset1
            validNumericArb,  // tipPreset2
            Arb.boolean()     // avsEnabled
        ) { creditSurcharge, debitSurcharge, tipEnabled, tipPreset0, tipPreset1, tipPreset2, avsEnabled ->

            // Shared in-memory store — both VMs read/write the same backing map
            val store = mutableMapOf<String, Any?>()

            // vm1: save all settings
            val vm1 = SettingsViewModel(buildColorRepo(), buildPrefs(store), buildPaymentViewModel())
            vm1.updateSurcharge("credit", creditSurcharge)
            vm1.updateSurcharge("debit", debitSurcharge)
            vm1.setTipEnabled(tipEnabled)
            vm1.updateTipPreset(0, tipPreset0)
            vm1.updateTipPreset(1, tipPreset1)
            vm1.updateTipPreset(2, tipPreset2)
            vm1.setAvsEnabled(avsEnabled)

            // vm2: reload from the same store (simulates app restart / ViewModel recreation)
            val vm2 = SettingsViewModel(buildColorRepo(), buildPrefs(store), buildPaymentViewModel())
            val s1 = vm1.state.value
            val s2 = vm2.state.value

            // All persisted fields must be identical after reload
            s2.creditSurchargePercent == s1.creditSurchargePercent &&
                s2.debitSurchargePercent == s1.debitSurchargePercent &&
                s2.tipEnabled == s1.tipEnabled &&
                s2.tipPresets[0] == s1.tipPresets[0] &&
                s2.tipPresets[1] == s1.tipPresets[1] &&
                s2.tipPresets[2] == s1.tipPresets[2] &&
                s2.avsEnabled == s1.avsEnabled
        }
    }

    // -------------------------------------------------------------------------
    // Property 7 (focused): surcharge round-trip
    // -------------------------------------------------------------------------

    /**
     * For any valid surcharge string, saving and reloading produces the same value.
     *
     * **Validates: Requirements 5.4**
     */
    test("Property 7: credit surcharge round-trip — saved value is restored on reload") {
        // Feature: payment-ui-redesign, Property 7
        forAll(
            PropTestConfig(iterations = 100),
            validNumericArb
        ) { surcharge ->
            val store = mutableMapOf<String, Any?>()

            val vm1 = SettingsViewModel(buildColorRepo(), buildPrefs(store), buildPaymentViewModel())
            vm1.updateSurcharge("credit", surcharge)

            val vm2 = SettingsViewModel(buildColorRepo(), buildPrefs(store), buildPaymentViewModel())
            vm2.state.value.creditSurchargePercent == vm1.state.value.creditSurchargePercent
        }
    }

    test("Property 7: debit surcharge round-trip — saved value is restored on reload") {
        // Feature: payment-ui-redesign, Property 7
        forAll(
            PropTestConfig(iterations = 100),
            validNumericArb
        ) { surcharge ->
            val store = mutableMapOf<String, Any?>()

            val vm1 = SettingsViewModel(buildColorRepo(), buildPrefs(store), buildPaymentViewModel())
            vm1.updateSurcharge("debit", surcharge)

            val vm2 = SettingsViewModel(buildColorRepo(), buildPrefs(store), buildPaymentViewModel())
            vm2.state.value.debitSurchargePercent == vm1.state.value.debitSurchargePercent
        }
    }

    // -------------------------------------------------------------------------
    // Property 7 (focused): tip toggle round-trip
    // -------------------------------------------------------------------------

    /**
     * For any boolean tip toggle value, saving and reloading produces the same value.
     *
     * **Validates: Requirements 6.6**
     */
    test("Property 7: tip toggle round-trip — saved boolean is restored on reload") {
        // Feature: payment-ui-redesign, Property 7
        forAll(
            PropTestConfig(iterations = 100),
            Arb.boolean()
        ) { tipEnabled ->
            val store = mutableMapOf<String, Any?>()

            val vm1 = SettingsViewModel(buildColorRepo(), buildPrefs(store), buildPaymentViewModel())
            vm1.setTipEnabled(tipEnabled)

            val vm2 = SettingsViewModel(buildColorRepo(), buildPrefs(store), buildPaymentViewModel())
            vm2.state.value.tipEnabled == vm1.state.value.tipEnabled
        }
    }

    // -------------------------------------------------------------------------
    // Property 7 (focused): AVS toggle round-trip
    // -------------------------------------------------------------------------

    /**
     * For any boolean AVS toggle value, saving and reloading produces the same value.
     *
     * **Validates: Requirements 7.4**
     */
    test("Property 7: AVS toggle round-trip — saved boolean is restored on reload") {
        // Feature: payment-ui-redesign, Property 7
        forAll(
            PropTestConfig(iterations = 100),
            Arb.boolean()
        ) { avsEnabled ->
            val store = mutableMapOf<String, Any?>()

            val vm1 = SettingsViewModel(buildColorRepo(), buildPrefs(store), buildPaymentViewModel())
            vm1.setAvsEnabled(avsEnabled)

            val vm2 = SettingsViewModel(buildColorRepo(), buildPrefs(store), buildPaymentViewModel())
            vm2.state.value.avsEnabled == vm1.state.value.avsEnabled
        }
    }

    // -------------------------------------------------------------------------
    // Property 7 (focused): tip presets round-trip
    // -------------------------------------------------------------------------

    /**
     * For any combination of tip preset strings, saving and reloading produces the same values.
     *
     * **Validates: Requirements 6.6**
     */
    test("Property 7: tip presets round-trip — all three preset values are restored on reload") {
        // Feature: payment-ui-redesign, Property 7
        forAll(
            PropTestConfig(iterations = 100),
            validNumericArb,  // preset 0
            validNumericArb,  // preset 1
            validNumericArb   // preset 2
        ) { preset0, preset1, preset2 ->
            val store = mutableMapOf<String, Any?>()

            val vm1 = SettingsViewModel(buildColorRepo(), buildPrefs(store), buildPaymentViewModel())
            vm1.updateTipPreset(0, preset0)
            vm1.updateTipPreset(1, preset1)
            vm1.updateTipPreset(2, preset2)

            val vm2 = SettingsViewModel(buildColorRepo(), buildPrefs(store), buildPaymentViewModel())
            val s1 = vm1.state.value
            val s2 = vm2.state.value

            s2.tipPresets[0] == s1.tipPresets[0] &&
                s2.tipPresets[1] == s1.tipPresets[1] &&
                s2.tipPresets[2] == s1.tipPresets[2]
        }
    }
})
