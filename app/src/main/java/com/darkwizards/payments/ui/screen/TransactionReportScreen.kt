package com.darkwizards.payments.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkwizards.payments.data.model.PaymentType
import com.darkwizards.payments.data.model.TransactionRecord
import com.darkwizards.payments.data.model.TransactionStatus
import com.darkwizards.payments.ui.viewmodel.TransactionViewModel
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * Sorts a list of [TransactionRecord] objects in reverse chronological order
 * (most recent first, by [TransactionRecord.dateTime] descending).
 *
 * This is a pure function extracted to enable property-based testing.
 *
 * @param transactions The list of transaction records to sort.
 * @return A new list sorted by [TransactionRecord.dateTime] in descending order.
 */
fun sortTransactionsReverseChronological(transactions: List<TransactionRecord>): List<TransactionRecord> {
    return transactions.sortedByDescending { it.dateTime }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReportScreen(
    viewModel: TransactionViewModel,
    onTransactionClick: (String) -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val sortedTransactions = sortTransactionsReverseChronological(transactions)

    // Bottom sheet state
    var selectedTransactionId by remember { mutableStateOf<String?>(null) }
    var showSendReceiptContact by remember { mutableStateOf(false) }
    var contactInput by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Transaction Report",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (sortedTransactions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No transactions yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sortedTransactions) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        onClick = {
                            // Show action sheet instead of navigating directly
                            selectedTransactionId = transaction.transactionId
                            showSendReceiptContact = false
                            contactInput = ""
                        }
                    )
                }
            }
        }
    }

    // ── Action sheet (ModalBottomSheet) ───────────────────────────────────────
    // Shown when a transaction row is tapped. Offers "View Details" and "Send Receipt".

    if (selectedTransactionId != null) {
        ModalBottomSheet(
            onDismissRequest = {
                selectedTransactionId = null
                showSendReceiptContact = false
                contactInput = ""
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!showSendReceiptContact) {
                    // ── Main action options ───────────────────────────────────

                    Text(
                        text = "Transaction Actions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // "View Details" — navigates to TransactionDetail
                    Button(
                        onClick = {
                            val txId = selectedTransactionId ?: return@Button
                            coroutineScope.launch {
                                sheetState.hide()
                            }.invokeOnCompletion {
                                selectedTransactionId = null
                                onTransactionClick(txId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("View Details")
                    }

                    // "Send Receipt" — shows inline contact entry
                    OutlinedButton(
                        onClick = {
                            showSendReceiptContact = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Send Receipt")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                } else {
                    // ── Inline contact entry (Send Receipt) ──────────────────

                    Text(
                        text = "Send Receipt",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = contactInput,
                        onValueChange = { contactInput = it },
                        label = { Text("Email or phone number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // "Send" — prototype only, dismisses the sheet (no network call)
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    sheetState.hide()
                                }.invokeOnCompletion {
                                    selectedTransactionId = null
                                    showSendReceiptContact = false
                                    contactInput = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Send")
                        }

                        // "Cancel" — returns to the main action options
                        OutlinedButton(
                            onClick = {
                                showSendReceiptContact = false
                                contactInput = ""
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Cancel")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionRecord,
    onClick: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")
    val statusColor = when (transaction.status) {
        TransactionStatus.APPROVED -> MaterialTheme.colorScheme.primary
        TransactionStatus.DECLINED -> MaterialTheme.colorScheme.error
        TransactionStatus.VOIDED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        TransactionStatus.REFUNDED -> MaterialTheme.colorScheme.tertiary
    }
    val paymentTypeLabel = when (transaction.paymentType) {
        PaymentType.CARD_PRESENT -> "Card Present"
        PaymentType.CARD_NOT_PRESENT -> "Card Not Present"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = transaction.transactionId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = transaction.amount,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = transaction.dateTime.format(formatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = paymentTypeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = transaction.status.name,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
        }
    }
}
