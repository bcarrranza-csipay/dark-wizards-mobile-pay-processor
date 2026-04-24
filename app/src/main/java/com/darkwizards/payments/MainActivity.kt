package com.darkwizards.payments

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.darkwizards.payments.data.TransactionStore
import com.darkwizards.payments.data.service.PaymentServiceImpl
import com.darkwizards.payments.ui.navigation.AppNavigation
import com.darkwizards.payments.ui.theme.ConstellationPaymentsTheme
import com.darkwizards.payments.ui.viewmodel.PaymentViewModel
import com.darkwizards.payments.ui.viewmodel.TransactionViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    private val transactionStore = TransactionStore()
    private val paymentService = PaymentServiceImpl(httpClient)
    private val paymentViewModel by lazy { PaymentViewModel(paymentService, transactionStore) }
    private val transactionViewModel by lazy { TransactionViewModel(paymentService, transactionStore) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ConstellationPaymentsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        paymentViewModel = paymentViewModel,
                        transactionViewModel = transactionViewModel
                    )
                }
            }
        }
    }
}
