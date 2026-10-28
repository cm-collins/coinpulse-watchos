package com.example.coinpulse.data.remote

import kotlin.coroutines.resume
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

internal enum class ApiFailure { Network, RateLimited, Client, Server, Malformed }

internal sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val kind: ApiFailure) : ApiResult<Nothing>
}

/**
 * Public Binance Spot market data only (no API key, no account or trading endpoints).
 * Docs: https://developers.binance.com/en/docs/products/spot/rest-api
 *
 * Retries are bounded ([maxAttempts] total) and only for I/O errors and 5xx. HTTP 429/418 stop
 * immediately (Binance requires backing off; repeated requests risk an IP ban). Cancellation
 * propagates and cancels the in-flight call.
 */
internal class BinanceMarketApi(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    private val maxAttempts: Int = 3,
    private val random: Random = Random.Default,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * `GET /api/v3/ticker/24hr?symbols=[...]` (FULL type; weight 2 for up to 20 symbols).
     * Always pass explicit symbols: omitting them is the maximum weight.
     */
    suspend fun get24hTickers(symbols: List<String>): ApiResult<List<Ticker24hDto>> {
        require(symbols.isNotEmpty()) { "symbols must not be empty" }
        return get(
            path = "api/v3/ticker/24hr",
            params = mapOf("symbols" to symbols.joinToString(",", "[", "]") { "\"$it\"" }),
        ) { json.decodeFromString<List<Ticker24hDto>>(it) }
    }

    /**
     * `GET /api/v3/klines` for the last 24h as 96 fifteen-minute candles (weight 2, `limit` max
     * 1000). Each row is a 12-element array; only the open time (index 0) and close (index 4) are read.
     */
    suspend fun get24hKlines(symbol: String): ApiResult<List<KlineDto>> =
        get(
            path = "api/v3/klines",
            params = mapOf("symbol" to symbol, "interval" to KLINE_INTERVAL, "limit" to KLINE_LIMIT.toString()),
        ) { text ->
            json.parseToJsonElement(text).jsonArray.map { row ->
                val cells = row.jsonArray
                KlineDto(openTimeMillis = cells[0].jsonPrimitive.long, close = cells[4].jsonPrimitive.content)
            }
        }

    /** Shared request loop: bounded retries, rate-limit stop, and malformed-body mapping. */
    private suspend fun <T> get(path: String, params: Map<String, String>, parse: (String) -> T): ApiResult<T> {
        val url =
            baseUrl.newBuilder()
                .addPathSegments(path)
                .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
                .build()
        val request = Request.Builder().url(url).get().build()

        var failure = ApiFailure.Network
        for (attempt in 1..maxAttempts) {
            when (val outcome = execute(request)) {
                is Outcome.Body -> return parseBody(outcome.text, parse)
                is Outcome.Http ->
                    when {
                        outcome.code == 429 || outcome.code == 418 -> return ApiResult.Failure(ApiFailure.RateLimited)
                        outcome.code in 500..599 -> failure = ApiFailure.Server
                        else -> return ApiResult.Failure(ApiFailure.Client)
                    }
                Outcome.Io -> failure = ApiFailure.Network
            }
            if (attempt < maxAttempts) delay(backoffMillis(attempt))
        }
        return ApiResult.Failure(failure)
    }

    private fun <T> parseBody(text: String, parse: (String) -> T): ApiResult<T> =
        try {
            ApiResult.Success(parse(text))
        } catch (_: SerializationException) {
            ApiResult.Failure(ApiFailure.Malformed)
        } catch (_: IllegalArgumentException) { // wrong JSON shape or a non-numeric cell
            ApiResult.Failure(ApiFailure.Malformed)
        } catch (_: IndexOutOfBoundsException) { // a kline row with too few cells
            ApiResult.Failure(ApiFailure.Malformed)
        }

    private sealed interface Outcome {
        data class Body(val text: String) : Outcome
        data class Http(val code: Int) : Outcome
        data object Io : Outcome
    }

    /**
     * The body is read inside the callback, not after resuming, so cancelling the coroutine
     * (which cancels the call) also interrupts a stalled body read instead of blocking on it.
     */
    private suspend fun execute(request: Request): Outcome =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(Outcome.Io)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val outcome =
                            try {
                                response.use {
                                    if (it.isSuccessful) Outcome.Body(it.body.string()) else Outcome.Http(it.code)
                                }
                            } catch (_: IOException) {
                                Outcome.Io
                            }
                        continuation.resume(outcome)
                    }
                },
            )
        }

    /** 500ms, 1s, 2s... capped at 4s, plus up to 250ms of jitter. */
    private fun backoffMillis(attempt: Int): Long =
        min(BASE_BACKOFF_MS shl (attempt - 1), MAX_BACKOFF_MS) + random.nextLong(JITTER_MS)

    companion object {
        const val DEFAULT_BASE_URL = "https://data-api.binance.vision"
        private const val KLINE_INTERVAL = "15m"
        private const val KLINE_LIMIT = 96 // 96 x 15m = 24h
        private const val BASE_BACKOFF_MS = 500L
        private const val MAX_BACKOFF_MS = 4_000L
        private const val JITTER_MS = 250L
    }
}
