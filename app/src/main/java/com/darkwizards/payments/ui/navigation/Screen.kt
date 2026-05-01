package com.darkwizards.payments.ui.navigation

sealed class Screen(val route: String) {

    // ── Merchant screens ──────────────────────────────────────────────────────

    /** Default start destination — merchant amount entry with numeric keypad. */
    object MerchantPay : Screen("merchant_pay")

    /** Full-screen settings (surcharges, tips, AVS, branding colors, payment mode). */
    object Settings : Screen("settings")

    // ── Customer screens ──────────────────────────────────────────────────────

    /**
     * First customer-facing screen — Debit / Credit selection.
     * No back arrow (handover boundary).
     */
    object PaymentOptions : Screen("payment_options/{baseAmountCents}") {
        fun createRoute(baseAmountCents: Int) = "payment_options/$baseAmountCents"
    }

    /**
     * Tip selection and total confirmation.
     * Receives base amount, card type, and surcharge from [PaymentOptions].
     */
    object TotalAmount : Screen("total_amount/{baseAmountCents}/{cardType}/{surchargeCents}") {
        fun createRoute(baseAmountCents: Int, cardType: String, surchargeCents: Int) =
            "total_amount/$baseAmountCents/$cardType/$surchargeCents"
    }

    /**
     * Card Present vs Card Not Present selection.
     * Receives full amount breakdown from [TotalAmount].
     */
    object PaymentType : Screen("payment_type/{baseAmountCents}/{cardType}/{surchargeCents}/{tipCents}") {
        fun createRoute(
            baseAmountCents: Int,
            cardType: String,
            surchargeCents: Int,
            tipCents: Int
        ) = "payment_type/$baseAmountCents/$cardType/$surchargeCents/$tipCents"
    }

    /**
     * Manual card entry (Card Not Present).
     * Receives the final total amount in cents.
     */
    object ManualEntry : Screen("manual_entry/{totalAmountCents}") {
        fun createRoute(totalAmountCents: Int) = "manual_entry/$totalAmountCents"
    }

    /** Post-signature receipt delivery + success state + auto-reset to [MerchantPay]. */
    object Receipt : Screen("receipt")

    // ── Existing screens (routes unchanged) ───────────────────────────────────

    /** NFC tap-to-pay screen. */
    object CardPresent : Screen("card_present")

    /** PIN entry screen (shared by Card Present and Card Not Present flows). */
    object PinEntry : Screen("pin_entry")

    /** Signature capture screen. */
    object SignatureCapture : Screen("signature_capture")

    /** Transaction result screen (legacy — kept for backward compatibility). */
    object TransactionResult : Screen("transaction_result")

    /** Transaction history list. */
    object TransactionReport : Screen("transaction_report")

    /** Transaction detail view. */
    object TransactionDetail : Screen("transaction_detail/{transactionId}") {
        fun createRoute(transactionId: String) = "transaction_detail/$transactionId"
    }
}
