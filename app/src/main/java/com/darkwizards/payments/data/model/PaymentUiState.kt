package com.darkwizards.payments.data.model

sealed class PaymentUiState {
    object Loading : PaymentUiState()
    object SelectPaymentType : PaymentUiState()
    data class CardNotPresentEntry(val error: String? = null) : PaymentUiState()
    data class CardPresentEntry(val error: String? = null) : PaymentUiState()
    data class PinEntry(val digitsEntered: Int = 0) : PaymentUiState()
    object SignatureCapture : PaymentUiState()
    object Processing : PaymentUiState()
    data class Success(val result: SaleResponse, val paymentType: PaymentType) : PaymentUiState()
    data class Error(val message: String) : PaymentUiState()
    data class InitError(val message: String) : PaymentUiState()
}
