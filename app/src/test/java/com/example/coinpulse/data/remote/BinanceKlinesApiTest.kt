package com.example.coinpulse.data.remote

import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BinanceKlinesApiTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.close()

    private fun api() = BinanceMarketApi(client, server.url("/"), random = Random(0))

    private fun respond(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun requestsLast24hAsFifteenMinuteCandlesWithoutCredentials() = runTest {
        respond(200, Fixtures.load("klines_btc_15m.json"))

        api().get24hKlines("BTCUSDT")

        val request = server.takeRequest()
        assertEquals("/api/v3/klines", request.url.encodedPath)
        assertEquals("BTCUSDT", request.url.queryParameter("symbol"))
        assertEquals("15m", request.url.queryParameter("interval"))
        assertEquals("96", request.url.queryParameter("limit"))
        assertNull(request.headers["X-MBX-APIKEY"])
    }

    @Test
    fun readsOpenTimeAndCloseFromTwelveCellRows() = runTest {
        respond(200, Fixtures.load("klines_btc_15m.json"))

        val klines = (api().get24hKlines("BTCUSDT") as ApiResult.Success).value

        assertEquals(
            listOf(
                KlineDto(1767225600000, "66050.25000000"),
                KlineDto(1767226500000, "66180.10000000"),
                KlineDto(1767227400000, "66120.00000000"),
            ),
            klines,
        )
    }

    @Test
    fun rowWithTooFewCellsIsMalformed() = runTest {
        respond(200, Fixtures.load("klines_short_row.json"))
        assertEquals(ApiResult.Failure(ApiFailure.Malformed), api().get24hKlines("BTCUSDT"))
    }

    @Test
    fun nonArrayBodyIsMalformed() = runTest {
        respond(200, Fixtures.load("klines_not_array.json"))
        assertEquals(ApiResult.Failure(ApiFailure.Malformed), api().get24hKlines("BTCUSDT"))
    }

    @Test
    fun truncatedBodyIsMalformed() = runTest {
        respond(200, Fixtures.load("ticker24hr_truncated.json"))
        assertEquals(ApiResult.Failure(ApiFailure.Malformed), api().get24hKlines("BTCUSDT"))
    }

    @Test
    fun rateLimitIsNotRetried() = runTest {
        respond(429)
        assertEquals(ApiResult.Failure(ApiFailure.RateLimited), api().get24hKlines("BTCUSDT"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun serverErrorIsRetriedThenSucceeds() = runTest {
        respond(502)
        respond(200, Fixtures.load("klines_btc_15m.json"))
        assertEquals(3, (api().get24hKlines("BTCUSDT") as ApiResult.Success).value.size)
        assertEquals(2, server.requestCount)
    }
}
