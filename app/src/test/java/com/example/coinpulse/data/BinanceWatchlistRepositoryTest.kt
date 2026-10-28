package com.example.coinpulse.data

import com.example.coinpulse.data.remote.BinanceMarketApi
import com.example.coinpulse.data.remote.Fixtures
import com.example.coinpulse.domain.FailureReason
import com.example.coinpulse.domain.PriceDirection
import com.example.coinpulse.domain.WatchlistResult
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BinanceWatchlistRepositoryTest {
    private lateinit var server: MockWebServer
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val btc = TrackedPair("BTC", "USDT")
    private val eth = TrackedPair("ETH", "USDT")

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.close()

    private fun repository(vararg pairs: TrackedPair) =
        BinanceWatchlistRepository(
            api =
                BinanceMarketApi(
                    OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build(),
                    server.url("/"),
                    random = Random(0),
                ),
            pairs = pairs.toList(),
            clock = clock,
        )

    private fun respond(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun mapsFixtureToExactDecimalsAndMarksDataAsLive() = runTest {
        respond(200, Fixtures.load("ticker24hr_btc.json"))

        val snapshot = (repository(btc).getWatchlist() as WatchlistResult.Success).snapshot

        val item = snapshot.items.single()
        assertEquals("BTC", item.baseAsset)
        assertEquals("USDT", item.quoteAsset)
        assertEquals(BigDecimal("67234.50000000"), item.price)
        assertEquals(BigDecimal("2.310"), item.changePercent)
        assertEquals(BigDecimal("65100.00000000"), item.lowPrice)
        assertEquals(BigDecimal("67890.00000000"), item.highPrice)
        assertEquals(PriceDirection.Up, item.direction)
        assertEquals(now, snapshot.updatedAt)
        assertFalse(snapshot.isSample)
    }

    @Test
    fun itemsFollowTheTrackedPairOrderNotTheResponseOrder() = runTest {
        respond(200, Fixtures.load("ticker24hr_eth_btc_reversed.json"))

        val items = (repository(btc, eth).getWatchlist() as WatchlistResult.Success).snapshot.items

        assertEquals(listOf("BTC", "ETH"), items.map { it.baseAsset })
        assertEquals(PriceDirection.Down, items[1].direction)
        assertEquals(BigDecimal("-0.874"), items[1].changePercent)
    }

    @Test
    fun noTrackedPairsIsEmptyWithoutNetworkCall() = runTest {
        val snapshot = (repository().getWatchlist() as WatchlistResult.Success).snapshot

        assertTrue(snapshot.items.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun missingRequestedSymbolIsUnknownFailure() = runTest {
        respond(200, Fixtures.load("ticker24hr_eth_only.json"))
        assertEquals(WatchlistResult.Failure(FailureReason.Unknown), repository(btc).getWatchlist())
    }

    @Test
    fun nonNumericPriceIsUnknownFailure() = runTest {
        respond(200, Fixtures.load("ticker24hr_non_numeric_price.json"))
        assertEquals(WatchlistResult.Failure(FailureReason.Unknown), repository(btc).getWatchlist())
    }

    @Test
    fun nonNumericHighPriceIsUnknownFailure() = runTest {
        respond(200, Fixtures.load("ticker24hr_bad_high.json"))
        assertEquals(WatchlistResult.Failure(FailureReason.Unknown), repository(btc).getWatchlist())
    }

    @Test
    fun zeroPriceIsUnknownFailure() = runTest {
        respond(200, Fixtures.load("ticker24hr_zero_price.json"))
        assertEquals(WatchlistResult.Failure(FailureReason.Unknown), repository(btc).getWatchlist())
    }

    @Test
    fun malformedBodyIsUnknownFailure() = runTest {
        respond(200, Fixtures.load("ticker24hr_truncated.json"))
        assertEquals(WatchlistResult.Failure(FailureReason.Unknown), repository(btc).getWatchlist())
    }

    @Test
    fun rateLimitMapsToRateLimited() = runTest {
        respond(429)
        assertEquals(WatchlistResult.Failure(FailureReason.RateLimited), repository(btc).getWatchlist())
    }

    @Test
    fun unreachableServerMapsToOffline() = runTest {
        val repository = repository(btc)
        server.close()
        assertEquals(WatchlistResult.Failure(FailureReason.Offline), repository.getWatchlist())
    }
}
