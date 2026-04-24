package com.darkwizards.payments.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.darkwizards.payments.data.model.PaymentType
import com.darkwizards.payments.data.model.TransactionStatus
import com.darkwizards.payments.ui.viewmodel.RefundState
import com.darkwizards.payments.ui.viewmodel.TransactionViewModel
import com.darkwizards.payments.util.AmountUtils
import java.time.format.DateTimeFormatter

@Composable
fun TransactionDetailScreen(
    viewModel: TransactionViewModel,
    transactionId: String,
    onBack: () -> Unit
) {
    val transaction by viewModel.selectedTransaction.collectAsState()
    val refundState by viewModel.refundState.collectAsState()

    LaunchedEffect(transactionId) {
        viewModel.selectTransaction(transactionId)
        viewModel.resetRefundState()
    }

    val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Transaction Detail",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        val tx = transaction
        if (tx == null) {
            Text(
                text = "Transaction not found",
                color = MaterialTheme.colorScheme.error
            )
        } else {
            val paymentTypeLabel = when (tx.paymentType) {
                PaymentType.CARD_PRESENT -> "Card Present"
                PaymentType.CARD_NOT_PRESENT -> "Card Not Present"
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    DetailRow("Transaction ID", tx.transactionId)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Amount", tx.amount)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Fee", tx.feeAmount)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Date/Time", tx.dateTime.format(formatter))
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Payment Type", paymentTypeLabel)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Status", tx.status.name)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Approval #", tx.approvalNumber)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Account", "${tx.accountType} •••• ${tx.accountLast4}")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Refund section
            val canRefund = tx.status == TransactionStatus.APPROVED

            when (refundState) {
                is RefundState.Idle -> {
                    Button(
                        onClick = { viewModel.initiateRefund(tx.transactionId) },
                        enabled = canRefund,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = if (canRefund) "Refund" else "Refund (${tx.status.name})",
                            color = if (canRefund) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                is RefundState.Loading -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Processing refund...",
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                is RefundState.Success -> {
                    val refund = (refundState as RefundState.Success).refundResponse
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Refund Success",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Refund Successful",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            DetailRow("Refund Transaction ID", refund.transactionId)
                            Spacer(modifier = Modifier.height(8.dp))
                            DetailRow("Refund Amount", AmountUtils.centsToDisplay(refund.approvedAmount))
                        }
                    }
                }
                is RefundState.Error -> {
                    val errorMsg = (refundState as RefundState.Error).message
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Refund Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Refund Failed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.initiateRefund(tx.transactionId) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Retry Refund", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
