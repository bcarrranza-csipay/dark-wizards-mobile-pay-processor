package com.darkwizards.payments.ui.screen

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkwizards.payments.data.model.CvmResult
import com.darkwizards.payments.data.model.NfcAvailability
import com.darkwizards.payments.data.model.PaymentUiState
import com.darkwizards.payments.ui.theme.LocalColorTokens
import com.darkwizards.payments.ui.viewmodel.PaymentViewModel
import com.darkwizards.payments.util.NfcLogger

@Composable
fun TapScreen(
    viewModel: PaymentViewModel,
    nfcAdapter: NfcAdapter?,
    activity: Activity,
    onNavigateToPinEntry: () -> Unit,
    onNavigateToSignature: () -> Unit,
    onNavigateToResult: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val colorTokens = LocalColorTokens.current

    // Debug log overlay state
    var showLogs by remember { mutableStateOf(false) }

    // Guard: prevents stale Success state from a previous transaction
    // from immediately navigating to receipt before NFC flow starts.
    var nfcFlowActive by remember { mutableStateOf(false) }

    // Amount is already registered by PaymentTypeScreen via submitCardPresent().
    // Start NFC availability check immediately on screen entry.
    LaunchedEffect(Unit) {
        viewModel.checkNfcAvailability(context)
    }

    // Activate the navigation guard only once we're in an NFC-specific state
    // (not a stale Success/Error from a previous transaction)
    LaunchedEffect(uiState) {
        when (uiState) {
            is PaymentUiState.NfcWaiting,
            is PaymentUiState.NfcReading,
            is PaymentUiState.NfcSubmitting,
            is PaymentUiState.NfcHardwareUnavailable,
            is PaymentUiState.NfcTimeout,
            is PaymentUiState.NfcError,
            is PaymentUiState.NfcCvmRequired -> {
                nfcFlowActive = true
            }
            else -> { /* don't activate yet — might be stale state */ }
        }
    }

    // Enable NFC reader mode on entry, disable on exit
    DisposableEffect(Unit) {
        val callback = NfcAdapter.ReaderCallback { tag: Tag ->
            Handler(Looper.getMainLooper()).post {
                viewModel.onNfcTagDiscovered(tag)
            }
        }
        nfcAdapter?.enableReaderMode(
            activity,
            callback,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
        onDispose {
            nfcAdapter?.disableReaderMode(activity)
        }
    }

    // Navigation side-effects — only fires after NFC flow is active
    LaunchedEffect(uiState, nfcFlowActive) {
        if (!nfcFlowActive) return@LaunchedEffect
        when (val state = uiState) {
            is PaymentUiState.NfcCvmRequired -> {
                when (state.cvm) {
                    CvmResult.ONLINE_PIN -> onNavigateToPinEntry()
                    CvmResult.SIGNATURE  -> onNavigateToSignature()
                    else                 -> { /* NO_CVM / CDCVM handled by ViewModel */ }
                }
            }
            is PaymentUiState.Success -> onNavigateToResult()
            else -> { /* no navigation */ }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val state = uiState) {

            // ── NFC hardware unavailable ──────────────────────────────────────
            is PaymentUiState.NfcHardwareUnavailable -> {
                when (state.availability) {
                    is NfcAvailability.NoHardware -> {
                        Text(
                            text = "NFC is not available on this device",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                    is NfcAvailability.Disabled -> {
                        Text(
                            text = "NFC is disabled",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_NFC_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        ) {
                            Text("Open NFC Settings")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorTokens.spinnerColor
                            )
                        ) {
                            Text("Go Back")
                        }
                    }
                    is NfcAvailability.Available -> {
                        // Transitional — checkNfcAvailability will move to NfcWaiting
                        CircularProgressIndicator(color = colorTokens.spinnerColor)
                    }
                }
            }

            // ── Waiting for tap ───────────────────────────────────────────────
            is PaymentUiState.NfcWaiting -> {
                Text(
                    text = "Tap-to-Pay",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(32.dp))

                val infiniteTransition = rememberInfiniteTransition(label = "nfc_rotation")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "nfc_icon_rotation"
                )
                Icon(
                    imageVector = Icons.Default.Contactless,
                    contentDescription = "NFC contactless icon",
                    tint = colorTokens.tapIconColor,
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Hold card or phone near the back of this device",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorTokens.spinnerColor
                    )
                ) {
                    Text("Cancel")
                }
            }

            // ── Reading card (EMV dialogue in progress) ───────────────────────
            is PaymentUiState.NfcReading -> {
                CircularProgressIndicator(
                    color = colorTokens.spinnerColor,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Reading card…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            }

            // ── Submitting to Pyxis ───────────────────────────────────────────
            is PaymentUiState.NfcSubmitting -> {
                CircularProgressIndicator(
                    color = colorTokens.spinnerColor,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Processing payment…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            }

            // ── CVM required — navigation handled by LaunchedEffect above ─────
            is PaymentUiState.NfcCvmRequired -> {
                CircularProgressIndicator(
                    color = colorTokens.spinnerColor,
                    modifier = Modifier.size(64.dp)
                )
            }

            // ── Timeout ───────────────────────────────────────────────────────
            is PaymentUiState.NfcTimeout -> {
                Text(
                    text = "No card detected — the session timed out.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.checkNfcAvailability(context) }
                ) {
                    Text("Try Again")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorTokens.spinnerColor
                    )
                ) {
                    Text("Cancel")
                }
            }

            // ── NFC error ─────────────────────────────────────────────────────
            is PaymentUiState.NfcError -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                when {
                    state.canRetryTap -> {
                        Button(
                            onClick = { viewModel.checkNfcAvailability(context) }
                        ) {
                            Text("Try Again")
                        }
                    }
                    state.canRetrySubmit -> {
                        Button(
                            onClick = { viewModel.retryNfcSubmission() }
                        ) {
                            Text("Retry")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorTokens.spinnerColor
                    )
                ) {
                    Text("Cancel")
                }
            }

            // ── Success — navigation handled by LaunchedEffect above ──────────
            is PaymentUiState.Success -> {
                CircularProgressIndicator(
                    color = colorTokens.spinnerColor,
                    modifier = Modifier.size(64.dp)
                )
            }

            // ── Transaction declined ──────────────────────────────────────────
            is PaymentUiState.Error -> {
                Text(
                    text = "Transaction Declined: Please contact your bank or try a different card.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorTokens.button1Color
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                ) {
                    Text("Try Different Payment Method")
                }
            }

            // ── All other states (CardPresentEntry, Loading, etc.) ────────────
            // Show a brief spinner — checkNfcAvailability() called on entry
            // will transition to NfcWaiting almost immediately.
            else -> {
                CircularProgressIndicator(
                    color = colorTokens.spinnerColor,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    } // end Column

        // ── Back arrow — top-left corner ──────────────────────────────────
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Navigate back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        // ── Debug log button — top-right corner ───────────────────────────
        IconButton(
            onClick = { showLogs = !showLogs },
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

        // ── Log overlay ───────────────────────────────────────────────────
        if (showLogs) {
            val logs = NfcLogger.getLines()
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
                        text = "NFC Debug Log (${logs.size} lines)",
                        color = Color.Yellow,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    if (logs.isEmpty()) {
                        Text(
                            text = "No logs yet — tap a card to generate logs",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        logs.reversed().forEach { line ->
                            val color = when {
                                line.startsWith("[") && line.contains("] E/") -> Color(0xFFFF6B6B)
                                line.startsWith("[") && line.contains("] D/") -> Color(0xFFADD8E6)
                                else -> Color.White
                            }
                            Text(
                                text = line,
                                color = color,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
                // Close + Clear buttons
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Button(
                        onClick = { NfcLogger.clear() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.size(width = 80.dp, height = 32.dp)
                    ) {
                        Text("Clear", fontSize = 10.sp, color = Color.White)
                    }
                }
                IconButton(
                    onClick = { showLogs = false },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close logs", tint = Color.White)
                }
            }
        }
    } // end Box
}
