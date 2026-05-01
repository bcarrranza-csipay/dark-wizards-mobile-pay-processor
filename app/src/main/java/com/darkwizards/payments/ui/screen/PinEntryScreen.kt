package com.darkwizards.payments.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkwizards.payments.data.model.PaymentUiState
import com.darkwizards.payments.ui.viewmodel.PaymentViewModel

@Composable
fun PinEntryScreen(
    viewModel: PaymentViewModel,
    onNavigateToSignature: () -> Unit,
    onNavigateToResult: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var pin by remember { mutableStateOf("") }
    // Guard: only navigate after the user has entered a PIN in THIS session.
    // Prevents stale Success/SignatureCapture from a previous transaction
    // from immediately navigating away.
    var pinSubmitted by remember { mutableStateOf(false) }

    LaunchedEffect(uiState, pinSubmitted) {
        if (!pinSubmitted) return@LaunchedEffect
        when (uiState) {
            is PaymentUiState.SignatureCapture -> onNavigateToSignature()
            is PaymentUiState.Success -> onNavigateToResult()
            else -> { /* no navigation */ }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enter PIN",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(32.dp))

        // 4 masked digit indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(40.dp))

        // Numeric keypad
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.size(72.dp))
                    } else {
                        Button(
                            onClick = {
                                when (key) {
                                    "⌫" -> {
                                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    }
                                    else -> {
                                        if (pin.length < 4) {
                                            pin += key
                                            if (pin.length == 4) {
                                                pinSubmitted = true
                                                viewModel.submitPin(pin)
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(72.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text(
                                text = key,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
