package com.darkwizards.payments.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkwizards.payments.data.TransactionStore
import com.darkwizards.payments.data.model.PaymentType
import com.darkwizards.payments.data.model.PaymentUiState
import com.darkwizards.payments.data.model.SaleResponse
import com.darkwizards.payments.data.model.TransactionRecord
import com.darkwizards.payments.data.model.TransactionStatus
import com.darkwizards.payments.data.service.PaymentService
import com.darkwizards.payments.util.AmountUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class PaymentViewModel(
    private val paymentService: PaymentService,
    private val transactionStore: TransactionStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Loading)
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    // Pending payment data stored between flow steps
    private var pendingAccountNumber: String = ""
    private var pendingAccountType: String = ""
    private var pendingExpiry: String = ""
    private var pendingAmountDollars: String = ""
    private var pendingPaymentType: PaymentType = PaymentType.CARD_NOT_PRESENT

    init {
        initToken()
    }

    private fun initToken() {
        _uiState.value = PaymentUiState.Loading
        viewModelScope.launch {
            paymentService.getToken().fold(
                onSuccess = { _uiState.value = PaymentUiState.SelectPaymentType },
                onFailure = { e ->
                    _uiState.value = PaymentUiState.InitError(
                        message = e.message ?: "Failed to connect to payment service"
                    )
                }
            )
        }
    }

    fun selectPaymentType(type: PaymentType) {
        pendingPaymentType = type
        _uiState.value = when (type) {
            PaymentType.CARD_PRESENT -> PaymentUiState.CardPresentEntry()
            PaymentType.CARD_NOT_PRESENT -> PaymentUiState.CardNotPresentEntry()
        }
    }

    fun submitCardNotPresent(cardNumber: String, expiry: String, cvv: String, amount: String) {
        // Validate required fields
        if (cardNumber.isBlank() || expiry.isBlank() || amount.isBlank()) {
            _uiState.value = PaymentUiState.CardNotPresentEntry(
                error = "Card number, expiration date, and amount are required"
            )
            return
        }

        pendingAccountNumber = cardNumber
        pendingAccountType = "Visa"
        pendingExpiry = expiry
        pendingAmountDollars = amount
        pendingPaymentType = PaymentType.CARD_NOT_PRESENT
        _uiState.value = PaymentUiState.PinEntry()
    }

    fun submitCardPresent(amount: String) {
        // Auto-populate test card details
        pendingAccountNumber = "4111111111111111"
        pendingAccountType = "Visa"
        pendingExpiry = "12.2026"
        pendingAmountDollars = amount
        pendingPaymentType = PaymentType.CARD_PRESENT
        _uiState.value = PaymentUiState.PinEntry()
    }

    fun submitPin(pin: String) {
        // Accept any 4-digit PIN
        if (pin.length == 4 && pin.all { it.isDigit() }) {
            _uiState.value = PaymentUiState.SignatureCapture
        }
    }

    fun confirmSignature() {
        _uiState.value = PaymentUiState.Processing
        viewModelScope.launch {
            paymentService.processSale(
                accountNumber = pendingAccountNumber,
                accountType = pendingAccountType,
                expiry = pendingExpiry,
                totalAmountDollars = pendingAmountDollars
            ).fold(
                onSuccess = { saleResponse ->
                    addTransactionRecord(saleResponse)
                    _uiState.value = PaymentUiState.Success(
                        result = saleResponse,
                        paymentType = pendingPaymentType
                    )
                },
                onFailure = { e ->
                    _uiState.value = PaymentUiState.Error(
                        message = e.message ?: "Payment failed"
                    )
                }
            )
        }
    }

    fun retry() {
        val currentState = _uiState.value
        if (currentState is PaymentUiState.InitError) {
            initToken()
        } else {
            _uiState.value = PaymentUiState.SelectPaymentType
        }
    }

    private fun addTransactionRecord(response: SaleResponse) {
        val record = TransactionRecord(
            transactionId = response.transactionId,
            amount = AmountUtils.centsToDisplay(response.approvedAmount),
            amountCents = response.approvedAmount.toIntOrNull() ?: 0,
            feeAmount = AmountUtils.centsToDisplay(response.feeAmount),
            dateTime = LocalDateTime.now(),
            paymentType = pendingPaymentType,
            status = TransactionStatus.APPROVED,
            approvalNumber = response.approvalNumber,
            accountLast4 = response.accountLast4,
            accountType = response.accountType
        )
        transactionStore.addTransaction(record)
    }
}
