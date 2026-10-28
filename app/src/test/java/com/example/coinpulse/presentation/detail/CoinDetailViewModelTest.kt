package com.example.coinpulse.presentation.detail

import com.example.coinpulse.domain.FailureReason
import com.example.coinpulse.domain.PriceHistoryRepository
import com.example.coinpulse.domain.PriceHistoryResult
import com.example.coinpulse.domain.PricePoint
import com.example.coinpulse.presentation.watchlist.assertIs
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoinDetailViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun points(vararg prices: String) =
        prices.mapIndexed { i, p -> PricePoint(Instant.EPOCH.plusSeconds(i * 900L), BigDecimal(p)) }

    private class FakeHistory(var result: PriceHistoryResult) : PriceHistoryRepository {
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0
        var lastRequest: Pair<String, String>? = null

        override suspend fun get24hHistory(baseAsset: String, quoteAsset: String): PriceHistoryResult {
            calls++
            lastRequest = baseAsset to quoteAsset
            gate?.await()
            return result
        }
    }

    @Test
    fun startsLoadingThenReady() = runTest {
        val repository = FakeHistory(PriceHistoryResult.Success(points("1", "2", "3")))
        val vm = CoinDetailViewModel("BTC", "USDT", repository)
        assertEquals(ChartUiState.Loading, vm.chart.value)

        runCurrent()

        assertEquals(3, assertIs<ChartUiState.Ready>(vm.chart.value).points.size)
        assertEquals("BTC" to "USDT", repository.lastRequest)
    }

    @Test
    fun failureBecomesUnavailableWithReason() = runTest {
        val vm = CoinDetailViewModel("BTC", "USDT", FakeHistory(PriceHistoryResult.Failure(FailureReason.Offline)))
        runCurrent()
        assertEquals(ChartUiState.Unavailable(FailureReason.Offline), vm.chart.value)
    }

    @Test
    fun fewerThanTwoPointsIsUnavailable() = runTest {
        val vm = CoinDetailViewModel("BTC", "USDT", FakeHistory(PriceHistoryResult.Success(points("1"))))
        runCurrent()
        assertEquals(ChartUiState.Unavailable(FailureReason.Unknown), vm.chart.value)
    }

    @Test
    fun retryReloadsAndRecovers() = runTest {
        val repository = FakeHistory(PriceHistoryResult.Failure(FailureReason.RateLimited))
        val vm = CoinDetailViewModel("BTC", "USDT", repository)
        runCurrent()
        assertIs<ChartUiState.Unavailable>(vm.chart.value)

        repository.result = PriceHistoryResult.Success(points("1", "2"))
        repository.gate = CompletableDeferred()
        vm.onRetry()
        runCurrent()
        assertEquals(ChartUiState.Loading, vm.chart.value)

        repository.gate!!.complete(Unit)
        runCurrent()
        assertIs<ChartUiState.Ready>(vm.chart.value)
        assertEquals(2, repository.calls)
    }

    @Test
    fun overlappingRetriesAreIgnored() = runTest {
        val repository = FakeHistory(PriceHistoryResult.Success(points("1", "2")))
        repository.gate = CompletableDeferred()
        val vm = CoinDetailViewModel("BTC", "USDT", repository)
        runCurrent()

        vm.onRetry()
        vm.onRetry()
        repository.gate!!.complete(Unit)
        runCurrent()

        assertEquals(1, repository.calls)
    }
}
