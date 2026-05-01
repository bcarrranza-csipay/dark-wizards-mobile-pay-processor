package com.darkwizards.payments.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkwizards.payments.data.model.PaymentUiState
import com.darkwizards.payments.ui.theme.LocalColorTokens
import com.darkwizards.payments.ui.viewmodel.PaymentViewModel
import com.darkwizards.payments.ui.viewmodel.SettingsViewModel

// ── ExpiryVisualTransformation ────────────────────────────────────────────────

/**
 * A [VisualTransformation] that inserts "/" after position 2 in the displayed text.
 *
 * The raw stored value is digits only (MMYY format). The displayed value shows MM/YY.
 *
 * Examples:
 *   raw "12"   → displayed "12"
 *   raw "1225" → displayed "12/25"
 *   raw "1"    → displayed "1"
 *
 * **Validates: Requirements 14.3**
 */
class ExpiryVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val transformed = buildString {
            raw.forEachIndexed { index, char ->
                if (index == 2) append('/')
                append(char)
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return when {
                    offset <= 2 -> offset
                    else -> offset + 1  // account for the inserted "/"
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                return when {
                    offset <= 2 -> offset
                    offset == 3 -> 2  // the "/" position maps back to position 2
                    else -> offset - 1
                }
            }
        }

        return TransformedText(AnnotatedString(transformed), offsetMapping)
    }
}

// ── Pure validation functions (extracted for property testing) ────────────────

/**
 * Validates the manual entry form fields and returns a map of field name → error message.
 *
 * Fields validated:
 * - "cardNumber": must be non-empty
 * - "expiry": raw digits (MMYY) must be exactly 4 digits representing a valid MM/YY
 * - "cvv": must be non-empty
 * - "zip" (when [avsEnabled]): must be exactly 5 digits
 *
 * Returns an empty map when all fields are valid.
 *
 * @param cardNumber  Raw card number string.
 * @param expiry      Raw expiry digits (MMYY, no slash).
 * @param cvv         CVV string.
 * @param zip         ZIP code string (only validated when [avsEnabled] is true).
 * @param avsEnabled  Whether AVS fields are shown and should be validated.
 * @return Map of field key → error message. Empty map means all fields are valid.
 *
 * **Validates: Requirements 14.5, 14.6, 14.7**
 */
fun validateManualEntryForm(
    cardNumber: String,
    expiry: String,
    cvv: String,
    zip: String,
    avsEnabled: Boolean,
    cardholderName: String = "",
    streetAddress: String = "",
    city: String = "",
    state: String = ""
): Map<String, String> {
    val errors = mutableMapOf<String, String>()

    if (cardNumber.isBlank()) {
        errors["cardNumber"] = "Card number is required"
    }

    val expiryError = validateExpiry(expiry)
    if (expiryError != null) {
        errors["expiry"] = expiryError
    }

    if (cvv.isBlank()) {
        errors["cvv"] = "CVV is required"
    }

    if (avsEnabled) {
        if (cardholderName.isBlank()) {
            errors["cardholderName"] = "Cardholder name is required"
        }
        if (streetAddress.isBlank()) {
            errors["streetAddress"] = "Street address is required"
        }
        if (city.isBlank()) {
            errors["city"] = "City is required"
        }
        if (state.isBlank()) {
            errors["state"] = "State is required"
        }
        if (!isValidZip(zip)) {
            errors["zip"] = "Invalid ZIP"
        }
    }

    return errors
}

/**
 * Validates the expiry raw digits string (MMYY format, no slash).
 *
 * Returns null if valid, or an error message string if invalid.
 *
 * Valid: exactly 4 digits where MM is 01–12.
 *
 * **Validates: Requirements 14.6**
 */
fun validateExpiry(expiry: String): String? {
    if (expiry.length != 4 || !expiry.all { it.isDigit() }) {
        return "Expiry must be MM/YY"
    }
    val month = expiry.substring(0, 2).toIntOrNull() ?: return "Expiry must be MM/YY"
    if (month < 1 || month > 12) {
        return "Expiry must be MM/YY"
    }
    return null
}

