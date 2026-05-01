package com.darkwizards.payments.ui.navigation

import android.app.Activity
import android.nfc.NfcAdapter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.darkwizards.payments.ui.screen.ManualEntryScreen
import com.darkwizards.payments.ui.screen.MerchantPayScreen
import com.darkwizards.payments.ui.screen.PaymentOptionsScreen
import com.darkwizards.payments.ui.screen.PaymentTypeScreen
import com.darkwizards.payments.ui.screen.PinEntryScreen
import com.darkwizards.payments.ui.screen.ReceiptScreen
import com.darkwizards.payments.ui.screen.SettingsScreen
import com.darkwizards.payments.ui.screen.SignatureCaptureScreen
import com.darkwizards.payments.ui.screen.TapScreen
import com.darkwizards.payments.ui.screen.TotalAmountScreen
import com.darkwizards.payments.ui.screen.TransactionDetailScreen
import com.darkwizards.payments.ui.screen.TransactionReportScreen
import com.darkwizards.payments.ui.screen.TransactionResultScreen
import com.darkwizards.payments.ui.theme.LocalColorTokens
import com.darkwizards.payments.ui.viewmodel.PaymentViewModel
import com.darkwizards.payments.ui.viewmodel.SettingsViewModel
import com.darkwizards.payments.ui.viewmodel.TransactionViewModel

// ── Bottom nav items ──────────────────────────────────────────────────────────

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Pay",          Icons.Default.CreditCard, Screen.MerchantPay.route),
    BottomNavItem("Transactions", Icons.Default.Receipt,    Screen.TransactionReport.route),
    BottomNavItem("Settings",     Icons.Default.Settings,   Screen.Settings.route)
)

/**
 * Routes that belong to the customer flow.
 *
 * The Home Bar is hidden whenever the current destination is in this set, ensuring
 * the merchant navigation is never visible during a customer payment session.
 * This set is the single source of truth for Home Bar visibility — keep it in sync
 * with [Screen] additions.
 */
val customerRoutes = setOf(
    Screen.PaymentOptions.route,
    Screen.TotalAmount.route,
    Screen.PaymentType.route,
    Screen.CardPresent.route,
    Screen.ManualEntry.route,
    Screen.PinEntry.route,
    Screen.SignatureCapture.route,
    Screen.Receipt.route
)

// ── AppNavigation ─────────────────────────────────────────────────────────────

