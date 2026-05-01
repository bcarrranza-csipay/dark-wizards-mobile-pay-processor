package com.darkwizards.payments.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkwizards.payments.data.TransactionStore
import com.darkwizards.payments.data.model.PaymentType
import com.darkwizards.payments.data.model.RefundResponse
import com.darkwizards.payments.data.model.TransactionRecord
import com.darkwizards.payments.data.model.TransactionStatus
import com.darkwizards.payments.data.service.PaymentService
import com.darkwizards.payments.util.AmountUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

sealed class RefundState {
    object Idle : RefundState()
    object Loading : RefundState()
    data class Success(val refundResponse: RefundResponse) : RefundState()
    data class Error(val message: String) : RefundState()
}

class TransactionViewModel(
    private val paymentService: PaymentService,
    private val transactionStore: TransactionStore
) : ViewModel() {

    // Expose the store's StateFlow directly — no intermediate copy.
    val transactions: StateFlow<List<TransactionRecord>> = transactionStore.transactions

    // Version counter from the store — used by TransactionReportScreen to
    // detect mutations and re-read the snapshot.
    val storeVersion: StateFlow<Int> = transactionStore.version

    /** Returns the current transaction list snapshot. */
    fun getTransactionSnapshot(): List<TransactionRecord> = transactionStore.transactions.value

    /**
     * No-op kept for API compatibility.
     */
    fun refreshTransactions() { /* no-op */ }

    /**
     * Fetches all transactions from the MCP server and merges them into the
     * local TransactionStore. This ensures the list is always up-to-date,
     * even if addTransactionRecord was missed during the payment flow.
     *
     * Called every time the Transactions tab is entered.
     */
    fun loadTransactionsFromServer() {
        viewModelScope.launch {
            android.util.Log.d("TransactionVM", "loadTransactionsFromServer starting...")
            paymentService.getAllTransactions().fold(
                onSuccess = { history ->
                    android.util.Log.d("TransactionVM", "Server returned ${history.size} transactions, store has ${transactionStore.transactions.value.size}")
                    var added = 0
                    history.forEach { tx ->
                        if (transactionStore.getTransaction(tx.transactionId) != null) return@forEach
                        added++
                        val status = when (tx.transactionStatus) {
                            "Declined" -> TransactionStatus.DECLINED
                            "Voided"   -> TransactionStatus.VOIDED
                            "Refunded" -> TransactionStatus.REFUNDED
                            else       -> TransactionStatus.APPROVED
                        }
                        val dateTime = try {
                            val parts = tx.creationTime.split(" ")
                            val dateParts = parts[0].split("-")
                            val timeParts = parts[1].split(":")
                            // Server timestamps are UTC — parse as UTC to match addTransactionRecord
                            java.time.LocalDateTime.of(
                                dateParts[0].toInt(), dateParts[1].toInt(), dateParts[2].toInt(),
                                timeParts[0].toInt(), timeParts[1].toInt(), timeParts[2].toInt()
                            )
                        } catch (e: Exception) { java.time.LocalDateTime.now(java.time.ZoneOffset.UTC) }
                        transactionStore.addTransaction(
                            TransactionRecord(
                                transactionId  = tx.transactionId,
                                amount         = AmountUtils.centsToDisplay(tx.approvedAmount),
                                amountCents    = tx.approvedAmount.toIntOrNull() ?: 0,
                                feeAmount      = AmountUtils.centsToDisplay(tx.feeAmount),
                                dateTime       = dateTime,
                                paymentType    = PaymentType.CARD_NOT_PRESENT,
                                status         = status,
                                approvalNumber = tx.approvalNumber,
                                accountLast4   = tx.accountLast4,
                                accountType    = tx.accountType
                            )
                        )
                    }
                    android.util.Log.d("TransactionVM", "Added $added new transactions from server, store now has ${transactionStore.transactions.value.size}")
                },
                onFailure = { e ->
                    android.util.Log.e("TransactionVM", "loadTransactionsFromServer FAILED: ${e.message}")
                }
            )
        }
    }

    private val _selectedTransaction = MutableStateFlow<TransactionRecord?>(null)
    val selectedTransaction: StateFlow<TransactionRecord?> = _selectedTransaction.asStateFlow()

    private val _refundState = MutableStateFlow<RefundState>(RefundState.Idle)
    val refundState: StateFlow<RefundState> = _refundState.asStateFlow()

    fun selectTransaction(id: String) {
        _selectedTransaction.value = transactionStore.getTransaction(id)
    }

    fun initiateRefund(transactionId: String) {
        _refundState.value = RefundState.Loading
        viewModelScope.launch {
            paymentService.settleTransactions().fold(
                onSuccess = {
                    paymentService.processRefund(transactionId).fold(
                        onSuccess = { refundResponse ->
                            transactionStore.updateTransactionStatus(transactionId, TransactionStatus.REFUNDED)
                            _selectedTransaction.value = transactionStore.getTransaction(transactionId)
                            _refundState.value = RefundState.Success(refundResponse)
                        },
                        onFailure = { e ->
                            _refundState.value = RefundState.Error(e.message ?: "Refund failed")
                        }
                    )
                },
                onFailure = { e ->
                    _refundState.value = RefundState.Error(e.message ?: "Settlement failed")
                }
            )
        }
    }

    fun resetRefundState() {
        _refundState.value = RefundState.Idle
    }
}
