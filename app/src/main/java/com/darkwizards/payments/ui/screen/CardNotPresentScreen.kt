package com.darkwizards.payments.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.darkwizards.payments.data.model.PaymentUiState
import com.darkwizards.payments.ui.viewmodel.PaymentViewModel

@Composable
fun CardNotPresentScreen(
    viewModel: PaymentViewModel,
    onNavigateToPinEntry: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var cardNumber by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    var cardNumberError by remember { mutableStateOf(false) }
    var expiryError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    // Navigate when state transitions to PinEntry
    LaunchedEffect(uiState) {
        if (uiState is PaymentUiState.PinEntry) {
            onNavigateToPinEntry()
        }
    }

    val errorMessage = (uiState as? PaymentUiState.CardNotPresentEntry)?.error

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        errorContainerColor = MaterialTheme.colorScheme.surfaceVariant
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Manual Card Entry",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = cardNumber,
            onValueChange = {
                cardNumber = it
                cardNumberError = false
            },
            label = { Text("Card Number") },
            isError = cardNumberError,
            supportingText = if (cardNumberError) {{ Text("Card number is required") }} else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = expiryDate,
            onValueChange = {
                expiryDate = it
                expiryError = false
            },
            label = { Text("Expiration Date (MM.YYYY)") },
            isError = expiryError,
            supportingText = if (expiryError) {{ Text("Expiration date is required") }} else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = cvv,
            onValueChange = { cvv = it },
            label = { Text("CVV") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = {
                amount = it
                amountError = false
            },
            label = { Text("Amount ($)") },
            isError = amountError,
            supportingText = if (amountError) {{ Text("Amount is required") }} else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                cardNumberError = cardNumber.isBlank()
                expiryError = expiryDate.isBlank()
                amountError = amount.isBlank()
                if (!cardNumberError && !expiryError && !amountError) {
                    viewModel.submitCardNotPresent(cardNumber, expiryDate, cvv, amount)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Submit Payment", color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
