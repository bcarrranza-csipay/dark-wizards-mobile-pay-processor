package com.darkwizards.payments.ui.viewmodel

import com.darkwizards.payments.data.TransactionStore
import com.darkwizards.payments.data.model.HistoricalTransaction
import com.darkwizards.payments.data.model.ModeResponse
import com.darkwizards.payments.data.model.PaymentType
import com.darkwizards.payments.data.model.RefundResponse
import com.darkwizards.payments.data.model.SaleResponse
import com.darkwizards.payments.data.model.SettleResponse
import com.darkwizards.payments.data.model.TransactionDetail
import com.darkwizards.payments.data.model.TransactionRecord
import com.darkwizards.payments.data.model.TransactionStatus
import com.darkwizards.payments.data.service.PaymentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionViewModelTest : FunSpec({

    val testDispatcher = StandardTestDispatcher()

    lateinit var fakeService: FakePaymentServiceForTxn
    lateinit var store: TransactionStore
    lateinit var viewModel: TransactionViewModel

    beforeEach {
        Dispatchers.setMain(testDispatcher)
        fakeService = FakePaymentServiceForTxn()
        store = TransactionStore()
    }

    afterEach {
        Dispatchers.resetMain()
    }

    fun createViewModel(): TransactionViewModel {
        return TransactionViewModel(fakeService, store).also { viewModel = it }
    }

    fun addSampleTransaction(id: String = "txn-001", status: TransactionStatus = TransactionStatus.APPROVED): TransactionRecord {
        val record = TransactionRecord(
            transactionId = id,
            amount = "$25.00",
            amountCents = 2500,
            feeAmount = "$0.75",
            dateTime = LocalDateTime.now(),
            paymentType = PaymentType.CARD_NOT_PRESENT,
            status = status,
            approvalNumber = "AP123",
            accountLast4 = "1111",
            accountType = "Visa"
        )
        store.addTransaction(record)
        return record
    }

    test("transactions flow reflects store contents") {
        val vm = createViewModel()
        vm.transactions.value.size shouldBe 0
        addSampleTransaction()
        vm.transactions.value.size shouldBe 1
    }

    test("selectTransaction sets selectedTransaction") {
        addSampleTransaction("txn-001")
        val vm = createViewModel()
        vm.selectTransaction("txn-001")
        vm.selectedTransaction.value?.transactionId shouldBe "txn-001"
    }

    test("selectTransaction with unknown id sets null") {
        val vm = createViewModel()
        vm.selectTransaction("nonexistent")
        vm.selectedTransaction.value shouldBe null
    }

    test("initiateRefund success updates status and refundState") {
        addSampleTransaction("txn-001")
        fakeService.settleResult = Result.success(SettleResponse("OK", 1))
        fakeService.refundResult = Result.success(
            RefundResponse(
                transactionId = "refund-001",
                referencedTransactionId = "txn-001",
                approvedAmount = "2500"
            )
        )
        val vm = createViewModel()
        vm.initiateRefund("txn-001")
        testDispatcher.scheduler.advanceUntilIdle()

        val refundState = vm.refundState.value
        refundState.shouldBeInstanceOf<RefundState.Success>()
        refundState.refundResponse.transactionId shouldBe "refund-001"

        store.getTransaction("txn-001")?.status shouldBe TransactionStatus.REFUNDED
        vm.selectedTransaction.value?.status shouldBe TransactionStatus.REFUNDED
    }

    test("initiateRefund sets loading state") {
        addSampleTransaction("txn-001")
        fakeService.settleResult = Result.success(SettleResponse("OK", 1))
        fakeService.refundResult = Result.success(
            RefundResponse("refund-001", "txn-001", "2500")
        )
        val vm = createViewModel()
        vm.initiateRefund("txn-001")
        // Before advancing, state should be Loading
        vm.refundState.value.shouldBeInstanceOf<RefundState.Loading>()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    test("initiateRefund settle failure shows error") {
        addSampleTransaction("txn-001")
        fakeService.settleResult = Result.failure(Exception("Settlement failed"))
        val vm = createViewModel()
        vm.initiateRefund("txn-001")
        testDispatcher.scheduler.advanceUntilIdle()

        val refundState = vm.refundState.value
        refundState.shouldBeInstanceOf<RefundState.Error>()
        refundState.message shouldBe "Settlement failed"
    }

    test("initiateRefund refund failure shows error") {
        addSampleTransaction("txn-001")
        fakeService.settleResult = Result.success(SettleResponse("OK", 1))
        fakeService.refundResult = Result.failure(Exception("Refund denied"))
        val vm = createViewModel()
        vm.initiateRefund("txn-001")
        testDispatcher.scheduler.advanceUntilIdle()

        val refundState = vm.refundState.value
        refundState.shouldBeInstanceOf<RefundState.Error>()
        refundState.message shouldBe "Refund denied"
    }

    test("resetRefundState sets Idle") {
        val vm = createViewModel()
        addSampleTransaction("txn-001")
        fakeService.settleResult = Result.success(SettleResponse("OK", 1))
        fakeService.refundResult = Result.success(
            RefundResponse("refund-001", "txn-001", "2500")
        )
        vm.initiateRefund("txn-001")
        testDispatcher.scheduler.advanceUntilIdle()
        vm.refundState.value.shouldBeInstanceOf<RefundState.Success>()

        vm.resetRefundState()
        vm.refundState.value.shouldBeInstanceOf<RefundState.Idle>()
    }
})

private class FakePaymentServiceForTxn : PaymentService {
    var settleResult: Result<SettleResponse> = Result.failure(Exception("not configured"))
    var refundResult: Result<RefundResponse> = Result.failure(Exception("not configured"))

    override suspend fun getToken(): Result<String> = Result.success("fake-token")

    override suspend fun processSale(
        accountNumber: String,
        accountType: String,
        expiry: String,
        totalAmountDollars: String
    ): Result<SaleResponse> = Result.failure(Exception("not implemented"))

    override suspend fun processRefund(transactionId: String): Result<RefundResponse> = refundResult

    override suspend fun settleTransactions(): Result<SettleResponse> = settleResult

    override suspend fun getTransaction(transactionId: String): Result<TransactionDetail> =
        Result.failure(Exception("not implemented"))

    override suspend fun getMode(): Result<ModeResponse> =
        Result.failure(Exception("not implemented"))

    override suspend fun getAllTransactions(terminalId: String?): Result<List<HistoricalTransaction>> =
        Result.success(emptyList())
}