/**
 * Returns true if [zip] is exactly 5 numeric digits.
 *
 * **Validates: Requirements 14.7**
 */
fun isValidZip(zip: String): Boolean {
    return zip.length == 5 && zip.all { it.isDigit() }
}

/**
 * Returns whether AVS fields should be shown based on the toggle state.
 *
 * **Validates: Requirements 7.2, 7.3**
 */
fun shouldShowAvsFields(avsEnabled: Boolean): Boolean = avsEnabled

/**
 * Converts a raw expiry string from MMYY to MM.YYYY format.
 *
 * Example: "1225" → "12.2025"
 *
 * @param mmyy  Raw 4-digit expiry string (MMYY).
 * @return Formatted expiry string in MM.YYYY format.
 */
fun convertExpiryToMmYyyy(mmyy: String): String {
    require(mmyy.length == 4) { "Expiry must be exactly 4 digits (MMYY)" }
    val month = mmyy.substring(0, 2)
    val year2 = mmyy.substring(2, 4).toInt()
    val year4 = 2000 + year2
    return "$month.$year4"
}

// ── Screen composable ─────────────────────────────────────────────────────────

/**
 * Customer-facing manual card entry screen (Card Not Present flow).
 *
 * Displays the confirmed total amount at the top, followed by card entry fields.
 * When AVS is enabled via [SettingsViewModel], additional billing address fields are shown.
 *
 * Inline validation fires on submit attempt and on field blur. Errors clear when the
 * field value becomes valid.
 *
 * On valid submit: converts expiry from MMYY to MM.YYYY, then calls
 * [PaymentViewModel.submitCardNotPresent].
 *
 * Navigates to PinEntry when [PaymentViewModel.uiState] transitions to [PaymentUiState.PinEntry].
 *
 * @param totalAmountCents      Final total in cents (displayed at top of screen).
 * @param settingsViewModel     Provides AVS toggle state.
 * @param paymentViewModel      Used to call [PaymentViewModel.submitCardNotPresent] on valid submit.
 * @param onNavigateToPinEntry  Navigates to [PinEntryScreen] after successful submission.
 * @param onNavigateBack        Navigates back to [PaymentTypeScreen].
 *
 * _Requirements: 14.1–14.9, 16.1–16.4_
 */
