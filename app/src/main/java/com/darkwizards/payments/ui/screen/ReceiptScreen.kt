package com.darkwizards.payments.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkwizards.payments.ui.theme.LocalColorTokens
import kotlinx.coroutines.delay

/**
 * Post-signature receipt delivery screen.
 *
 * **Initial state:** Displays "Get your receipt" heading, optional email and phone
 * [OutlinedTextField]s, a "Send" pill button ([Button1Color]) and a "Skip" button
 * ([Button2Color]).
 *
 * **Success state:** Triggered by tapping "Send" or "Skip". Shows a ✓ checkmark,
 * "Payment Complete" heading, and "Returning to merchant..." text. After a 2.5-second
 * delay, navigates to [MerchantPayScreen] with the customer back stack cleared.
 *
 * No network call is made — this is a prototype-only implementation (Requirement 15.4).
 *
 * @param onNavigateToMerchantPay  Navigates to [MerchantPayScreen], clearing the customer
 *                                  back stack (called after Send/Skip + 2.5 s delay).
 * @param onNavigateBack           Navigates back to [SignatureCaptureScreen].
 */
@Composable
fun ReceiptScreen(
    onNavigateToMerchantPay: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val tokens = LocalColorTokens.current

    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var sent  by remember { mutableStateOf(false) }

    // After transitioning to success state, wait 2.5 s then navigate to MerchantPay
    LaunchedEffect(sent) {
        if (sent) {
            delay(2500L)
            onNavigateToMerchantPay()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.backgroundColor)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!sent) {
            // ── Initial state ─────────────────────────────────────────────────

            // Back arrow
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Get your receipt",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter your email or phone number to receive a receipt (optional)",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Email field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address", color = Color.White.copy(alpha = 0.7f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor      = Color.White,
                    unfocusedTextColor    = Color.White,
                    focusedBorderColor    = tokens.button1Color,
                    unfocusedBorderColor  = Color.White.copy(alpha = 0.5f),
                    cursorColor           = Color.White,
                    focusedLabelColor     = tokens.button1Color,
                    unfocusedLabelColor   = Color.White.copy(alpha = 0.7f)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Phone field
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone number", color = Color.White.copy(alpha = 0.7f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor      = Color.White,
                    unfocusedTextColor    = Color.White,
                    focusedBorderColor    = tokens.button1Color,
                    unfocusedBorderColor  = Color.White.copy(alpha = 0.5f),
                    cursorColor           = Color.White,
                    focusedLabelColor     = tokens.button1Color,
                    unfocusedLabelColor   = Color.White.copy(alpha = 0.7f)
                )
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Send button (Button1Color)
            Button(
                onClick = { sent = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.button1Color,
                    contentColor   = Color.White
                )
            ) {
                Text(
                    text = "Send",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skip button (Button2Color)
            Button(
                onClick = { sent = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.button2Color,
                    contentColor   = Color.White
                )
            ) {
                Text(
                    text = "Skip",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

        } else {
            // ── Success state ─────────────────────────────────────────────────

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "✓",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Payment Complete",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Returning to merchant...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
