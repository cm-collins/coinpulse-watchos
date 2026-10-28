package com.example.coinpulse.data

import com.example.coinpulse.data.remote.BinanceMarketApi
import com.example.coinpulse.data.remote.Fixtures
import com.example.coinpulse.domain.FailureReason
import com.example.coinpulse.domain.PriceHistoryResult
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BinancePriceHistoryRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.close()

    private fun repository() =
        BinancePriceHistoryRepository(
            BinanceMarketApi(
                OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build(),
                server.url("/"),
                random = Random(0),
            ),
        )

    private fun respond(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun mapsCandlesToExactDecimalPointsOldestFirst() = runTest {
        respond(200, Fixtures.load("klines_btc_15m.json"))

        val points = (repository().get24hHistory("BTC", "USDT") as PriceHistoryResult.Success).points

        assertEquals(
            listOf(
                Instant.ofEpochMilli(1767225600000) to BigDecimal("66050.25000000"),
                Instant.ofEpochMilli(1767226500000) to BigDecimal("66180.10000000"),
                Instant.ofEpochMilli(1767227400000) to BigDecimal("66120.00000000"),
            ),
            points.map { it.time to it.price },
        )
        assertEquals("BTCUSDT", server.takeRequest().url.queryParameter("symbol"))
    }

    @Test
    fun nonNumericCloseIsUnknownFailure() = runTest {
        respond(200, Fixtures.load("klines_non_numeric_close.json"))
        assertEquals(PriceHistoryResult.Failure(FailureReason.Unknown), repository().get24hHistory("BTC", "USDT"))
    }

    @Test
    fun zeroCloseIsUnknownFailure() = runTest {
        respond(200, Fixtures.load("klines_zero_close.json"))
        assertEquals(PriceHistoryResult.Failure(FailureReason.Unknown), repository().get24hHistory("BTC", "USDT"))
    }

    @Test
    fun rateLimitMapsToRateLimited() = runTest {
        respond(418)
        assertEquals(PriceHistoryResult.Failure(FailureReason.RateLimited), repository().get24hHistory("BTC", "USDT"))
    }

    @Test
    fun unreachableServerMapsToOffline() = runTest {
        val repository = repository()
        server.close()
        assertEquals(PriceHistoryResult.Failure(FailureReason.Offline), repository.get24hHistory("BTC", "USDT"))
    }
}
