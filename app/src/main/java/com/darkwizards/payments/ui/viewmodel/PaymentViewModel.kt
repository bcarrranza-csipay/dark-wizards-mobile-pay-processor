package com.darkwizards.payments.ui.viewmodel

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkwizards.payments.data.TransactionStore
import com.darkwizards.payments.data.model.CvmResult
import com.darkwizards.payments.data.model.EmvCardData
import com.darkwizards.payments.data.model.NfcAvailability
import com.darkwizards.payments.data.model.PaymentType
import com.darkwizards.payments.data.model.PaymentUiState
import com.darkwizards.payments.data.model.SaleResponse
import com.darkwizards.payments.data.model.TransactionRecord
import com.darkwizards.payments.data.model.TransactionStatus
import com.darkwizards.payments.data.service.PaymentService
import com.darkwizards.payments.domain.EmvKernel
import com.darkwizards.payments.util.AmountUtils
import com.darkwizards.payments.util.NfcLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalDateTime

/** The three modes the user can pick from the badge sheet. */
enum class PaymentMode(val key: String, val label: String) {
    SIMULATOR("simulator", "Tony MCP"),
    MOCK("mock",           "Sandbox"),
    LIVE("live",           "Live (Coming soon)");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: SIMULATOR
    }
}

class PaymentViewModel(
    private val paymentService: PaymentService,
    private val transactionStore: TransactionStore,
    internal val isoDepFactory: (Tag) -> IsoDep? = { tag -> IsoDep.get(tag) },
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Loading)
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    // Client-side selected mode — drives badge label/color and seed behaviour
    private val _selectedMode = MutableStateFlow(PaymentMode.SIMULATOR)
    val selectedMode: StateFlow<PaymentMode> = _selectedMode.asStateFlow()

    // Controls visibility of the mode-picker bottom sheet
    private val _showModePicker = MutableStateFlow(false)
    val showModePicker: StateFlow<Boolean> = _showModePicker.asStateFlow()

    // Pending payment data stored between flow steps
    private var pendingAccountNumber: String = ""
    private var pendingAccountType: String = ""
    private var pendingExpiry: String = ""
    private var pendingAmountDollars: String = ""
    private var pendingPaymentType: PaymentType = PaymentType.CARD_NOT_PRESENT

    /** Exposed for TapScreen to display the merchant-entered amount while waiting for a tap. */
    internal val currentAmountDollars: String get() = pendingAmountDollars

    // NFC-specific fields
    private var pendingNfcCardData: EmvCardData? = null
    private var nfcTimeoutJob: Job? = null
    private var nfcRetryCount: Int = 0

    init {
        initToken()
    }

    // ── Mode picker ───────────────────────────────────────────────────────────

    fun openModePicker() { _showModePicker.value = true }
    fun closeModePicker() { _showModePicker.value = false }

    /**
     * Called when the user selects a mode in the bottom sheet.
     * LIVE is disabled — tapping it does nothing.
     * Switching mode clears transactions and re-initialises.
     */
    fun selectMode(mode: PaymentMode) {
        if (mode == PaymentMode.LIVE) return   // coming soon — not interactive
        if (mode == _selectedMode.value) {
            closeModePicker()
            return
        }
        _selectedMode.value = mode
        closeModePicker()
        // Clear existing transactions and re-init so seeds reload for the new mode
        transactionStore.clearTransactions()
        initToken()
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private fun initToken() {
        _uiState.value = PaymentUiState.Loading
        viewModelScope.launch {
            paymentService.getToken().fold(
                onSuccess = {
                    // Always restore full transaction history from Redis on startup
                    paymentService.getAllTransactions().fold(
                        onSuccess = { history ->
                            history.forEach { tx ->
                                // Skip if already in store (avoid duplicates on mode switch)
                                if (transactionStore.getTransaction(tx.transactionId) != null) return@forEach
                                val status = when (tx.transactionStatus) {
                                    "Declined" -> TransactionStatus.DECLINED
                                    "Voided"   -> TransactionStatus.VOIDED
                                    "Refunded" -> TransactionStatus.REFUNDED
                                    else       -> TransactionStatus.APPROVED
                                }
                                val paymentType = PaymentType.CARD_NOT_PRESENT
                                val dateTime = try {
                                    val parts = tx.creationTime.split(" ")
                                    val dateParts = parts[0].split("-")
                                    val timeParts = parts[1].split(":")
                                    LocalDateTime.of(
                                        dateParts[0].toInt(), dateParts[1].toInt(), dateParts[2].toInt(),
                                        timeParts[0].toInt(), timeParts[1].toInt(), timeParts[2].toInt()
                                    )
                                } catch (e: Exception) { LocalDateTime.now() }
                                transactionStore.addTransaction(
                                    TransactionRecord(
                                        transactionId  = tx.transactionId,
                                        amount         = AmountUtils.centsToDisplay(tx.approvedAmount),
                                        amountCents    = tx.approvedAmount.toIntOrNull() ?: 0,
                                        feeAmount      = AmountUtils.centsToDisplay(tx.feeAmount),
                                        dateTime       = dateTime,
                                        paymentType    = paymentType,
                                        status         = status,
                                        approvalNumber = tx.approvalNumber,
                                        accountLast4   = tx.accountLast4,
                                        accountType    = tx.accountType
                                    )
                                )
                            }
                        },
                        onFailure = { /* best-effort */ }
                    )

                    // Additionally seed mock transactions when mode is MOCK
                    if (_selectedMode.value == PaymentMode.MOCK) {
                        paymentService.getMode().fold(
                            onSuccess = { modeResponse ->
                                modeResponse.seededTransactions.forEach { seeded ->
                                    if (transactionStore.getTransaction(seeded.transactionId) != null) return@forEach
                                    val status = when (seeded.transactionStatus) {
                                        "Declined" -> TransactionStatus.DECLINED
                                        "Voided"   -> TransactionStatus.VOIDED
                                        "Refunded" -> TransactionStatus.REFUNDED
                                        else       -> TransactionStatus.APPROVED
                                    }
                                    val dateTime = try {
                                        val parts = seeded.creationTime.split(" ")
                                        val dateParts = parts[0].split("-")
                                        val timeParts = parts[1].split(":")
                                        LocalDateTime.of(
                                            dateParts[0].toInt(), dateParts[1].toInt(), dateParts[2].toInt(),
                                            timeParts[0].toInt(), timeParts[1].toInt(), timeParts[2].toInt()
                                        )
                                    } catch (e: Exception) { LocalDateTime.now() }
                                    transactionStore.addTransaction(
                                        TransactionRecord(
                                            transactionId  = seeded.transactionId,
                                            amount         = AmountUtils.centsToDisplay(seeded.approvedAmount),
                                            amountCents    = seeded.approvedAmount.toIntOrNull() ?: 0,
                                            feeAmount      = AmountUtils.centsToDisplay(seeded.feeAmount),
                                            dateTime       = dateTime,
                                            paymentType    = PaymentType.CARD_NOT_PRESENT,
                                            status         = status,
                                            approvalNumber = seeded.approvalNumber,
                                            accountLast4   = seeded.accountLast4,
                                            accountType    = seeded.accountType
                                        )
                                    )
                                }
                            },
                            onFailure = { /* best-effort */ }
                        )
                    }
                    _uiState.value = PaymentUiState.SelectPaymentType
                },
                onFailure = { e ->
                    _uiState.value = PaymentUiState.InitError(
                        message = e.message ?: "Failed to connect to payment service"
                    )
                }
            )
        }
    }

    // ── Payment flow ──────────────────────────────────────────────────────────

    fun selectPaymentType(type: PaymentType) {
        pendingPaymentType = type
        _uiState.value = when (type) {
            PaymentType.CARD_PRESENT     -> PaymentUiState.CardPresentEntry()
            PaymentType.CARD_NOT_PRESENT -> PaymentUiState.CardNotPresentEntry()
        }
    }

    fun submitCardNotPresent(cardNumber: String, expiry: String, cvv: String, amount: String) {
        if (cardNumber.isBlank() || expiry.isBlank() || amount.isBlank()) {
            _uiState.value = PaymentUiState.CardNotPresentEntry(
                error = "Card number, expiration date, and amount are required"
            )
            return
        }
        pendingAccountNumber = cardNumber
        pendingAccountType   = "Visa"
        pendingExpiry        = expiry
        pendingAmountDollars = amount
        pendingPaymentType   = PaymentType.CARD_NOT_PRESENT
        _uiState.value = PaymentUiState.PinEntry()
    }

    fun submitCardPresent(amount: String) {
        pendingAccountNumber = "4111111111111111"
        pendingAccountType   = "Visa"
        pendingExpiry        = "12.2026"
        pendingAmountDollars = amount
        pendingPaymentType   = PaymentType.CARD_PRESENT
        // Do NOT change state here — TapScreen manages NFC activation itself
    }

    // ── NFC methods ───────────────────────────────────────────────────────────

    fun checkNfcAvailability(context: Context) {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        when {
            adapter == null -> {
                _uiState.value = PaymentUiState.NfcHardwareUnavailable(NfcAvailability.NoHardware)
            }
            !adapter.isEnabled -> {
                _uiState.value = PaymentUiState.NfcHardwareUnavailable(NfcAvailability.Disabled)
            }
            else -> {
                _uiState.value = PaymentUiState.NfcWaiting
                nfcTimeoutJob?.cancel()
                nfcTimeoutJob = viewModelScope.launch {
                    delay(60_000L)
                    onNfcTimeout()
                }
            }
        }
    }

    fun onNfcTimeout() {
        nfcTimeoutJob?.cancel()
        nfcTimeoutJob = null
        _uiState.value = PaymentUiState.NfcTimeout(pendingAmountDollars)
    }

    fun onNfcTagDiscovered(tag: Tag) {
        nfcTimeoutJob?.cancel()
        nfcTimeoutJob = null
        _uiState.value = PaymentUiState.NfcReading
        viewModelScope.launch {
            val isoDep = withContext(ioDispatcher) { isoDepFactory(tag) }
            if (isoDep == null) {
                NfcLogger.e("PaymentViewModel", "IsoDep.get(tag) returned null — tag tech: ${tag.techList.joinToString()}")
                _uiState.value = PaymentUiState.NfcError(
                    message = "Card read error — not an ISO-DEP card",
                    canRetryTap = true,
                    canRetrySubmit = false
                )
                return@launch
            }
            NfcLogger.d("PaymentViewModel", "IsoDep obtained, starting EMV read")
            val result = withContext(ioDispatcher) { EmvKernel.readCard(isoDep) }
            result.fold(
                onSuccess = { cardData ->
                    determineCvmState(cardData)
                },
                onFailure = { e ->
                    _uiState.value = PaymentUiState.NfcError(
                        message = e.message ?: "Card read error",
                        canRetryTap = true,
                        canRetrySubmit = false
                    )
                }
            )
        }
    }

    private fun determineCvmState(cardData: EmvCardData) {
        pendingNfcCardData = cardData
        pendingAccountNumber = cardData.pan
        pendingAccountType = cardData.accountType
        pendingExpiry = cardData.expiry
        pendingPaymentType = PaymentType.CARD_PRESENT

        when (cardData.cvmResult) {
            CvmResult.ONLINE_PIN -> {
                _uiState.value = PaymentUiState.NfcCvmRequired(CvmResult.ONLINE_PIN, cardData)
            }
            CvmResult.SIGNATURE -> {
                _uiState.value = PaymentUiState.NfcSubmitting
                viewModelScope.launch {
                    paymentService.processSale(
                        accountNumber      = cardData.pan,
                        accountType        = cardData.accountType,
                        expiry             = cardData.expiry,
                        totalAmountDollars = pendingAmountDollars
                    ).fold(
                        onSuccess = { saleResponse ->
                            addTransactionRecord(saleResponse)
                            _uiState.value = PaymentUiState.NfcCvmRequired(CvmResult.SIGNATURE, cardData)
                        },
                        onFailure = { e ->
                            _uiState.value = PaymentUiState.NfcError(
                                message = e.message ?: "Payment failed",
                                canRetryTap = false,
                                canRetrySubmit = true,
                                retryCount = nfcRetryCount
                            )
                        }
                    )
                }
            }
            CvmResult.NO_CVM, CvmResult.CDCVM -> {
                _uiState.value = PaymentUiState.NfcSubmitting
                viewModelScope.launch {
                    paymentService.processSale(
                        accountNumber      = cardData.pan,
                        accountType        = cardData.accountType,
                        expiry             = cardData.expiry,
                        totalAmountDollars = pendingAmountDollars
                    ).fold(
                        onSuccess = { saleResponse ->
                            addTransactionRecord(saleResponse)
                            _uiState.value = PaymentUiState.Success(
                                result      = saleResponse,
                                paymentType = PaymentType.CARD_PRESENT
                            )
                        },
                        onFailure = { e ->
                            _uiState.value = PaymentUiState.NfcError(
                                message = e.message ?: "Payment failed",
                                canRetryTap = false,
                                canRetrySubmit = true,
                                retryCount = nfcRetryCount
                            )
                        }
                    )
                }
            }
        }
    }

    fun retryNfcSubmission() {
        if (nfcRetryCount >= 3) {
            _uiState.value = PaymentUiState.NfcError(
                message = "Please tap card again",
                canRetryTap = true,
                canRetrySubmit = false,
                retryCount = nfcRetryCount
            )
            return
        }
        nfcRetryCount++
        val cardData = pendingNfcCardData ?: run {
            _uiState.value = PaymentUiState.NfcError(
                message = "Please tap card again",
                canRetryTap = true,
                canRetrySubmit = false,
                retryCount = nfcRetryCount
            )
            return
        }
        _uiState.value = PaymentUiState.NfcSubmitting
        viewModelScope.launch {
            paymentService.processSale(
                accountNumber      = cardData.pan,
                accountType        = cardData.accountType,
                expiry             = cardData.expiry,
                totalAmountDollars = pendingAmountDollars
            ).fold(
                onSuccess = { saleResponse ->
                    addTransactionRecord(saleResponse)
                    nfcRetryCount = 0
                    _uiState.value = PaymentUiState.Success(
                        result      = saleResponse,
                        paymentType = PaymentType.CARD_PRESENT
                    )
                },
                onFailure = { e ->
                    _uiState.value = PaymentUiState.NfcError(
                        message = e.message ?: "Payment failed",
                        canRetryTap = nfcRetryCount >= 3,
                        canRetrySubmit = nfcRetryCount < 3,
                        retryCount = nfcRetryCount
                    )
                }
            )
        }
    }

    fun submitPin(pin: String) {
        if (pin.length == 4 && pin.all { it.isDigit() }) {
            val currentState = _uiState.value
            if (currentState is PaymentUiState.NfcCvmRequired && currentState.cvm == CvmResult.ONLINE_PIN) {
                // NFC Online PIN path: submit the NFC sale
                val cardData = currentState.cardData
                _uiState.value = PaymentUiState.NfcSubmitting
                viewModelScope.launch {
                    paymentService.processSale(
                        accountNumber      = cardData.pan,
                        accountType        = cardData.accountType,
                        expiry             = cardData.expiry,
                        totalAmountDollars = pendingAmountDollars
                    ).fold(
                        onSuccess = { saleResponse ->
                            addTransactionRecord(saleResponse)
                            _uiState.value = PaymentUiState.Success(
                                result      = saleResponse,
                                paymentType = PaymentType.CARD_PRESENT
                            )
                        },
                        onFailure = { e ->
                            _uiState.value = PaymentUiState.NfcError(
                                message = e.message ?: "Payment failed",
                                canRetryTap = false,
                                canRetrySubmit = true,
                                retryCount = nfcRetryCount
                            )
                        }
                    )
                }
            } else {
                _uiState.value = PaymentUiState.SignatureCapture
            }
        }
    }

    fun confirmSignature() {
        _uiState.value = PaymentUiState.Processing
        viewModelScope.launch {
            paymentService.processSale(
                accountNumber      = pendingAccountNumber,
                accountType        = pendingAccountType,
                expiry             = pendingExpiry,
                totalAmountDollars = pendingAmountDollars
            ).fold(
                onSuccess = { saleResponse ->
                    addTransactionRecord(saleResponse)
                    _uiState.value = PaymentUiState.Success(
                        result      = saleResponse,
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
        if (_uiState.value is PaymentUiState.InitError) initToken()
        else _uiState.value = PaymentUiState.SelectPaymentType
    }

    private fun addTransactionRecord(response: SaleResponse) {
        transactionStore.addTransaction(
            TransactionRecord(
                transactionId  = response.transactionId,
                amount         = AmountUtils.centsToDisplay(response.approvedAmount),
                amountCents    = response.approvedAmount.toIntOrNull() ?: 0,
                feeAmount      = AmountUtils.centsToDisplay(response.feeAmount),
                dateTime       = LocalDateTime.now(),
                paymentType    = pendingPaymentType,
                status         = TransactionStatus.APPROVED,
                approvalNumber = response.approvalNumber,
                accountLast4   = response.accountLast4,
                accountType    = response.accountType
            )
        )
    }
}
