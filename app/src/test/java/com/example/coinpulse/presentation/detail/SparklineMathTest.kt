package com.example.coinpulse.presentation.detail

import com.example.coinpulse.domain.PricePoint
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SparklineMathTest {
    private fun points(vararg prices: String) =
        prices.mapIndexed { i, p -> PricePoint(Instant.EPOCH.plusSeconds(i * 900L), BigDecimal(p)) }

    @Test
    fun mapsLowestToZeroAndHighestToOne() {
        val values = normalizePrices(points("100", "150", "200", "125"))
        assertEquals(listOf(0f, 0.5f, 1f, 0.25f), values)
    }

    @Test
    fun flatSeriesSitsInTheMiddleWithoutDividingByZero() {
        assertEquals(listOf(0.5f, 0.5f, 0.5f), normalizePrices(points("42.10", "42.10", "42.10")))
    }

    @Test
    fun emptyInputGivesEmptyOutput() {
        assertTrue(normalizePrices(emptyList()).isEmpty())
    }

    @Test
    fun keepsPrecisionForSubCentPrices() {
        // 0.00000125 between 0.00000100 and 0.00000200 -> exactly 0.25
        assertEquals(listOf(0f, 0.25f, 1f), normalizePrices(points("0.00000100", "0.00000125", "0.00000200")))
    }
}
