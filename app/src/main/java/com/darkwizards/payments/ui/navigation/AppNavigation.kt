package com.darkwizards.payments.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.darkwizards.payments.ui.screen.CardPresentScreen
import com.darkwizards.payments.ui.screen.PaymentScreen
import com.darkwizards.payments.ui.screen.PinEntryScreen
import com.darkwizards.payments.ui.screen.SignatureCaptureScreen
import com.darkwizards.payments.ui.screen.TransactionDetailScreen
import com.darkwizards.payments.ui.screen.TransactionReportScreen
import com.darkwizards.payments.ui.screen.TransactionResultScreen
import com.darkwizards.payments.ui.viewmodel.PaymentViewModel
import com.darkwizards.payments.ui.viewmodel.TransactionViewModel

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Pay", Icons.Default.CreditCard, Screen.Payment.route),
    BottomNavItem("Transactions", Icons.Default.Receipt, Screen.TransactionReport.route)
)

@Composable
fun AppNavigation(
    paymentViewModel: PaymentViewModel,
    transactionViewModel: TransactionViewModel
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { BottomNavBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Payment.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Payment.route) {
                PaymentScreen(
                    viewModel = paymentViewModel,
                    onCardPresent = { navController.navigate(Screen.CardPresent.route) },
                    onCardNotPresent = { navController.navigate(Screen.CardNotPresent.route) }
                )
            }
            composable(Screen.CardPresent.route) {
                CardPresentScreen(
                    viewModel = paymentViewModel,
                    onNavigateToPinEntry = { navController.navigate(Screen.PinEntry.route) }
                )
            }
            composable(Screen.CardNotPresent.route) {
                CardNotPresentScreen(
                    viewModel = paymentViewModel,
                    onNavigateToPinEntry = { navController.navigate(Screen.PinEntry.route) }
                )
            }
            composable(Screen.PinEntry.route) {
                PinEntryScreen(
                    viewModel = paymentViewModel,
                    onNavigateToSignature = { navController.navigate(Screen.SignatureCapture.route) }
                )
            }
            composable(Screen.SignatureCapture.route) {
                SignatureCaptureScreen(
                    viewModel = paymentViewModel,
                    onNavigateToResult = { navController.navigate(Screen.TransactionResult.route) }
                )
            }
            composable(Screen.TransactionResult.route) {
                TransactionResultScreen(
                    viewModel = paymentViewModel,
                    onNewPayment = {
                        navController.navigate(Screen.Payment.route) {
                            popUpTo(Screen.Payment.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.TransactionReport.route) {
                TransactionReportScreen(
                    viewModel = transactionViewModel,
                    onTransactionClick = { transactionId ->
                        navController.navigate(Screen.TransactionDetail.createRoute(transactionId))
                    }
                )
            }
            composable(
                route = Screen.TransactionDetail.route,
                arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
                TransactionDetailScreen(
                    viewModel = transactionViewModel,
                    transactionId = transactionId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
