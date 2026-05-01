package com.darkwizards.payments.ui.viewmodel

import com.darkwizards.payments.data.TransactionStore
import com.darkwizards.payments.data.model.HistoricalTransaction
import com.darkwizards.payments.data.model.ModeResponse
import com.darkwizards.payments.data.model.PaymentType
import com.darkwizards.payments.data.model.PaymentUiState
import com.darkwizards.payments.data.model.RefundResponse
import com.darkwizards.payments.data.model.SaleResponse
import com.darkwizards.payments.data.model.SettleResponse
import com.darkwizards.payments.data.model.TransactionDetail
import com.darkwizards.payments.data.service.PaymentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest : FunSpec({

    val testDispatcher = StandardTestDispatcher()

    lateinit var fakeService: FakePaymentService
    lateinit var store: TransactionStore
    lateinit var viewModel: PaymentViewModel

    beforeEach {
        Dispatchers.setMain(testDispatcher)
        fakeService = FakePaymentService()
        store = TransactionStore()
    }

    afterEach {
        Dispatchers.resetMain()
    }

    fun createViewModel(): PaymentViewModel {
        return PaymentViewModel(fakeService, store).also { viewModel = it }
    }

    test("init success transitions to SelectPaymentType") {
        fakeService.tokenResult = Result.success("test-token")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.SelectPaymentType>()
    }

    test("init failure transitions to InitError") {
        fakeService.tokenResult = Result.failure(Exception("Connection refused"))
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.InitError>()
        state.message shouldBe "Connection refused"
    }

    test("selectPaymentType CARD_PRESENT transitions to CardPresentEntry") {
        fakeService.tokenResult = Result.success("token")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentType(PaymentType.CARD_PRESENT)
        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.CardPresentEntry>()
    }

    test("selectPaymentType CARD_NOT_PRESENT transitions to CardNotPresentEntry") {
        fakeService.tokenResult = Result.success("token")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentType(PaymentType.CARD_NOT_PRESENT)
        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.CardNotPresentEntry>()
    }

    test("submitCardNotPresent with empty card number shows error") {
        fakeService.tokenResult = Result.success("token")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentType(PaymentType.CARD_NOT_PRESENT)
        vm.submitCardNotPresent("", "12.2026", "123", "25.00")
        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.CardNotPresentEntry>()
        state.error shouldBe "Card number, expiration date, and amount are required"
    }

    test("submitCardNotPresent with empty expiry shows error") {
        fakeService.tokenResult = Result.success("token")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentType(PaymentType.CARD_NOT_PRESENT)
        vm.submitCardNotPresent("4111111111111111", "", "123", "25.00")
        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.CardNotPresentEntry>()
        state.error shouldBe "Card number, expiration date, and amount are required"
    }

    test("submitCardNotPresent with empty amount shows error") {
        fakeService.tokenResult = Result.success("token")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentType(PaymentType.CARD_NOT_PRESENT)
        vm.submitCardNotPresent("4111111111111111", "12.2026", "123", "")
        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.CardNotPresentEntry>()
        state.error shouldBe "Card number, expiration date, and amount are required"
    }

    test("submitCardNotPresent with valid fields transitions to PinEntry") {
        fakeService.tokenResult = Result.success("token")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentType(PaymentType.CARD_NOT_PRESENT)
        vm.submitCardNotPresent("4111111111111111", "12.2026", "123", "25.00")
        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.PinEntry>()
    }

    test("submitCardPresent stores pending amount and stays in CardPresentEntry") {
        fakeService.tokenResult = Result.success("token")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentType(PaymentType.CARD_PRESENT)
        vm.submitCardPresent("50.00")
        // submitCardPresent stores the amount for the NFC flow but does not change state
        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.CardPresentEntry>()
    }

    test("submitPin with 4 digits transitions to SignatureCapture") {
        fakeService.tokenResult = Result.success("token")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentType(PaymentType.CARD_NOT_PRESENT)
        vm.submitCardNotPresent("4111111111111111", "12.2026", "123", "25.00")
        vm.submitPin("1234")
        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.SignatureCapture>()
    }

    test("submitPin with non-4-digit input does not transition") {
        fakeService.tokenResult = Result.success("token")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentType(PaymentType.CARD_NOT_PRESENT)
        vm.submitCardNotPresent("4111111111111111", "12.2026", "123", "25.00")
        vm.submitPin("12")
        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.PinEntry>()
    }

    test("confirmSignature success transitions to Success and adds transaction") {
        fakeService.tokenResult = Result.success("token")
        fakeService.saleResult = Result.success(
            SaleResponse(
                transactionId = "txn-001",
                approvedAmount = "2500",
                feeAmount = "75",
                approvalNumber = "AP123",
                accountType = "Visa",
                accountFirst6 = "411111",
                accountLast4 = "1111"
            )
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentType(PaymentType.CARD_NOT_PRESENT)
        vm.submitCardNotPresent("4111111111111111", "12.2026", "123", "25.00")
        vm.submitPin("1234")
        vm.confirmSignature()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.Success>()
        state.result.transactionId shouldBe "txn-001"
        state.paymentType shouldBe PaymentType.CARD_NOT_PRESENT

        store.transactions.value.size shouldBe 1
        store.transactions.value[0].transactionId shouldBe "txn-001"
    }

    test("confirmSignature failure transitions to Error") {
        fakeService.tokenResult = Result.success("token")
        fakeService.saleResult = Result.failure(Exception("Do Not Honor"))
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentType(PaymentType.CARD_NOT_PRESENT)
        vm.submitCardNotPresent("4111111111111111", "12.2026", "123", "25.00")
        vm.submitPin("1234")
        vm.confirmSignature()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        state.shouldBeInstanceOf<PaymentUiState.Error>()
        state.message shouldBe "Do Not Honor"
    }

    test("retry from InitError re-attempts token acquisition") {
        fakeService.tokenResult = Result.failure(Exception("fail"))
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.InitError>()

        fakeService.tokenResult = Result.success("new-token")
        vm.retry()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.SelectPaymentType>()
    }

    test("retry from Error goes back to SelectPaymentType") {
        fakeService.tokenResult = Result.success("token")
        fakeService.saleResult = Result.failure(Exception("fail"))
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentType(PaymentType.CARD_NOT_PRESENT)
        vm.submitCardNotPresent("4111111111111111", "12.2026", "123", "25.00")
        vm.submitPin("1234")
        vm.confirmSignature()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.Error>()

        vm.retry()
        vm.uiState.value.shouldBeInstanceOf<PaymentUiState.SelectPaymentType>()
    }
})

/** Simple fake PaymentService for unit testing */
private class FakePaymentService : PaymentService {
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
