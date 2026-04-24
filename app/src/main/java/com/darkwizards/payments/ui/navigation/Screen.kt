package com.darkwizards.payments.ui.navigation

sealed class Screen(val route: String) {
    object Payment : Screen("payment")
    object CardPresent : Screen("card_present")
    object CardNotPresent : Screen("card_not_present")
    object PinEntry : Screen("pin_entry")
    object SignatureCapture : Screen("signature_capture")
    object TransactionResult : Screen("transaction_result")
    object TransactionReport : Screen("transaction_report")
    object TransactionDetail : Screen("transaction_detail/{transactionId}") {
        fun createRoute(transactionId: String) = "transaction_detail/$transactionId"
    }
}
