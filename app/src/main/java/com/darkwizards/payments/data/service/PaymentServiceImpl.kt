package com.darkwizards.payments.data.service

import com.darkwizards.payments.data.model.ModeResponse
import com.darkwizards.payments.data.model.SeededTransaction
import com.darkwizards.payments.data.model.HistoricalTransaction
import com.darkwizards.payments.data.model.RefundResponse
import com.darkwizards.payments.data.model.SaleResponse
import com.darkwizards.payments.data.model.SettleResponse
import com.darkwizards.payments.data.model.TransactionDetail
import com.darkwizards.payments.util.AmountUtils
import com.darkwizards.payments.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

/**
 * PaymentService implementation that communicates with the pyxis-payment-mcp
 * service via JSON-RPC over HTTP (MCP protocol).
 */
class PaymentServiceImpl(
    private val httpClient: HttpClient,
    // Base URL is set per build flavor via BuildConfig:
    //   dev flavor   → http://10.0.2.2:3000  (Android emulator localhost)
    //   demo flavor  → https://<app-runner-url> (AWS App Runner)
    private val baseUrl: String = BuildConfig.MCP_BASE_URL
) : PaymentService {

    companion object {
        const val TERMINAL_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        private const val DEFAULT_USERNAME = "sandbox"
        private const val DEFAULT_PASSWORD = "sandbox"
        private const val NETWORK_ERROR_MESSAGE = "Payment service is currently unavailable. Please try again later."
    }

    private var bearerToken: String? = null
    private val requestIdCounter = AtomicInteger(0)

    private val json = Json { ignoreUnknownKeys = true }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    override suspend fun getToken(): Result<String> {
        return try {
            val arguments = JsonObject(
                mapOf(
                    "username" to JsonPrimitive(DEFAULT_USERNAME),
                    "password" to JsonPrimitive(DEFAULT_PASSWORD)
                )
            )
            val response = callTool("pyxis_get_token", arguments)
            val parsed = parseToolResponse(response)

            val status = parsed.jsonObject["status"]?.jsonPrimitive?.content
            if (status == "Success") {
                val token = parsed.jsonObject["token"]?.jsonPrimitive?.content
                    ?: return Result.failure(Exception("Missing token in response"))
                bearerToken = token
                Result.success(token)
            } else {
                val errorMsg = extractErrorMessage(parsed)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapNetworkException(e)))
        }
    }

    override suspend fun processSale(
        accountNumber: String,
        accountType: String,
        expiry: String,
        totalAmountDollars: String
    ): Result<SaleResponse> {
        val token = bearerToken
            ?: return Result.failure(Exception("Not authenticated. Call getToken first."))

        return try {
            val totalAmountCents = AmountUtils.dollarsToCents(totalAmountDollars)

            val accountInfo = JsonObject(
                mapOf(
                    "accountNumber" to JsonPrimitive(accountNumber),
                    "accountType" to JsonPrimitive(accountType),
                    "accountAccessory" to JsonPrimitive(expiry)
                )
            )
            val arguments = JsonObject(
                mapOf(
                    "bearerToken" to JsonPrimitive(token),
                    "terminalId" to JsonPrimitive(TERMINAL_ID),
                    "accountInfo" to accountInfo,
                    "totalAmount" to JsonPrimitive(totalAmountCents)
                )
            )
            val response = callTool("pyxis_sale", arguments)
            val parsed = parseToolResponse(response)

            val status = parsed.jsonObject["status"]?.jsonPrimitive?.content
            if (status == "Success") {
                val obj = parsed.jsonObject
                Result.success(
                    SaleResponse(
                        transactionId = obj["transactionId"]?.jsonPrimitive?.content ?: "",
                        approvedAmount = obj["approvedAmount"]?.jsonPrimitive?.content ?: "0",
                        feeAmount = obj["feeAmount"]?.jsonPrimitive?.content ?: "0",
                        approvalNumber = obj["approvalNumber"]?.jsonPrimitive?.content ?: "",
                        accountType = obj["accountType"]?.jsonPrimitive?.content ?: "",
                        accountFirst6 = obj["accountFirst6"]?.jsonPrimitive?.content ?: "",
                        accountLast4 = obj["accountLast4"]?.jsonPrimitive?.content ?: ""
                    )
                )
            } else {
                val errorMsg = extractErrorMessage(parsed)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            if (e.message != null && !isNetworkException(e)) {
                Result.failure(e)
            } else {
                Result.failure(Exception(mapNetworkException(e)))
            }
        }
    }

    override suspend fun processRefund(transactionId: String): Result<RefundResponse> {
        val token = bearerToken
            ?: return Result.failure(Exception("Not authenticated. Call getToken first."))

        return try {
            // First settle all transactions so the target becomes refundable
            settleTransactions()

            val arguments = JsonObject(
                mapOf(
                    "bearerToken" to JsonPrimitive(token),
                    "terminalId" to JsonPrimitive(TERMINAL_ID),
                    "transactionToRefundId" to JsonPrimitive(transactionId)
                )
            )
            val response = callTool("pyxis_refund", arguments)
            val parsed = parseToolResponse(response)

            val status = parsed.jsonObject["status"]?.jsonPrimitive?.content
            if (status == "Success") {
                val obj = parsed.jsonObject
                Result.success(
                    RefundResponse(
                        transactionId = obj["transactionId"]?.jsonPrimitive?.content ?: "",
                        referencedTransactionId = obj["referencedTransactionId"]?.jsonPrimitive?.content ?: "",
                        approvedAmount = obj["approvedAmount"]?.jsonPrimitive?.content ?: "0"
                    )
                )
            } else {
                val errorMsg = extractErrorMessage(parsed)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            if (e.message != null && !isNetworkException(e)) {
                Result.failure(e)
            } else {
                Result.failure(Exception(mapNetworkException(e)))
            }
        }
    }

    override suspend fun settleTransactions(): Result<SettleResponse> {
        val token = bearerToken
            ?: return Result.failure(Exception("Not authenticated. Call getToken first."))

        return try {
            val arguments = JsonObject(
                mapOf(
                    "bearerToken" to JsonPrimitive(token),
                    "terminalId" to JsonPrimitive(TERMINAL_ID),
                    "olderThanHours" to JsonPrimitive(0)
                )
            )
            val response = callTool("pyxis_settle_transactions", arguments)
            val parsed = parseToolResponse(response)

            val status = parsed.jsonObject["status"]?.jsonPrimitive?.content
            if (status == "Success") {
                val obj = parsed.jsonObject
                Result.success(
                    SettleResponse(
                        message = obj["message"]?.jsonPrimitive?.content ?: "",
                        settled = obj["settled"]?.jsonPrimitive?.intOrNull ?: 0
                    )
                )
            } else {
                val errorMsg = extractErrorMessage(parsed)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            if (e.message != null && !isNetworkException(e)) {
                Result.failure(e)
            } else {
                Result.failure(Exception(mapNetworkException(e)))
            }
        }
    }

    override suspend fun getTransaction(transactionId: String): Result<TransactionDetail> {
        val token = bearerToken
            ?: return Result.failure(Exception("Not authenticated. Call getToken first."))

        return try {
            val arguments = JsonObject(
                mapOf(
                    "bearerToken" to JsonPrimitive(token),
                    "transactionId" to JsonPrimitive(transactionId)
                )
            )
            val response = callTool("pyxis_get_transaction", arguments)
            val parsed = parseToolResponse(response)

            val status = parsed.jsonObject["status"]?.jsonPrimitive?.content
            if (status == "Success") {
                val obj = parsed.jsonObject
                Result.success(
                    TransactionDetail(
                        transactionId = obj["transactionId"]?.jsonPrimitive?.content ?: "",
                        type = obj["type"]?.jsonPrimitive?.content ?: "",
                        transactionStatus = obj["transactionStatus"]?.jsonPrimitive?.content
                            ?: obj["status"]?.jsonPrimitive?.content ?: "",
                        terminalId = obj["terminalId"]?.jsonPrimitive?.content ?: "",
                        totalAmount = obj["totalAmount"]?.jsonPrimitive?.content ?: "0",
                        approvedAmount = obj["approvedAmount"]?.jsonPrimitive?.content ?: "0",
                        feeAmount = obj["feeAmount"]?.jsonPrimitive?.content ?: "0",
                        approvalNumber = obj["approvalNumber"]?.jsonPrimitive?.content ?: "",
                        accountType = obj["accountType"]?.jsonPrimitive?.content ?: "",
                        accountFirst6 = obj["accountFirst6"]?.jsonPrimitive?.content ?: "",
                        accountLast4 = obj["accountLast4"]?.jsonPrimitive?.content ?: "",
                        isDeclined = obj["isDeclined"]?.jsonPrimitive?.booleanOrNull ?: false,
                        creationTime = obj["creationTime"]?.jsonPrimitive?.content
                            ?: obj["createdAt"]?.jsonPrimitive?.content ?: "",
                        settlementDate = obj["settlementDate"]?.jsonPrimitive?.content
                            ?: obj["settledAt"]?.jsonPrimitive?.content,
                        referencedTransactionId = obj["referencedTransactionId"]?.jsonPrimitive?.content
                    )
                )
            } else {
                val errorMsg = extractErrorMessage(parsed)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            if (e.message != null && !isNetworkException(e)) {
                Result.failure(e)
            } else {
                Result.failure(Exception(mapNetworkException(e)))
            }
        }
    }

    override suspend fun getMode(): Result<ModeResponse> {
        return try {
            val arguments = JsonObject(emptyMap())
            val response = callTool("pyxis_get_mode", arguments)
            val parsed = parseToolResponse(response)
            val obj = parsed.jsonObject

            val mode = obj["mode"]?.jsonPrimitive?.content ?: "simulator"
            val seededRaw = obj["seededTransactions"]?.jsonArray ?: JsonArray(emptyList())
            val seeded = seededRaw.map { el ->
                val t = el.jsonObject
                SeededTransaction(
                    transactionId  = t["transactionId"]?.jsonPrimitive?.content ?: "",
                    type           = t["type"]?.jsonPrimitive?.content ?: "Sale",
                    transactionStatus = t["transactionStatus"]?.jsonPrimitive?.content ?: "Approved",
                    totalAmount    = t["totalAmount"]?.jsonPrimitive?.content ?: "0",
                    approvedAmount = t["approvedAmount"]?.jsonPrimitive?.content ?: "0",
                    feeAmount      = t["feeAmount"]?.jsonPrimitive?.content ?: "0",
                    approvalNumber = t["approvalNumber"]?.jsonPrimitive?.content ?: "",
                    accountType    = t["accountType"]?.jsonPrimitive?.content ?: "",
                    accountFirst6  = t["accountFirst6"]?.jsonPrimitive?.content ?: "",
                    accountLast4   = t["accountLast4"]?.jsonPrimitive?.content ?: "",
                    creationTime   = t["creationTime"]?.jsonPrimitive?.content ?: "",
                    isDeclined     = t["isDeclined"]?.jsonPrimitive?.booleanOrNull ?: false,
                    isMockSeed     = t["isMockSeed"]?.jsonPrimitive?.booleanOrNull ?: true,
                )
            }
            Result.success(ModeResponse(mode = mode, seededTransactions = seeded))
        } catch (e: Exception) {
            // getMode is best-effort — fall back to "simulator" if the tool doesn't exist
            Result.success(ModeResponse(mode = "simulator"))
        }
    }

    override suspend fun getAllTransactions(terminalId: String?): Result<List<HistoricalTransaction>> {
        return try {
            val args = if (terminalId != null)
                JsonObject(mapOf("terminalId" to JsonPrimitive(terminalId)))
            else
                JsonObject(emptyMap())
            val response = callTool("pyxis_get_all_transactions", args)
            val parsed = parseToolResponse(response)
            val obj = parsed.jsonObject
            val txArray = obj["transactions"]?.jsonArray ?: JsonArray(emptyList())
            val list = txArray.map { el ->
                val t = el.jsonObject
                HistoricalTransaction(
                    transactionId      = t["transactionId"]?.jsonPrimitive?.content ?: "",
                    type               = t["type"]?.jsonPrimitive?.content ?: "Sale",
                    transactionStatus  = t["transactionStatus"]?.jsonPrimitive?.content ?: "Approved",
                    terminalId         = t["terminalId"]?.jsonPrimitive?.content ?: "",
                    totalAmount        = t["totalAmount"]?.jsonPrimitive?.content ?: "0",
                    approvedAmount     = t["approvedAmount"]?.jsonPrimitive?.content ?: "0",
                    feeAmount          = t["feeAmount"]?.jsonPrimitive?.content ?: "0",
                    approvalNumber     = t["approvalNumber"]?.jsonPrimitive?.content ?: "",
                    accountType        = t["accountType"]?.jsonPrimitive?.content ?: "",
                    accountFirst6      = t["accountFirst6"]?.jsonPrimitive?.content ?: "",
                    accountLast4       = t["accountLast4"]?.jsonPrimitive?.content ?: "",
                    isDeclined         = t["isDeclined"]?.jsonPrimitive?.booleanOrNull ?: false,
                    creationTime       = t["creationTime"]?.jsonPrimitive?.content ?: "",
                    settlementDate     = t["settlementDate"]?.jsonPrimitive?.content,
                    gatewayResponseCode    = t["gatewayResponseCode"]?.jsonPrimitive?.content ?: "",
                    gatewayResponseMessage = t["gatewayResponseMessage"]?.jsonPrimitive?.content ?: ""
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.success(emptyList()) // best-effort — empty list on failure
        }
    }

    // -------------------------------------------------------------------------
    // JSON-RPC / MCP Communication
    // -------------------------------------------------------------------------

    /**
     * Sends a JSON-RPC 2.0 request to the MCP server's tools/call endpoint.
     * The MCP protocol wraps tool calls as:
     * {
     *   "jsonrpc": "2.0",
     *   "id": <int>,
     *   "method": "tools/call",
     *   "params": { "name": "<tool_name>", "arguments": { ... } }
     * }
     */
    internal suspend fun callTool(toolName: String, arguments: JsonObject): String {
        val requestId = requestIdCounter.incrementAndGet()
        val requestBody = JsonObject(
            mapOf(
                "jsonrpc" to JsonPrimitive("2.0"),
                "id" to JsonPrimitive(requestId),
                "method" to JsonPrimitive("tools/call"),
                "params" to JsonObject(
                    mapOf(
                        "name" to JsonPrimitive(toolName),
                        "arguments" to arguments
                    )
                )
            )
        )

        val response = httpClient.post(baseUrl) {
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }
        return response.body<String>()
    }

    /**
     * Parses the MCP JSON-RPC response.
     * The MCP server returns:
     * {
     *   "jsonrpc": "2.0",
     *   "id": <int>,
     *   "result": {
     *     "content": [{ "type": "text", "text": "<json-string>" }]
     *   }
     * }
     * The actual tool response is JSON-encoded inside the "text" field.
     */
    internal fun parseToolResponse(rawResponse: String): JsonElement {
        val root = json.parseToJsonElement(rawResponse).jsonObject

        // Check for JSON-RPC error
        val error = root["error"]
        if (error != null) {
            val errorMsg = error.jsonObject["message"]?.jsonPrimitive?.content
                ?: "Unknown JSON-RPC error"
            throw Exception(errorMsg)
        }

        val result = root["result"]?.jsonObject
            ?: throw Exception("Missing result in JSON-RPC response")

        val content = result["content"]?.jsonArray
            ?: throw Exception("Missing content in MCP response")

        if (content.isEmpty()) {
            throw Exception("Empty content in MCP response")
        }

        val textContent = content[0].jsonObject["text"]?.jsonPrimitive?.content
            ?: throw Exception("Missing text in MCP response content")

        return json.parseToJsonElement(textContent)
    }

    // -------------------------------------------------------------------------
    // Error Handling
    // -------------------------------------------------------------------------

    /**
     * Extracts the error message from an MCP error response.
     * MCP errors have the shape: { "status": "Error", "errors": [{ "errorMsg": "..." }] }
     */
    internal fun extractErrorMessage(response: JsonElement): String {
        val errors = response.jsonObject["errors"]
        if (errors is JsonArray && errors.isNotEmpty()) {
            val firstError = errors[0].jsonObject
            return firstError["errorMsg"]?.jsonPrimitive?.content
                ?: "Unknown payment error"
        }
        return response.jsonObject["errorMsg"]?.jsonPrimitive?.content
            ?: "Unknown payment error"
    }

    /**
     * Maps network exceptions to user-friendly messages.
     */
    internal fun mapNetworkException(e: Exception): String {
        return when (e) {
            is SocketTimeoutException -> NETWORK_ERROR_MESSAGE
            is UnknownHostException -> NETWORK_ERROR_MESSAGE
            is IOException -> NETWORK_ERROR_MESSAGE
            else -> e.message ?: NETWORK_ERROR_MESSAGE
        }
    }

    /**
     * Checks if an exception is a network-related exception.
     */
    private fun isNetworkException(e: Exception): Boolean {
        return e is IOException || e is SocketTimeoutException || e is UnknownHostException
    }
}
