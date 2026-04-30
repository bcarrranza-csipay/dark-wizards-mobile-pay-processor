package com.darkwizards.payments.ui.navigation

import android.app.Activity
import android.nfc.NfcAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.darkwizards.payments.ui.screen.CardNotPresentScreen
import com.darkwizards.payments.ui.screen.PaymentScreen
import com.darkwizards.payments.ui.screen.PinEntryScreen
import com.darkwizards.payments.ui.screen.SignatureCaptureScreen
import com.darkwizards.payments.ui.screen.TapScreen
import com.darkwizards.payments.ui.screen.TransactionDetailScreen
import com.darkwizards.payments.ui.screen.TransactionReportScreen
import com.darkwizards.payments.ui.screen.TransactionResultScreen
import com.darkwizards.payments.ui.viewmodel.PaymentMode
import com.darkwizards.payments.ui.viewmodel.PaymentViewModel
import com.darkwizards.payments.ui.viewmodel.TransactionViewModel

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Pay",          Icons.Default.CreditCard, Screen.Payment.route),
    BottomNavItem("Transactions", Icons.Default.Receipt,    Screen.TransactionReport.route)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    paymentViewModel: PaymentViewModel,
    transactionViewModel: TransactionViewModel
) {
    val navController   = rememberNavController()
    val selectedMode    by paymentViewModel.selectedMode.collectAsState()
    val showModePicker  by paymentViewModel.showModePicker.collectAsState()
    val sheetState      = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = { BottomNavBar(navController) }
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = Screen.Payment.route,
                modifier         = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Payment.route) {
                    PaymentScreen(
                        viewModel        = paymentViewModel,
                        onCardPresent    = { navController.navigate(Screen.CardPresent.route) },
                        onCardNotPresent = { navController.navigate(Screen.CardNotPresent.route) }
                    )
                }
                composable(Screen.CardPresent.route) {
                    val context = LocalContext.current
                    TapScreen(
                        viewModel            = paymentViewModel,
                        nfcAdapter           = NfcAdapter.getDefaultAdapter(context),
                        activity             = context as Activity,
                        onNavigateToPinEntry = { navController.navigate(Screen.PinEntry.route) },
                        onNavigateToSignature = { navController.navigate(Screen.SignatureCapture.route) },
                        onNavigateToResult   = { navController.navigate(Screen.TransactionResult.route) },
                        onNavigateBack       = {
                            navController.navigate(Screen.Payment.route) {
                                popUpTo(Screen.Payment.route) { inclusive = false }
                            }
                        }
                    )
                }
                composable(Screen.CardNotPresent.route) {
                    CardNotPresentScreen(
                        viewModel            = paymentViewModel,
                        onNavigateToPinEntry = { navController.navigate(Screen.PinEntry.route) }
                    )
                }
                composable(Screen.PinEntry.route) {
                    PinEntryScreen(
                        viewModel               = paymentViewModel,
                        onNavigateToSignature   = { navController.navigate(Screen.SignatureCapture.route) }
                    )
                }
                composable(Screen.SignatureCapture.route) {
                    SignatureCaptureScreen(
                        viewModel            = paymentViewModel,
                        onNavigateToResult   = { navController.navigate(Screen.TransactionResult.route) }
                    )
                }
                composable(Screen.TransactionResult.route) {
                    TransactionResultScreen(
                        viewModel  = paymentViewModel,
                        onNewPayment = {
                            navController.navigate(Screen.Payment.route) {
                                popUpTo(Screen.Payment.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.TransactionReport.route) {
                    TransactionReportScreen(
                        viewModel            = transactionViewModel,
                        onTransactionClick   = { transactionId ->
                            navController.navigate(Screen.TransactionDetail.createRoute(transactionId))
                        }
                    )
                }
                composable(
                    route     = Screen.TransactionDetail.route,
                    arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
                    TransactionDetailScreen(
                        viewModel     = transactionViewModel,
                        transactionId = transactionId,
                        onBack        = { navController.popBackStack() }
                    )
                }
            }
        }

        // ── Tappable mode badge — top-right corner ────────────────────────
        ModeBadge(
            mode     = selectedMode,
            onClick  = { paymentViewModel.openModePicker() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp)
        )
    }

    // ── Mode picker bottom sheet ──────────────────────────────────────────
    if (showModePicker) {
        ModalBottomSheet(
            onDismissRequest = { paymentViewModel.closeModePicker() },
            sheetState       = sheetState,
            containerColor   = MaterialTheme.colorScheme.surface,
        ) {
            ModePickerSheet(
                currentMode  = selectedMode,
                onModeSelect = { paymentViewModel.selectMode(it) }
            )
        }
    }
}

// ── Mode picker sheet content ─────────────────────────────────────────────────

@Composable
private fun ModePickerSheet(
    currentMode: PaymentMode,
    onModeSelect: (PaymentMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text       = "Payment Mode",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text  = "Select how the app processes payments",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        PaymentMode.entries.forEach { mode ->
            val isLive     = mode == PaymentMode.LIVE
            val isSelected = mode == currentMode
            val rowAlpha   = if (isLive) 0.38f else 1f
            val textColor  = when {
                isSelected && !isLive -> MaterialTheme.colorScheme.primary
                else                  -> MaterialTheme.colorScheme.onSurface
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isLive) { onModeSelect(mode) }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick  = { if (!isLive) onModeSelect(mode) },
                    colors   = RadioButtonDefaults.colors(
                        selectedColor   = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rowAlpha)
                    )
                )
                Column(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .weight(1f)
                ) {
                    Text(
                        text       = mode.label,
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected && !isLive) FontWeight.SemiBold else FontWeight.Normal,
                        color      = textColor.copy(alpha = rowAlpha)
                    )
                    val subtitle = when (mode) {
                        PaymentMode.SIMULATOR -> "Local in-memory simulation"
                        PaymentMode.MOCK      -> "Realistic sandbox responses"
                        PaymentMode.LIVE      -> "Real gateway — coming soon"
                    }
                    Text(
                        text  = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rowAlpha)
                    )
                }
            }
        }
    }
}

// ── Bottom nav ────────────────────────────────────────────────────────────────

@Composable
fun BottomNavBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick  = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                },
                icon   = { Icon(item.icon, contentDescription = item.label) },
                label  = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = MaterialTheme.colorScheme.primary,
                    selectedTextColor   = MaterialTheme.colorScheme.primary,
                    indicatorColor      = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

// ── Mode badge ────────────────────────────────────────────────────────────────

/**
 * Tappable pill badge in the top-right corner.
 *
 *  Tony MCP  → blue-grey
 *  Sandbox   → amber
 *  Live      → green (disabled)
 */
@Composable
fun ModeBadge(
    mode:     PaymentMode,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when (mode) {
        PaymentMode.LIVE      -> Color(0xFF2E7D32)   // dark green
        PaymentMode.MOCK      -> Color(0xFFF57F17)   // amber
        PaymentMode.SIMULATOR -> Color(0xFF546E7A)   // blue-grey
    }

    Text(
        text     = mode.label,
        color    = Color.White,
        fontSize = 10.sp,
        modifier = modifier
            .background(color = bgColor, shape = RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

