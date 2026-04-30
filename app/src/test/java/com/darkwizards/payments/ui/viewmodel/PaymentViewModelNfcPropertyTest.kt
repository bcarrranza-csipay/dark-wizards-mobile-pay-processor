package com.darkwizards.payments.ui.viewmodel

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.darkwizards.payments.data.TransactionStore
import com.darkwizards.payments.data.model.CvmResult
import com.darkwizards.payments.data.model.EmvCardData
import com.darkwizards.payments.data.model.HistoricalTransaction
import com.darkwizards.payments.data.model.ModeResponse
import com.darkwizards.payments.data.model.PaymentUiState
import com.darkwizards.payments.data.model.RefundResponse
import com.darkwizards.payments.data.model.SaleResponse
import com.darkwizards.payments.data.model.SettleResponse
import com.darkwizards.payments.data.model.TransactionDetail
import com.darkwizards.payments.data.service.PaymentService
import com.darkwizards.payments.domain.EmvKernel
import com.darkwizards.payments.data.model.PaymentType
import com.darkwizards.payments.util.emvCardData
import com.darkwizards.payments.util.emvFailureMode
import com.darkwizards.payments.util.pan
import com.darkwizards.payments.util.positiveAmount
import com.darkwizards.payments.util.saleResponse
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalKotest::class)
class PaymentViewModelNfcPropertyTest : FunSpec({
    val testDispatcher = StandardTestDispatcher()

    beforeEach {
        Dispatchers.setMain(testDispatcher)
        mockkObject(EmvKernel)
        mockkStatic(NfcAdapter::class)
    }

    afterEach {
        Dispatchers.resetMain()
        unmockkAll()
    }

    test("Property 4: PAN passed through unchanged — accountNumber in processSale equals EMV PAN") {
        // Feature: nfc-tap-to-pay, Property 4: PAN passthrough
        // Validates: Requirements 4.2, 4.3
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.pan()
        ) { pan ->
            val capturingService = CapturingPaymentService()
            val store = TransactionStore()
            val isoDep = mockk<IsoDep>()
            val tag = mockk<Tag>()

            val cardData = EmvCardData(
                pan = pan,
                expiry = "12.2026",
                accountType = "Visa",
                track2Equivalent = byteArrayOf(),
                applicationCryptogram = ByteArray(8),
                cryptogramInfoData = 0x80.toByte(),
                aip = byteArrayOf(0x00, 0x00),
                cvmResult = CvmResult.NO_CVM,
                cdcvmPerformed = false
            )

            coEvery { EmvKernel.readCard(isoDep) } returns Result.success(cardData)

            val vm = PaymentViewModel(capturingService, store, { isoDep }, testDispatcher)
            testDispatcher.scheduler.advanceUntilIdle() // wait for token init

            vm.onNfcTagDiscovered(tag)
            testDispatcher.scheduler.advanceUntilIdle()

            // The accountNumber passed to processSale must equal the PAN from EmvCardData
            capturingService.capturedAccountNumber shouldBe pan
        }
    }

    test("Property 4 (ONLINE_PIN path): PAN passed through unchanged via submitPin") {
        // Feature: nfc-tap-to-pay, Property 4: PAN passthrough (ONLINE_PIN path)
        // Validates: Requirements 4.2, 4.3
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.pan()
        ) { pan ->
            val capturingService = CapturingPaymentService()
            val store = TransactionStore()
            val isoDep = mockk<IsoDep>()
            val tag = mockk<Tag>()

            val cardData = EmvCardData(
                pan = pan,
                expiry = "12.2026",
                accountType = "Visa",
                track2Equivalent = byteArrayOf(),
                applicationCryptogram = ByteArray(8),
                cryptogramInfoData = 0x80.toByte(),
                aip = byteArrayOf(0x00, 0x00),
                cvmResult = CvmResult.ONLINE_PIN,
                cdcvmPerformed = false
            )

            coEvery { EmvKernel.readCard(isoDep) } returns Result.success(cardData)

            val vm = PaymentViewModel(capturingService, store, { isoDep }, testDispatcher)
            testDispatcher.scheduler.advanceUntilIdle()

            vm.onNfcTagDiscovered(tag)
            testDispatcher.scheduler.advanceUntilIdle()

            // Should be in NfcCvmRequired(ONLINE_PIN) state
            vm.uiState.value.shouldBeInstanceOf<PaymentUiState.NfcCvmRequired>()

            // Submit PIN — this triggers processSale with cardData.pan
            vm.submitPin("1234")
            testDispatcher.scheduler.advanceUntilIdle()

            capturingService.capturedAccountNumber shouldBe pan
        }
    }

    // Feature: nfc-tap-to-pay, Property 7: No amount-based declines
    // Validates: Requirements 5.6, 7.1, 7.2, 7.4
    test("Property 7: No amount-based declines — NFC terminal never transitions to NfcError based solely on amount") {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.positiveAmount()
        ) { amount ->
            val service = CapturingPaymentService()
            val store = TransactionStore()
            val isoDep = mockk<IsoDep>()
            val tag = mockk<Tag>()
            val context = mockk<Context>()
            val adapter = mockk<NfcAdapter>()

            // Mock NFC adapter to return enabled state
            every { NfcAdapter.getDefaultAdapter(context) } returns adapter
            every { adapter.isEnabled } returns true

            // Mock EmvKernel to return a successful card read regardless of amount
            val cardData = EmvCardData(
                pan = "4111111111111111",
                expiry = "12.2026",
                accountType = "Visa",
                track2Equivalent = byteArrayOf(),
                applicationCryptogram = ByteArray(8),
                cryptogramInfoData = 0x80.toByte(),
                aip = byteArrayOf(0x00, 0x00),
                cvmResult = CvmResult.NO_CVM,
                cdcvmPerformed = false
            )
            coEvery { EmvKernel.readCard(isoDep) } returns Result.success(cardData)

            // Mock PaymentService to return success regardless of amount
            service.saleResult = Result.success(
                SaleResponse("txn-001", "5000", "150", "AP001", "Visa", "411111", "1111")
            )

            val vm = PaymentViewModel(service, store, { isoDep }, testDispatcher)
            testDispatcher.scheduler.advanceUntilIdle() // wait for token init

            // Set the pending amount — this is the only variable across iterations
            vm.submitCardPresent(amount)

            // Activate NFC (transitions to NfcWaiting)
            vm.checkNfcAvailability(context)

            // Simulate a card tap
            vm.onNfcTagDiscovered(tag)
            testDispatcher.scheduler.advanceUntilIdle()

            // The terminal must NEVER transition to NfcError based solely on the amount.
            // With a successful EmvKernel and successful PaymentService, the only valid
            // terminal states are NfcSubmitting or Success (NO_CVM path goes directly to Success).
            vm.uiState.value.shouldNotBeInstanceOf<PaymentUiState.NfcError>()
        }
    }

    // Feature: nfc-tap-to-pay, Property 8: EMV→processSale mapping
    // Validates: Requirements 6.1
    test("Property 8: EMV card data correctly mapped to processSale fields") {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.emvCardData(),
            Arb.positiveAmount()
        ) { cardData, amount ->
            val capturingService = CapturingPaymentService()
            val store = TransactionStore()
            val isoDep = mockk<IsoDep>()
            val tag = mockk<Tag>()

            coEvery { EmvKernel.readCard(isoDep) } returns Result.success(cardData)

            val vm = PaymentViewModel(capturingService, store, { isoDep }, testDispatcher)
            testDispatcher.scheduler.advanceUntilIdle() // wait for token init

            // Set the merchant-entered amount before the tap.
            // submitCardPresent sets pendingAmountDollars; onNfcTagDiscovered will
            // overwrite pan/expiry/accountType from the EMV card data but preserves
            // the merchant-entered amount.
            vm.submitCardPresent(amount)

            // Simulate a card tap — triggers EmvKernel.readCard then processSale
            vm.onNfcTagDiscovered(tag)
            testDispatcher.scheduler.advanceUntilIdle()

            // Verify all three processSale fields are correctly mapped from EmvCardData:
            //   accountNumber == emvCardData.pan
            //   accountAccessory (expiry) == emvCardData.expiry (already in MM.YYYY format)
            //   totalAmountDollars == merchant-entered amount
            capturingService.capturedAccountNumber shouldBe cardData.pan
            capturingService.capturedExpiry shouldBe cardData.expiry
            capturingService.capturedTotalAmountDollars shouldBe amount
        }
    }

    // Feature: nfc-tap-to-pay, Property 9: CARD_PRESENT paymentType
    // Validates: Requirements 6.5, 10.2
    test("Property 9: Successful NFC transaction stored with CARD_PRESENT payment type") {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.saleResponse()
        ) { saleResponse ->
            val capturingService = CapturingPaymentService()
            val store = TransactionStore()
            val isoDep = mockk<IsoDep>()
            val tag = mockk<Tag>()

            // Configure the service to return the generated SaleResponse on success
            capturingService.saleResult = Result.success(saleResponse)

            // Use a NO_CVM card so the flow goes directly to submission without CVM prompts
            val cardData = EmvCardData(
                pan = "4111111111111111",
                expiry = "12.2026",
                accountType = "Visa",
                track2Equivalent = byteArrayOf(),
                applicationCryptogram = ByteArray(8),
                cryptogramInfoData = 0x80.toByte(),
                aip = byteArrayOf(0x00, 0x00),
                cvmResult = CvmResult.NO_CVM,
                cdcvmPerformed = false
            )

            coEvery { EmvKernel.readCard(isoDep) } returns Result.success(cardData)

            val vm = PaymentViewModel(capturingService, store, { isoDep }, testDispatcher)
            testDispatcher.scheduler.advanceUntilIdle() // wait for token init

            // Simulate a card tap — triggers EmvKernel.readCard then processSale
            vm.onNfcTagDiscovered(tag)
            testDispatcher.scheduler.advanceUntilIdle()

            // The UI state must be Success after a successful authorization
            vm.uiState.value.shouldBeInstanceOf<PaymentUiState.Success>()

            // The TransactionStore must contain exactly one record (from this NFC transaction)
            // with paymentType == CARD_PRESENT, regardless of the SaleResponse contents
            val transactions = store.transactions.value
            transactions.size shouldBe 1
            transactions.first().paymentType shouldBe PaymentType.CARD_PRESENT
        }
    }

    // Feature: nfc-tap-to-pay, Property 10: EMV failure prevents sale
    // Validates: Requirements 6.6
    test("Property 10: EMV failure prevents processSale call — processSale is never called when EMV flow fails") {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.emvFailureMode()
        ) { failure ->
            val paymentService = mockk<PaymentService>(relaxed = true)
            val store = TransactionStore()
            val isoDep = mockk<IsoDep>()
            val tag = mockk<Tag>()

            // Mock EmvKernel.readCard to return a failure Result for this failure mode
            coEvery { EmvKernel.readCard(isoDep) } returns Result.failure(failure)

            val vm = PaymentViewModel(paymentService, store, { isoDep }, testDispatcher)
            testDispatcher.scheduler.advanceUntilIdle() // wait for token init

            // Simulate a card tap — EmvKernel.readCard will fail
            vm.onNfcTagDiscovered(tag)
            testDispatcher.scheduler.advanceUntilIdle()

            // The UI state must be NfcError (EMV failure → error state, not submission)
            vm.uiState.value.shouldBeInstanceOf<PaymentUiState.NfcError>()

            // processSale must NEVER be called when the EMV flow fails
            coVerify(exactly = 0) {
                paymentService.processSale(any(), any(), any(), any())
            }
        }
    }
})

