package com.darkwizards.payments.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darkwizards.payments.R
import com.darkwizards.payments.data.model.PaymentType
import com.darkwizards.payments.data.model.PaymentUiState
import com.darkwizards.payments.ui.viewmodel.PaymentViewModel

@Composable
fun PaymentScreen(
    viewModel: PaymentViewModel,
    onCardPresent: () -> Unit,
    onCardNotPresent: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (uiState) {
            is PaymentUiState.Loading -> {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier.size(200.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Connecting to payment service...", color = MaterialTheme.colorScheme.onBackground)
            }
            is PaymentUiState.InitError -> {
                val error = (uiState as PaymentUiState.InitError).message
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.retry() }) {
                    Text("Retry")
                }
            }
            is PaymentUiState.SelectPaymentType -> {
                Text(
                    text = "Select Payment Type",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(32.dp))
                PaymentTypeCard(
                    title = "Card Present (Tap-to-Pay)",
                    description = "Simulate a contactless tap payment",
                    onClick = {
                        viewModel.selectPaymentType(PaymentType.CARD_PRESENT)
                        onCardPresent()
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                PaymentTypeCard(
                    title = "Card Not Present (Manual Entry)",
                    description = "Enter card details manually",
                    onClick = {
                        viewModel.selectPaymentType(PaymentType.CARD_NOT_PRESENT)
                        onCardNotPresent()
                    }
                )
            }
            else -> {
                // For other states, show selection (user navigated back)
                Text(
                    text = "Select Payment Type",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(32.dp))
                PaymentTypeCard(
                    title = "Card Present (Tap-to-Pay)",
                    description = "Simulate a contactless tap payment",
                    onClick = {
                        viewModel.selectPaymentType(PaymentType.CARD_PRESENT)
                        onCardPresent()
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                PaymentTypeCard(
                    title = "Card Not Present (Manual Entry)",
                    description = "Enter card details manually",
                    onClick = {
                        viewModel.selectPaymentType(PaymentType.CARD_NOT_PRESENT)
                        onCardNotPresent()
                    }
                )
            }
        }
    }
}

@Composable
private fun PaymentTypeCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f)
            )
        }
    }
}
