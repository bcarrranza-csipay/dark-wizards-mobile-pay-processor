package com.darkwizards.payments.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkwizards.payments.data.TransactionStore
import com.darkwizards.payments.data.model.RefundResponse
import com.darkwizards.payments.data.model.TransactionRecord
import com.darkwizards.payments.data.model.TransactionStatus
import com.darkwizards.payments.data.service.PaymentService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    val transactions: StateFlow<List<TransactionRecord>> = transactionStore.transactions

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
