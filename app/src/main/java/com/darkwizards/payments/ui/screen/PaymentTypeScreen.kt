package com.darkwizards.payments.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkwizards.payments.ui.theme.LocalColorTokens
import com.darkwizards.payments.ui.viewmodel.PaymentViewModel

/**
 * Customer-facing payment method selection screen (Card Present vs Card Not Present).
 *
 * Displays the confirmed total amount and lets the customer choose between tapping
 * their card (NFC) or entering card details manually.
 *
 * When "Card Present" is selected, [PaymentViewModel.submitCardPresent] is called with
 * [totalAmountDollars] before navigating so the amount is registered in the ViewModel.
 *
 * @param totalAmountCents   Final total (base + surcharge + tip) in cents.
 * @param totalAmountDollars Pre-formatted dollar string (e.g. "25.75") passed to
 *                           [PaymentViewModel.submitCardPresent] on Card Present selection.
 * @param paymentViewModel   Used to call [PaymentViewModel.submitCardPresent] before
 *                           navigating to the NFC screen.
 * @param onCardPresent      Navigates to [TapScreen] (Card Present flow).
 * @param onCardNotPresent   Navigates to [ManualEntryScreen] (Card Not Present flow).
 * @param onNavigateBack     Navigates back to [TotalAmountScreen].
 */
@Composable
fun PaymentTypeScreen(
    totalAmountCents: Int,
    totalAmountDollars: String,
    paymentViewModel: PaymentViewModel,
    onCardPresent: () -> Unit,
    onCardNotPresent: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val tokens = LocalColorTokens.current
    val formattedTotal = formatCentsDisplay(totalAmountCents)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.backgroundColor)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Back arrow ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Total amount display ──────────────────────────────────────────────
        Text(
            text = "Total",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = formattedTotal,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // ── Payment method prompt ─────────────────────────────────────────────
        Text(
            text = "How would you like to pay?",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Card Present button ───────────────────────────────────────────────
        Button(
            onClick = {
                paymentViewModel.submitCardPresent(totalAmountDollars)
                onCardPresent()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.button1Color,
                contentColor = Color.White
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Nfc,
                    contentDescription = "NFC",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Card Present",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tap to Pay",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Card Not Present button ───────────────────────────────────────────
        Button(
            onClick = onCardNotPresent,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.button2Color,
                contentColor = Color.White
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Manual Entry",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Card Not Present",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Manual Entry",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
