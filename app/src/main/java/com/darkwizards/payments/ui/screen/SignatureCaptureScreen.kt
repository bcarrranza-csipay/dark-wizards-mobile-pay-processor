package com.darkwizards.payments.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.darkwizards.payments.data.model.PaymentUiState
import com.darkwizards.payments.ui.viewmodel.PaymentViewModel

data class Line(val start: Offset, val end: Offset)

@Composable
fun SignatureCaptureScreen(
    viewModel: PaymentViewModel,
    onNavigateToResult: () -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val lines = remember { mutableStateListOf<Line>() }

    LaunchedEffect(uiState) {
        if (uiState is PaymentUiState.Success || uiState is PaymentUiState.Error) {
            onNavigateToResult()
        }
    }

    val isProcessing = uiState is PaymentUiState.Processing
    val strokeColor = Color(0xFFE0D0FF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Signature Capture",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sign below to confirm payment",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Drawing canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(isProcessing) {
                    if (!isProcessing) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val previous = change.previousPosition
                            val current = change.position
                            lines.add(Line(previous, current))
                        }
                    }
                }
        ) {
            lines.forEach { line ->
                drawLine(
                    color = strokeColor,
                    start = line.start,
                    end = line.end,
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (isProcessing) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Processing payment...", color = MaterialTheme.colorScheme.onBackground)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { lines.clear() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
                Button(
                    onClick = { viewModel.confirmSignature() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Confirm", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
