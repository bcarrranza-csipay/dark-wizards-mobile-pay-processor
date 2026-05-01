package com.darkwizards.payments

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.darkwizards.payments.data.TransactionStore
import com.darkwizards.payments.data.service.PaymentServiceImpl
import com.darkwizards.payments.ui.navigation.AppNavigation
import com.darkwizards.payments.ui.theme.ColorTokenRepository
import com.darkwizards.payments.ui.theme.ConstellationPaymentsTheme
import com.darkwizards.payments.ui.theme.LocalColorTokens
import com.darkwizards.payments.ui.viewmodel.PaymentViewModel
import com.darkwizards.payments.ui.viewmodel.SettingsViewModel
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

    private val colorTokenRepository by lazy {
        ColorTokenRepository(getSharedPreferences("color_tokens", Context.MODE_PRIVATE))
    }

    private val settingsViewModel by lazy {
        SettingsViewModel(
            colorTokenRepository = colorTokenRepository,
            settingsPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE),
            paymentViewModel = paymentViewModel
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val tokens by colorTokenRepository.tokensFlow.collectAsState(
                initial = colorTokenRepository.loadTokens()
            )
            CompositionLocalProvider(LocalColorTokens provides tokens) {
                ConstellationPaymentsTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation(
                            paymentViewModel = paymentViewModel,
                            transactionViewModel = transactionViewModel,
                            settingsViewModel = settingsViewModel
                        )
                    }
                }
            }
        }
    }
}
