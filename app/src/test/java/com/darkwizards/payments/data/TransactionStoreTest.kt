package com.darkwizards.payments.data

import com.darkwizards.payments.data.model.PaymentType
import com.darkwizards.payments.data.model.TransactionRecord
import com.darkwizards.payments.data.model.TransactionStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class TransactionStoreTest : FunSpec({

    fun createRecord(
        id: String = "txn-001",
        dateTime: LocalDateTime = LocalDateTime.now(),
        status: TransactionStatus = TransactionStatus.APPROVED
    ) = TransactionRecord(
        transactionId = id,
        amount = "$25.30",
        amountCents = 2530,
        feeAmount = "$0.76",
        dateTime = dateTime,
        paymentType = PaymentType.CARD_PRESENT,
        status = status,
        approvalNumber = "AP123",
        accountLast4 = "1111",
        accountType = "Visa"
    )

    test("initial transactions list is empty") {
        val store = TransactionStore()
        store.transactions.value.shouldBeEmpty()
    }

    test("addTransaction adds a record") {
        val store = TransactionStore()
        val record = createRecord()
        store.addTransaction(record)
        store.transactions.value shouldHaveSize 1
        store.transactions.value.first().transactionId shouldBe "txn-001"
    }

    test("transactions are sorted descending by dateTime") {
        val store = TransactionStore()
        val older = createRecord(id = "txn-old", dateTime = LocalDateTime.of(2024, 1, 1, 10, 0))
        val newer = createRecord(id = "txn-new", dateTime = LocalDateTime.of(2024, 6, 15, 14, 30))

        store.addTransaction(older)
        store.addTransaction(newer)

        val ids = store.transactions.value.map { it.transactionId }
        ids shouldBe listOf("txn-new", "txn-old")
    }

    test("updateTransactionStatus changes status of matching transaction") {
        val store = TransactionStore()
        store.addTransaction(createRecord(id = "txn-001", status = TransactionStatus.APPROVED))

        store.updateTransactionStatus("txn-001", TransactionStatus.REFUNDED)

        store.transactions.value.first().status shouldBe TransactionStatus.REFUNDED
    }

    test("updateTransactionStatus does not affect other transactions") {
        val store = TransactionStore()
        val now = LocalDateTime.now()
        store.addTransaction(createRecord(id = "txn-001", dateTime = now.minusHours(1)))
        store.addTransaction(createRecord(id = "txn-002", dateTime = now))

        store.updateTransactionStatus("txn-001", TransactionStatus.VOIDED)

        val txn002 = store.transactions.value.find { it.transactionId == "txn-002" }
        txn002.shouldNotBeNull()
        txn002.status shouldBe TransactionStatus.APPROVED
    }

    test("getTransaction returns matching record") {
        val store = TransactionStore()
        store.addTransaction(createRecord(id = "txn-001"))

        val result = store.getTransaction("txn-001")
        result.shouldNotBeNull()
        result.transactionId shouldBe "txn-001"
    }

    test("getTransaction returns null for non-existent id") {
        val store = TransactionStore()
        store.addTransaction(createRecord(id = "txn-001"))

        store.getTransaction("txn-999").shouldBeNull()
    }
})
