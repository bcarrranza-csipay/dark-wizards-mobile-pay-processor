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
import com.darkwizards.payments.data.model.NfcAvailability
import com.darkwizards.payments.data.model.PaymentType
import com.darkwizards.payments.data.model.PaymentUiState
import com.darkwizards.payments.data.model.RefundResponse
import com.darkwizards.payments.data.model.SaleResponse
import com.darkwizards.payments.data.model.SettleResponse
import com.darkwizards.payments.data.model.TransactionDetail
import com.darkwizards.payments.data.service.PaymentService
import com.darkwizards.payments.domain.EmvKernel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
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

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelNfcTest : FunSpec({

    val testDispatcher = StandardTestDispatcher()

    lateinit var fakeService: FakeNfcPaymentService
    lateinit var store: TransactionStore

    // Reusable card data helpers
    fun makeCardData(cvmResult: CvmResult) = EmvCardData(
        pan = "4111111111111111",
        expiry = "12.2026",
        accountType = "Visa",
        track2Equivalent = byteArrayOf(0x41, 0x11),
        applicationCryptogram = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08),
        cryptogramInfoData = 0x80.toByte(),
        aip = byteArrayOf(0x00, 0x00),
        cvmResult = cvmResult,
        cdcvmPerformed = cvmResult == CvmResult.CDCVM
    )

    fun makeSaleResponse(txId: String = "txn-nfc-001") = SaleResponse(
        transactionId = txId,
        approvedAmount = "5000",
        feeAmount = "150",
        approvalNumber = "AP999",
        accountType = "Visa",
        accountFirst6 = "411111",
        accountLast4 = "1111"
    )

    beforeEach {
        Dispatchers.setMain(testDispatcher)
        fakeService = FakeNfcPaymentService()
        store = TransactionStore()
        mockkObject(EmvKernel)
        mockkStatic(NfcAdapter::class)
    }

    afterEach {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // Creates a ViewModel with an injectable IsoDep factory for testability
    fun createViewModel(isoDepFactory: (Tag) -> IsoDep? = { null }): PaymentViewModel {
        fakeService.tokenResult = Result.success("test-token")
        val vm = PaymentViewModel(fakeService, store, isoDepFactory, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    // ── Test 1: checkNfcAvailability with null adapter → NfcHardwareUnavailable(NoHardware) ──

    test("checkNfcAvailability with null adapter emits NfcHardwareUnavailable(NoHardware)") {
        val vm = createViewModel()
        val context = mockk<Context>()
        every { NfcAdapter.getDefaultAdapter(context) } returns null

        vm.checkNfcAvailability(context)

        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.NfcHardwareUnavailable>()
        state.availability shouldBe NfcAvailability.NoHardware
    }

    // ── Test 2: checkNfcAvailability with disabled adapter → NfcHardwareUnavailable(Disabled) ──

    test("checkNfcAvailability with disabled adapter emits NfcHardwareUnavailable(Disabled)") {
        val vm = createViewModel()
        val context = mockk<Context>()
        val adapter = mockk<NfcAdapter>()
        every { NfcAdapter.getDefaultAdapter(context) } returns adapter
        every { adapter.isEnabled } returns false

        vm.checkNfcAvailability(context)

        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.NfcHardwareUnavailable>()
        state.availability shouldBe NfcAvailability.Disabled
    }

    // ── Test 3: checkNfcAvailability with enabled adapter → NfcWaiting ──

    test("checkNfcAvailability with enabled adapter emits NfcWaiting") {
        val vm = createViewModel()
        val context = mockk<Context>()
        val adapter = mockk<NfcAdapter>()
        every { NfcAdapter.getDefaultAdapter(context) } returns adapter
        every { adapter.isEnabled } returns true

        vm.checkNfcAvailability(context)

        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.NfcWaiting>()
    }

    // ── Test 4: onNfcTimeout → NfcTimeout with amount ──

    test("onNfcTimeout emits NfcTimeout with pendingAmountDollars") {
        val vm = createViewModel()
        // Set pendingAmountDollars via submitCardPresent
        vm.submitCardPresent("42.00")
        // Transition to NfcWaiting
        val context = mockk<Context>()
        val adapter = mockk<NfcAdapter>()
        every { NfcAdapter.getDefaultAdapter(context) } returns adapter
        every { adapter.isEnabled } returns true
        vm.checkNfcAvailability(context)

        vm.onNfcTimeout()

        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.NfcTimeout>()
        state.amount shouldBe "42.00"
    }

    // ── Test 5: onNfcTagDiscovered with EmvKernel failure → NfcError(canRetryTap=true) ──

    test("onNfcTagDiscovered with EmvKernel failure emits NfcError with canRetryTap=true") {
        val isoDep = mockk<IsoDep>()
        val vm = createViewModel { isoDep }
        val tag = mockk<Tag>()
        coEvery { EmvKernel.readCard(isoDep) } returns Result.failure(Exception("Card read error"))

        vm.onNfcTagDiscovered(tag)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.NfcError>()
        state.canRetryTap shouldBe true
        state.canRetrySubmit shouldBe false
    }

    // ── Test 6: onNfcTagDiscovered with ONLINE_PIN CVM → NfcCvmRequired(ONLINE_PIN) ──

    test("onNfcTagDiscovered with ONLINE_PIN CVM emits NfcCvmRequired(ONLINE_PIN)") {
        val isoDep = mockk<IsoDep>()
        val vm = createViewModel { isoDep }
        val cardData = makeCardData(CvmResult.ONLINE_PIN)
        val tag = mockk<Tag>()
        coEvery { EmvKernel.readCard(isoDep) } returns Result.success(cardData)

        vm.onNfcTagDiscovered(tag)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.NfcCvmRequired>()
        state.cvm shouldBe CvmResult.ONLINE_PIN
    }

    // ── Test 7: onNfcTagDiscovered with NO_CVM → NfcSubmitting then Success ──

    test("onNfcTagDiscovered with NO_CVM emits Success with CARD_PRESENT paymentType") {
        val isoDep = mockk<IsoDep>()
        val vm = createViewModel { isoDep }
        val cardData = makeCardData(CvmResult.NO_CVM)
        val tag = mockk<Tag>()
        coEvery { EmvKernel.readCard(isoDep) } returns Result.success(cardData)
        fakeService.saleResult = Result.success(makeSaleResponse())

        vm.onNfcTagDiscovered(tag)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.Success>()
        state.paymentType shouldBe PaymentType.CARD_PRESENT
    }

    // ── Test 8: onNfcTagDiscovered with CDCVM → NfcSubmitting then Success ──

    test("onNfcTagDiscovered with CDCVM emits Success with CARD_PRESENT paymentType") {
        val isoDep = mockk<IsoDep>()
        val vm = createViewModel { isoDep }
        val cardData = makeCardData(CvmResult.CDCVM)
        val tag = mockk<Tag>()
        coEvery { EmvKernel.readCard(isoDep) } returns Result.success(cardData)
        fakeService.saleResult = Result.success(makeSaleResponse())

        vm.onNfcTagDiscovered(tag)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.Success>()
        state.paymentType shouldBe PaymentType.CARD_PRESENT
    }

    // ── Test 9: onNfcTagDiscovered with SIGNATURE CVM → NfcCvmRequired(SIGNATURE) after submission ──

    test("onNfcTagDiscovered with SIGNATURE CVM emits NfcCvmRequired(SIGNATURE) after successful submission") {
        val isoDep = mockk<IsoDep>()
        val vm = createViewModel { isoDep }
        val cardData = makeCardData(CvmResult.SIGNATURE)
        val tag = mockk<Tag>()
        coEvery { EmvKernel.readCard(isoDep) } returns Result.success(cardData)
        fakeService.saleResult = Result.success(makeSaleResponse())

        vm.onNfcTagDiscovered(tag)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.NfcCvmRequired>()
        state.cvm shouldBe CvmResult.SIGNATURE
    }

    // ── Test 10: submitPin in NfcCvmRequired(ONLINE_PIN) state → submits NFC sale ──

    test("submitPin with 4 digits in NfcCvmRequired(ONLINE_PIN) state submits NFC sale and emits Success") {
        val isoDep = mockk<IsoDep>()
        val vm = createViewModel { isoDep }
        val cardData = makeCardData(CvmResult.ONLINE_PIN)
        val tag = mockk<Tag>()
        coEvery { EmvKernel.readCard(isoDep) } returns Result.success(cardData)
        fakeService.saleResult = Result.success(makeSaleResponse())

        // Get to NfcCvmRequired(ONLINE_PIN) state
        vm.onNfcTagDiscovered(tag)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.NfcCvmRequired>()

        // Submit PIN
        vm.submitPin("1234")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.Success>()
        state.paymentType shouldBe PaymentType.CARD_PRESENT
    }

    // ── Test 11: retryNfcSubmission increments retryCount and re-submits ──

    test("retryNfcSubmission on failure increments retryCount to 1") {
        val isoDep = mockk<IsoDep>()
        val vm = createViewModel { isoDep }
        val cardData = makeCardData(CvmResult.NO_CVM)
        val tag = mockk<Tag>()
        coEvery { EmvKernel.readCard(isoDep) } returns Result.success(cardData)
        // First submission fails
        fakeService.saleResult = Result.failure(Exception("Network error"))

        vm.onNfcTagDiscovered(tag)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should be in NfcError with canRetrySubmit=true
        val errorState = vm.uiState.value
        errorState.shouldBeInstanceOf<PaymentUiState.NfcError>()
        errorState.canRetrySubmit shouldBe true

        // Retry also fails
        vm.retryNfcSubmission()
        testDispatcher.scheduler.advanceUntilIdle()

        val retryState = vm.uiState.value
        retryState.shouldBeInstanceOf<PaymentUiState.NfcError>()
        retryState.retryCount shouldBe 1
    }

    // ── Test 12: retryNfcSubmission blocks at 3 retries ──

    test("retryNfcSubmission blocks at 3 retries with canRetryTap=true and canRetrySubmit=false") {
        val isoDep = mockk<IsoDep>()
        val vm = createViewModel { isoDep }
        val cardData = makeCardData(CvmResult.NO_CVM)
        val tag = mockk<Tag>()
        coEvery { EmvKernel.readCard(isoDep) } returns Result.success(cardData)
        fakeService.saleResult = Result.failure(Exception("Network error"))

        // Initial tap → fails
        vm.onNfcTagDiscovered(tag)
        testDispatcher.scheduler.advanceUntilIdle()

        // 3 retries, all failing
        repeat(3) {
            vm.retryNfcSubmission()
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // 4th retry attempt should be blocked (nfcRetryCount == 3)
        vm.retryNfcSubmission()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.NfcError>()
        state.canRetryTap shouldBe true
        state.canRetrySubmit shouldBe false
    }

    // ── Test 13: Successful NFC transaction adds CARD_PRESENT record to TransactionStore ──

    test("successful NFC transaction with NO_CVM adds CARD_PRESENT record to TransactionStore") {
        val isoDep = mockk<IsoDep>()
        val vm = createViewModel { isoDep }
        val cardData = makeCardData(CvmResult.NO_CVM)
        val tag = mockk<Tag>()
        coEvery { EmvKernel.readCard(isoDep) } returns Result.success(cardData)
        fakeService.saleResult = Result.success(makeSaleResponse("txn-card-present-001"))

        vm.onNfcTagDiscovered(tag)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.Success>()
        store.transactions.value.size shouldBe 1
        store.transactions.value[0].paymentType shouldBe PaymentType.CARD_PRESENT
        store.transactions.value[0].transactionId shouldBe "txn-card-present-001"
    }
})

/** Simple fake PaymentService for NFC unit testing */
private class FakeNfcPaymentService : PaymentService {
    var tokenResult: Result<String> = Result.success("fake-token")
    var saleResult: Result<SaleResponse> = Result.failure(Exception("not configured"))

    override suspend fun getToken(): Result<String> = tokenResult

    override suspend fun processSale(
        accountNumber: String,
        accountType: String,
        expiry: String,
        totalAmountDollars: String
    ): Result<SaleResponse> = saleResult

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
