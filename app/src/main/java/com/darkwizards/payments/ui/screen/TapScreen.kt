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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darkwizards.payments.data.model.CvmResult
import com.darkwizards.payments.data.model.NfcAvailability
import com.darkwizards.payments.data.model.PaymentUiState
import com.darkwizards.payments.ui.viewmodel.PaymentViewModel

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

    // Check NFC availability on screen entry
    LaunchedEffect(Unit) {
        viewModel.checkNfcAvailability(context)
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

    // Navigation side-effects driven by uiState
    LaunchedEffect(uiState) {
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
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("Go Back")
                        }
                    }
                    is NfcAvailability.Available -> {
                        // Transitional — checkNfcAvailability will move to NfcWaiting
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                Spacer(modifier = Modifier.height(24.dp))

                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // pendingAmountDollars is exposed as internal currentAmountDollars
                OutlinedTextField(
                    value = viewModel.currentAmountDollars,
                    onValueChange = { /* read-only */ },
                    label = { Text("Amount ($)") },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(32.dp))

                // Animated NFC icon
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
                    tint = MaterialTheme.colorScheme.primary,
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
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Cancel")
                }
            }

            // ── Reading card (EMV dialogue in progress) ───────────────────────
            is PaymentUiState.NfcReading -> {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.primary,
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
                        containerColor = MaterialTheme.colorScheme.secondary
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
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Cancel")
                }
            }

            // ── Success — navigation handled by LaunchedEffect above ──────────
            is PaymentUiState.Success -> {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }

            // ── All other states (Loading, SelectPaymentType, etc.) ───────────
            else -> {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}
