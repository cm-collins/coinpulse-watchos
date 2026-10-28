package com.example.coinpulse

import android.app.Application
import com.example.coinpulse.data.BinancePriceHistoryRepository
import com.example.coinpulse.data.BinanceWatchlistRepository
import com.example.coinpulse.data.TrackedPair
import com.example.coinpulse.data.remote.BinanceMarketApi
import com.example.coinpulse.domain.PriceHistoryRepository
import com.example.coinpulse.domain.WatchlistRepository
import java.time.Clock
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Manual dependency container; created once by [CoinPulseApplication] and passed explicitly. */
class AppContainer {
    val clock: Clock = Clock.systemUTC()

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

    private val api = BinanceMarketApi(httpClient)

    val priceHistoryRepository: PriceHistoryRepository = BinancePriceHistoryRepository(api)

    val watchlistRepository: WatchlistRepository =
        BinanceWatchlistRepository(
            api = api,
            pairs =
                listOf(
                    TrackedPair("BTC", "USDT"),
                    TrackedPair("ETH", "USDT"),
                    TrackedPair("SOL", "USDT"),
                    TrackedPair("XRP", "USDT"),
                    TrackedPair("ADA", "USDT"),
                ),
            clock = clock,
        )
}

class CoinPulseApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
