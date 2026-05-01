package com.darkwizards.payments.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.darkwizards.payments.data.model.PaymentType
import com.darkwizards.payments.data.model.TransactionRecord
import com.darkwizards.payments.data.model.TransactionStatus
import com.darkwizards.payments.ui.viewmodel.TransactionViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
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
    transactions: List<TransactionRecord> = emptyList(),
    onTransactionClick: (String) -> Unit
) {
    // Read the version counter — this is a simple Int StateFlow that increments
    // on every store mutation. collectAsState on a primitive Int is reliable
    // even inside NavHost composable blocks.
    val storeVersion by viewModel.storeVersion.collectAsState()

    // Read the actual transaction list directly from the store on every recomposition.
    // No remember, no caching — always fresh.
    val currentTransactions = viewModel.getTransactionSnapshot()
    val sortedTransactions = sortTransactionsReverseChronological(currentTransactions)

    // Fetch latest transactions from server on EVERY recomposition triggered by version change.
    // This is aggressive but guarantees freshness.
    LaunchedEffect(storeVersion) {
        // Small delay to let any in-flight server writes complete
        kotlinx.coroutines.delay(500L)
        viewModel.loadTransactionsFromServer()
    }

    // Bottom sheet state
    var selectedTransactionId by remember { mutableStateOf<String?>(null) }
    var showSendReceiptContact by remember { mutableStateOf(false) }
    var contactInput by remember { mutableStateOf("") }

    var showDebugLogs by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (currentTransactions.size > 30)
                "Showing 30 of ${currentTransactions.size} transactions"
            else
                "${sortedTransactions.size} transaction${if (sortedTransactions.size != 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(12.dp))

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

        // ── Debug bug icon — top-right corner ─────────────────────────────
        IconButton(
            onClick = { showDebugLogs = !showDebugLogs },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(32.dp)
                .background(Color.Black.copy(alpha = 0.25f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "Debug logs",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }

        // ── Debug log overlay ──────────────────────────────────────────────
        if (showDebugLogs) {
            val newestTx = sortedTransactions.firstOrNull()
            val directStoreSize = viewModel.getTransactionSnapshot().size
            val allLogs = com.darkwizards.payments.util.NfcLogger.getLines()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 48.dp)
                ) {
                    Text(
                        text = "v$storeVersion | total=$directStoreSize | top=${newestTx?.transactionId?.take(8) ?: "none"} | ${newestTx?.dateTime?.toString()?.take(19) ?: ""}",
                        color = Color.Yellow,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    if (allLogs.isEmpty()) {
                        Text("No logs yet", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        allLogs.reversed().forEach { line ->
                            val color = when {
                                line.contains("] E/") -> Color(0xFFFF6B6B)
                                line.contains("] D/") -> Color(0xFFADD8E6)
                                else -> Color.White
                            }
                            Text(text = line, color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { com.darkwizards.payments.util.NfcLogger.clear() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.size(width = 80.dp, height = 32.dp)
                    ) { Text("Clear", fontSize = 10.sp, color = Color.White) }
                }
                IconButton(
                    onClick = { showDebugLogs = false },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    } // end Box

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

/**
 * Formats a [LocalDateTime] (stored in UTC) as a relative date string in the
 * device's local timezone:
 * - "Today HH:mm AM/PM" if the local date is today
 * - "Yesterday HH:mm AM/PM" if the local date is yesterday
 * - "MM/dd/yyyy HH:mm AM/PM" for all older dates
 */
private fun formatRelativeDate(dateTime: LocalDateTime): String {
    // Convert UTC stored time → device local timezone for display
    val localDateTime = dateTime
        .atZone(java.time.ZoneOffset.UTC)
        .withZoneSameInstant(java.time.ZoneId.systemDefault())
        .toLocalDateTime()

    val today = LocalDate.now()
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val formattedTime = localDateTime.format(timeFormatter)

    return when (localDateTime.toLocalDate()) {
        today -> "Today $formattedTime"
        today.minusDays(1) -> "Yesterday $formattedTime"
        else -> {
            val dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
            "${localDateTime.format(dateFormatter)} $formattedTime"
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionRecord,
    onClick: () -> Unit
) {
    val statusColor = when (transaction.status) {
        TransactionStatus.APPROVED -> Color(0xFF4CAF50)
        TransactionStatus.DECLINED -> Color(0xFFF44336)
        TransactionStatus.VOIDED -> Color(0xFF9E9E9E)
        TransactionStatus.REFUNDED -> Color(0xFFFF9800)
    }
    val paymentTypeLabel = when (transaction.paymentType) {
        PaymentType.CARD_PRESENT -> "Card Present"
        PaymentType.CARD_NOT_PRESENT -> "Card Not Present"
    }
    val cardInfo = "${transaction.accountType} •••• ${transaction.accountLast4}"
    val dateLabel = "${formatRelativeDate(transaction.dateTime)} · $paymentTypeLabel"

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
            // Row 1: Amount (left) + Status badge (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = transaction.amount,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = transaction.status.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Row 2: Card info
            Text(
                text = cardInfo,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            // Row 3: Relative date + payment type
            Text(
                text = dateLabel,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
