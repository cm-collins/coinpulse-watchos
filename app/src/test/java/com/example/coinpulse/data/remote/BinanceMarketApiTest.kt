package com.example.coinpulse.data.remote

import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BinanceMarketApiTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    private val symbols = listOf("BTCUSDT")

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.close()

    private fun api(maxAttempts: Int = 3) =
        BinanceMarketApi(client, server.url("/"), maxAttempts = maxAttempts, random = Random(0))

    private fun respond(code: Int, body: String = "", headers: Map<String, String> = emptyMap()) =
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .body(body)
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                .build(),
        )

    @Test
    fun sendsPublicGetWithExplicitSymbolsAndNoCredentials() = runTest {
        respond(200, Fixtures.load("ticker24hr_btc.json"))

        api().get24hTickers(symbols)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v3/ticker/24hr", request.url.encodedPath)
        assertEquals("[\"BTCUSDT\"]", request.url.queryParameter("symbols"))
        assertNull(request.headers["X-MBX-APIKEY"])
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun parsesStringsAndIgnoresUnknownFields() = runTest {
        respond(200, Fixtures.load("ticker24hr_btc.json"))

        val result = api().get24hTickers(symbols)

        val tickers = (result as ApiResult.Success).value
        assertEquals(listOf(Ticker24hDto("BTCUSDT", "67234.50000000", "2.310", "67890.00000000", "65100.00000000")), tickers)
    }

    @Test
    fun emptyArrayIsSuccess() = runTest {
        respond(200, Fixtures.load("ticker24hr_empty.json"))
        assertEquals(ApiResult.Success(emptyList<Ticker24hDto>()), api().get24hTickers(symbols))
    }

    @Test
    fun malformedJsonIsMalformedAndNotRetried() = runTest {
        respond(200, Fixtures.load("ticker24hr_truncated.json"))
        assertEquals(ApiResult.Failure(ApiFailure.Malformed), api().get24hTickers(symbols))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun missingRequiredFieldIsMalformed() = runTest {
        respond(200, Fixtures.load("ticker24hr_missing_field.json"))
        assertEquals(ApiResult.Failure(ApiFailure.Malformed), api().get24hTickers(symbols))
    }

    @Test
    fun rateLimitIsNotRetried() = runTest {
        respond(429, "", mapOf("Retry-After" to "30"))
        assertEquals(ApiResult.Failure(ApiFailure.RateLimited), api().get24hTickers(symbols))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun ipBanIsNotRetried() = runTest {
        respond(418, "", mapOf("Retry-After" to "120"))
        assertEquals(ApiResult.Failure(ApiFailure.RateLimited), api().get24hTickers(symbols))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun clientErrorWithBinanceErrorBodyIsNotRetried() = runTest {
        respond(400, Fixtures.load("error_invalid_symbol.json"))
        assertEquals(ApiResult.Failure(ApiFailure.Client), api().get24hTickers(symbols))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun serverErrorIsRetriedThenSucceeds() = runTest {
        respond(500)
        respond(200, Fixtures.load("ticker24hr_btc.json"))

        assertTrue(api().get24hTickers(symbols) is ApiResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun persistentServerErrorStopsAfterMaxAttempts() = runTest {
        repeat(5) { respond(503) }

        assertEquals(ApiResult.Failure(ApiFailure.Server), api(maxAttempts = 3).get24hTickers(symbols))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun connectionFailureIsNetworkErrorAfterBoundedRetries() = runTest {
        val api = api()
        server.close() // nothing is listening any more: connection refused

        assertEquals(ApiResult.Failure(ApiFailure.Network), api.get24hTickers(symbols))
    }

    @Test
    fun cancellationCancelsTheInFlightCall() = runBlocking {
        server.enqueue(
            MockResponse.Builder().body(Fixtures.load("ticker24hr_btc.json")).bodyDelay(10, TimeUnit.SECONDS).build(),
        )
        val job = launch(Dispatchers.IO) { api().get24hTickers(symbols) }
        server.takeRequest() // the request reached the server and is now stalled

        withTimeout(3_000) { job.cancelAndJoin() }

        assertTrue(job.isCancelled)
        assertFalse(job.isActive)
    }
}