/** Capturing fake PaymentService that records the accountNumber passed to processSale */
private class CapturingPaymentService : PaymentService {
    var capturedAccountNumber: String? = null
    var capturedExpiry: String? = null
    var capturedTotalAmountDollars: String? = null
    var tokenResult: Result<String> = Result.success("test-token")
    var saleResult: Result<SaleResponse> = Result.success(
        SaleResponse("txn-001", "5000", "150", "AP001", "Visa", "411111", "1111")
    )

    override suspend fun getToken(): Result<String> = tokenResult

    override suspend fun processSale(
        accountNumber: String,
        accountType: String,
        expiry: String,
        totalAmountDollars: String
    ): Result<SaleResponse> {
        capturedAccountNumber = accountNumber
        capturedExpiry = expiry
        capturedTotalAmountDollars = totalAmountDollars
        return saleResult
    }

    override suspend fun processRefund(transactionId: String): Result<RefundResponse> =
        Result.failure(Exception("not implemented"))

    override suspend fun settleTransactions(): Result<SettleResponse> =
        Result.failure(Exception("not implemented"))

    override suspend fun getTransaction(transactionId: String): Result<TransactionDetail> =
        Result.failure(Exception("not implemented"))

    override suspend fun getMode(): Result<ModeResponse> =
        Result.failure(Exception("not implemented"))

    override suspend fun getAllTransactions(terminalId: String?): Result<List<HistoricalTransaction>> =
        Result.success(emptyList())
}
