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

    // NFC-specific states
    object NfcWaiting : PaymentUiState()          // Reader active, waiting for tap
    object NfcReading : PaymentUiState()           // Tag detected, EMV dialogue in progress
    data class NfcCvmRequired(
        val cvm: CvmResult,
        val cardData: EmvCardData
    ) : PaymentUiState()                           // CVM step needed before submission
    object NfcSubmitting : PaymentUiState()        // Submitting to Pyxis
    data class NfcTimeout(val amount: String) : PaymentUiState()
    data class NfcError(
        val message: String,
        val canRetryTap: Boolean,
        val canRetrySubmit: Boolean,
        val retryCount: Int = 0
    ) : PaymentUiState()
    data class NfcHardwareUnavailable(
        val availability: NfcAvailability
    ) : PaymentUiState()
}
