package com.darkwizards.payments.data.service

import com.darkwizards.payments.data.model.McpError
import com.darkwizards.payments.data.model.ModeResponse
import com.darkwizards.payments.data.model.RefundResponse
import com.darkwizards.payments.data.model.SaleResponse
import com.darkwizards.payments.data.model.SettleResponse
import com.darkwizards.payments.data.model.TransactionDetail

/**
 * Centralized service layer for all MCP payment communication.
 * All methods return Result<T> wrapping success/failure.
 */
interface PaymentService {
    /** Authenticate with MCP service, store token internally */
    suspend fun getToken(): Result<String>

    /** Process a sale transaction. Amount in dollars (e.g., "25.30"). */
    suspend fun processSale(
        accountNumber: String,
        accountType: String,
        expiry: String,
        totalAmountDollars: String
    ): Result<SaleResponse>

    /** Settle all transactions, then refund a specific transaction */
    suspend fun processRefund(transactionId: String): Result<RefundResponse>

    /** Force-settle all transactions (olderThanHours=0) */
    suspend fun settleTransactions(): Result<SettleResponse>

    /** Look up a transaction by ID from MCP service */
    suspend fun getTransaction(transactionId: String): Result<TransactionDetail>

    /** Get current server mode and seeded mock transactions */
    suspend fun getMode(): Result<ModeResponse>
}
