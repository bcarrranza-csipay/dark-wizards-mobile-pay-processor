package com.darkwizards.payments.data

import com.darkwizards.payments.data.model.TransactionRecord
import com.darkwizards.payments.data.model.TransactionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TransactionStore {
    private val _transactions = MutableStateFlow<List<TransactionRecord>>(emptyList())
    val transactions: StateFlow<List<TransactionRecord>> = _transactions.asStateFlow()

    // Simple version counter — incremented on every mutation.
    // Used by UI to detect changes even when StateFlow doesn't trigger recomposition.
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun addTransaction(record: TransactionRecord) {
        _transactions.update { current ->
            (current + record).sortedByDescending { it.dateTime }
        }
        _version.value++
    }

    fun updateTransactionStatus(transactionId: String, newStatus: TransactionStatus) {
        _transactions.update { current ->
            current.map { record ->
                if (record.transactionId == transactionId) {
                    record.copy(status = newStatus)
                } else {
                    record
                }
            }
        }
        _version.value++
    }

    fun getTransaction(transactionId: String): TransactionRecord? {
        return _transactions.value.find { it.transactionId == transactionId }
    }

    fun clearTransactions() {
        _transactions.value = emptyList()
    }
}