@Composable
fun AppNavigation(
    paymentViewModel: PaymentViewModel,
    transactionViewModel: TransactionViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Collect transactions at the AppNavigation level — this ensures the
    // StateFlow is always actively collected regardless of which screen is visible.
    // Pass the list down to TransactionReportScreen as a parameter.
    val allTransactions by transactionViewModel.transactions.collectAsState()

    // Hide the Home Bar on all customer screens
    val showBottomBar = currentDestination?.route !in customerRoutes

    Scaffold(
        containerColor = LocalColorTokens.current.backgroundColor,
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(navController = navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.MerchantPay.route,
            modifier         = Modifier.padding(innerPadding)
        ) {

            // ── Merchant screens ──────────────────────────────────────────────

            composable(Screen.MerchantPay.route) {
                MerchantPayScreen(
                    navController = navController
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    settingsViewModel = settingsViewModel
                )
            }

            composable(Screen.TransactionReport.route) {
                TransactionReportScreen(
                    viewModel          = transactionViewModel,
                    transactions       = allTransactions,
                    onTransactionClick = { transactionId ->
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

            // ── Customer screens ──────────────────────────────────────────────

            composable(
                route     = Screen.PaymentOptions.route,
                arguments = listOf(navArgument("baseAmountCents") { type = NavType.IntType })
            ) { backStackEntry ->
                val baseAmountCents = backStackEntry.arguments?.getInt("baseAmountCents") ?: 0
                PaymentOptionsScreen(
                    baseAmountCents   = baseAmountCents,
                    navController     = navController,
                    settingsViewModel = settingsViewModel
                )
            }

            composable(
                route     = Screen.TotalAmount.route,
                arguments = listOf(
                    navArgument("baseAmountCents") { type = NavType.IntType },
                    navArgument("cardType")        { type = NavType.StringType },
                    navArgument("surchargeCents")  { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val baseAmountCents = backStackEntry.arguments?.getInt("baseAmountCents") ?: 0
                val cardType        = backStackEntry.arguments?.getString("cardType") ?: "debit"
                val surchargeCents  = backStackEntry.arguments?.getInt("surchargeCents") ?: 0
                TotalAmountScreen(
                    baseAmountCents   = baseAmountCents,
                    cardType          = cardType,
                    surchargeCents    = surchargeCents,
                    navController     = navController,
                    settingsViewModel = settingsViewModel
                )
            }

            composable(
                route     = Screen.PaymentType.route,
                arguments = listOf(
                    navArgument("baseAmountCents") { type = NavType.IntType },
                    navArgument("cardType")        { type = NavType.StringType },
                    navArgument("surchargeCents")  { type = NavType.IntType },
                    navArgument("tipCents")        { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val baseAmountCents  = backStackEntry.arguments?.getInt("baseAmountCents") ?: 0
                @Suppress("UNUSED_VARIABLE")
                val cardType         = backStackEntry.arguments?.getString("cardType") ?: "debit"
                val surchargeCents   = backStackEntry.arguments?.getInt("surchargeCents") ?: 0
                val tipCents         = backStackEntry.arguments?.getInt("tipCents") ?: 0
                val totalAmountCents = baseAmountCents + surchargeCents + tipCents
                // Store the card issuer so CVM routing works correctly
                paymentViewModel.setCardIssuer(cardType)
                PaymentTypeScreen(
                    totalAmountCents    = totalAmountCents,
                    onCardPresent       = {
                        navController.navigate(Screen.CardPresent.route)
                    },
                    onCardNotPresent    = {
                        navController.navigate(Screen.ManualEntry.createRoute(totalAmountCents))
                    },
                    onNavigateBack      = { navController.popBackStack() },
                    paymentViewModel    = paymentViewModel,
                    totalAmountDollars  = "%.2f".format(totalAmountCents / 100.0)
                )
            }

            // Card Present (TapScreen) — back navigates to PaymentType
            composable(Screen.CardPresent.route) {
                val context = LocalContext.current
                TapScreen(
                    viewModel             = paymentViewModel,
                    nfcAdapter            = NfcAdapter.getDefaultAdapter(context),
                    activity              = context as Activity,
                    onNavigateToPinEntry  = { navController.navigate(Screen.PinEntry.route) },
                    onNavigateToSignature = { navController.navigate(Screen.SignatureCapture.route) },
                    onNavigateToResult    = { navController.navigate(Screen.Receipt.route) },
                    onNavigateBack        = { navController.popBackStack() }
                )
            }

            composable(
                route     = Screen.ManualEntry.route,
                arguments = listOf(navArgument("totalAmountCents") { type = NavType.IntType })
            ) { backStackEntry ->
                val totalAmountCents = backStackEntry.arguments?.getInt("totalAmountCents") ?: 0
                ManualEntryScreen(
                    totalAmountCents  = totalAmountCents,
                    settingsViewModel = settingsViewModel,
                    paymentViewModel  = paymentViewModel,
                    onNavigateToPinEntry = { navController.navigate(Screen.PinEntry.route) },
                    onNavigateToSignature = { navController.navigate(Screen.SignatureCapture.route) },
                    onNavigateBack    = { navController.popBackStack() }
                )
            }

            composable(Screen.PinEntry.route) {
                PinEntryScreen(
                    viewModel             = paymentViewModel,
                    onNavigateToSignature = { navController.navigate(Screen.SignatureCapture.route) },
                    onNavigateToResult    = { navController.navigate(Screen.Receipt.route) }
                )
            }

            composable(Screen.SignatureCapture.route) {
                SignatureCaptureScreen(
                    viewModel          = paymentViewModel,
                    onNavigateToResult = { navController.navigate(Screen.Receipt.route) },
                    onNavigateBack     = { navController.popBackStack() }
                )
            }

            composable(Screen.Receipt.route) {
                ReceiptScreen(
                    onNavigateToMerchantPay = {
                        navController.navigate(Screen.MerchantPay.route) {
                            popUpTo(Screen.MerchantPay.route) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() },
                    paymentViewModel = paymentViewModel
                )
            }

            // Legacy TransactionResult — kept for backward compatibility
            composable(Screen.TransactionResult.route) {
                TransactionResultScreen(
                    viewModel    = paymentViewModel,
                    onNewPayment = {
                        navController.navigate(Screen.MerchantPay.route) {
                            popUpTo(Screen.MerchantPay.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

// ── Bottom nav bar ────────────────────────────────────────────────────────────

@Composable
fun BottomNavBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val homeBarColor = LocalColorTokens.current.homeBarColor

    NavigationBar(containerColor = homeBarColor) {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick  = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                        launchSingleTop = false
                        restoreState    = false
                    }
                },
                icon   = { Icon(item.icon, contentDescription = item.label) },
                label  = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor      = Color.White,
                    selectedIconColor   = homeBarColor,
                    selectedTextColor   = Color.White,
                    unselectedIconColor = Color.White.copy(alpha = 0.6f),
                    unselectedTextColor = Color.White.copy(alpha = 0.6f)
                )
            )
        }
    }
}
