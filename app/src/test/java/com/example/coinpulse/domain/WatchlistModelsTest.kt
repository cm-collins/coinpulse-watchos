package com.example.coinpulse.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchlistModelsTest {
    private fun item(price: String, low: String, high: String, change: String = "1.00") =
        WatchlistItem("BTC", "USDT", BigDecimal(price), BigDecimal(change), BigDecimal(low), BigDecimal(high))

    @Test
    fun rangePositionIsFractionBetweenLowAndHigh() {
        assertEquals(0.5f, item("150", "100", "200").rangePosition!!, 0.0001f)
        assertEquals(0f, item("100", "100", "200").rangePosition!!, 0.0001f)
        assertEquals(1f, item("200", "100", "200").rangePosition!!, 0.0001f)
    }

    @Test
    fun rangePositionKeepsPrecisionForSmallPrices() {
        // 0.5234 between 0.5100 and 0.5400 -> 0.0134 / 0.0300 = 0.4467
        assertEquals(0.4467f, item("0.5234", "0.5100", "0.5400").rangePosition!!, 0.0001f)
    }

    @Test
    fun rangePositionIsClampedWhenPriceMovedOutsideTheRange() {
        assertEquals(1f, item("250", "100", "200").rangePosition!!, 0.0001f)
        assertEquals(0f, item("50", "100", "200").rangePosition!!, 0.0001f)
    }

    @Test
    fun rangePositionIsNullWithoutARange() {
        assertNull(item("100", "100", "100").rangePosition)
        assertNull(item("100", "200", "100").rangePosition)
    }
}