@Composable
fun ManualEntryScreen(
    totalAmountCents: Int,
    settingsViewModel: SettingsViewModel,
    paymentViewModel: PaymentViewModel,
    onNavigateToPinEntry: () -> Unit,
    onNavigateToSignature: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val tokens = LocalColorTokens.current
    val settingsState by settingsViewModel.state.collectAsState()
    val uiState by paymentViewModel.uiState.collectAsState()

    val avsEnabled = settingsState.avsEnabled
    val formattedTotal = formatCentsDisplay(totalAmountCents)
    val totalAmountDollarString = "%.2f".format(totalAmountCents / 100.0)

    // ── Field state ───────────────────────────────────────────────────────────

    var cardNumber by remember { mutableStateOf("") }
    // Raw expiry digits only (MMYY), no slash — ExpiryVisualTransformation handles display
    var expiryRaw by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    // AVS fields
    var cardholderName by remember { mutableStateOf("") }
    var streetAddress by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var zip by remember { mutableStateOf("") }

    // ── Error state ───────────────────────────────────────────────────────────

    var cardNumberError by remember { mutableStateOf<String?>(null) }
    var expiryError by remember { mutableStateOf<String?>(null) }
    var cvvError by remember { mutableStateOf<String?>(null) }
    var cardholderNameError by remember { mutableStateOf<String?>(null) }
    var streetAddressError by remember { mutableStateOf<String?>(null) }
    var cityError by remember { mutableStateOf<String?>(null) }
    var stateError by remember { mutableStateOf<String?>(null) }
    var zipError by remember { mutableStateOf<String?>(null) }

    // Whether the user has attempted to submit (enables blur-time validation)
    var submitAttempted by remember { mutableStateOf(false) }
    // Guard: only navigate after the user has submitted the form in THIS session
    var formSubmitted by remember { mutableStateOf(false) }

    // ── Navigation ────────────────────────────────────────────────────────────

    LaunchedEffect(uiState, formSubmitted) {
        if (!formSubmitted) return@LaunchedEffect
        when (uiState) {
            is PaymentUiState.PinEntry -> onNavigateToPinEntry()
            is PaymentUiState.SignatureCapture -> onNavigateToSignature()
            else -> { /* no navigation */ }
        }
    }

    // ── Transaction declined state ────────────────────────────────────────────
    // When the payment service returns an error, show the declined message
    // in place of the normal form content (Requirements 17.1–17.3).
    if (uiState is PaymentUiState.Error) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.backgroundColor)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Transaction Declined: Please contact your bank or try a different card.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.button1Color,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Try Different Payment Method",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        return
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun validateAndUpdateErrors(): Boolean {
        val errors = validateManualEntryForm(
            cardNumber = cardNumber,
            expiry = expiryRaw,
            cvv = cvv,
            zip = zip,
            avsEnabled = avsEnabled,
            cardholderName = cardholderName,
            streetAddress = streetAddress,
            city = city,
            state = state
        )
        cardNumberError = errors["cardNumber"]
        expiryError = errors["expiry"]
        cvvError = errors["cvv"]
        cardholderNameError = errors["cardholderName"]
        streetAddressError = errors["streetAddress"]
        cityError = errors["city"]
        stateError = errors["state"]
        zipError = errors["zip"]
        return errors.isEmpty()
    }

    // ── Text field colors ─────────────────────────────────────────────────────

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White.copy(alpha = 0.1f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.07f),
        focusedBorderColor = tokens.button1Color,
        unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color.White.copy(alpha = 0.8f),
        unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
        errorContainerColor = Color.White.copy(alpha = 0.1f),
        errorBorderColor = MaterialTheme.colorScheme.error,
        errorTextColor = Color.White,
        errorLabelColor = MaterialTheme.colorScheme.error
    )

    // ── Layout ────────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back arrow row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Total amount display (smaller but visible — Requirement 14.1)
        Text(
            text = formattedTotal,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Enter card details",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Card Number ───────────────────────────────────────────────────────

        OutlinedTextField(
            value = cardNumber,
            onValueChange = { value ->
                cardNumber = value
                // Clear error when field becomes valid
                if (value.isNotBlank()) cardNumberError = null
                else if (submitAttempted) cardNumberError = "Card number is required"
            },
            label = { Text("Card Number") },
            isError = cardNumberError != null,
            supportingText = cardNumberError?.let { err -> { Text(err) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && submitAttempted) {
                        cardNumberError = if (cardNumber.isBlank()) "Card number is required" else null
                    }
                },
            colors = textFieldColors,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Expiry Date ───────────────────────────────────────────────────────

        OutlinedTextField(
            value = expiryRaw,
            onValueChange = { value ->
                // Only allow digits, max 4 characters (MMYY)
                val digits = value.filter { it.isDigit() }.take(4)
                expiryRaw = digits
                // Clear error when field becomes valid
                val err = validateExpiry(digits)
                if (err == null) expiryError = null
                else if (submitAttempted) expiryError = err
            },
            label = { Text("Expiry Date (MM/YY)") },
            isError = expiryError != null,
            supportingText = expiryError?.let { err -> { Text(err) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = ExpiryVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        // Validate on blur (Requirement 16.3)
                        val err = validateExpiry(expiryRaw)
                        if (err != null) expiryError = err
                        else expiryError = null
                    }
                },
            colors = textFieldColors,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── CVV ───────────────────────────────────────────────────────────────

        OutlinedTextField(
            value = cvv,
            onValueChange = { value ->
                cvv = value
                // Clear error when field becomes valid
                if (value.isNotBlank()) cvvError = null
                else if (submitAttempted) cvvError = "CVV is required"
            },
            label = { Text("CVV") },
            isError = cvvError != null,
            supportingText = cvvError?.let { err -> { Text(err) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && submitAttempted) {
                        cvvError = if (cvv.isBlank()) "CVV is required" else null
                    }
                },
            colors = textFieldColors,
            singleLine = true
        )

        // ── AVS Fields (conditional) ──────────────────────────────────────────

        if (shouldShowAvsFields(avsEnabled)) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Billing Address",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Cardholder Name
            OutlinedTextField(
                value = cardholderName,
                onValueChange = {
                    cardholderName = it
                    if (it.isNotBlank()) cardholderNameError = null
                    else if (submitAttempted) cardholderNameError = "Cardholder name is required"
                },
                label = { Text("Cardholder Name") },
                isError = cardholderNameError != null,
                supportingText = cardholderNameError?.let { err -> { Text(err) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && submitAttempted) {
                            cardholderNameError = if (cardholderName.isBlank()) "Cardholder name is required" else null
                        }
                    },
                colors = textFieldColors,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Street Address
            OutlinedTextField(
                value = streetAddress,
                onValueChange = {
                    streetAddress = it
                    if (it.isNotBlank()) streetAddressError = null
                    else if (submitAttempted) streetAddressError = "Street address is required"
                },
                label = { Text("Street Address") },
                isError = streetAddressError != null,
                supportingText = streetAddressError?.let { err -> { Text(err) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && submitAttempted) {
                            streetAddressError = if (streetAddress.isBlank()) "Street address is required" else null
                        }
                    },
                colors = textFieldColors,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // City
            OutlinedTextField(
                value = city,
                onValueChange = {
                    city = it
                    if (it.isNotBlank()) cityError = null
                    else if (submitAttempted) cityError = "City is required"
                },
                label = { Text("City") },
                isError = cityError != null,
                supportingText = cityError?.let { err -> { Text(err) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && submitAttempted) {
                            cityError = if (city.isBlank()) "City is required" else null
                        }
                    },
                colors = textFieldColors,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // State
            OutlinedTextField(
                value = state,
                onValueChange = {
                    state = it
                    if (it.isNotBlank()) stateError = null
                    else if (submitAttempted) stateError = "State is required"
                },
                label = { Text("State") },
                isError = stateError != null,
                supportingText = stateError?.let { err -> { Text(err) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && submitAttempted) {
                            stateError = if (state.isBlank()) "State is required" else null
                        }
                    },
                colors = textFieldColors,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ZIP Code
            OutlinedTextField(
                value = zip,
                onValueChange = { value ->
                    zip = value
                    // Clear error when field becomes valid
                    if (isValidZip(value)) zipError = null
                    else if (submitAttempted) zipError = "Invalid ZIP"
                },
                label = { Text("ZIP Code") },
                isError = zipError != null,
                supportingText = zipError?.let { err -> { Text(err) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && submitAttempted) {
                            zipError = if (!isValidZip(zip)) "Invalid ZIP" else null
                        }
                    },
                colors = textFieldColors,
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Submit Payment pill button ─────────────────────────────────────────

        Button(
            onClick = {
                submitAttempted = true
                val isValid = validateAndUpdateErrors()
                if (isValid) {
                    formSubmitted = true
                    val formattedExpiry = convertExpiryToMmYyyy(expiryRaw)
                    paymentViewModel.submitCardNotPresent(
                        cardNumber = cardNumber,
                        expiry = formattedExpiry,
                        cvv = cvv,
                        amount = totalAmountDollarString
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.button1Color,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Submit Payment",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
