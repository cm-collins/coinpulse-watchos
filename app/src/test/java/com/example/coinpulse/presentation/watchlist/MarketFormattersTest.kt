package com.example.coinpulse.presentation.watchlist

import com.example.coinpulse.domain.PriceDirection
import com.example.coinpulse.domain.WatchlistItem
import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketFormattersTest {
    private val us = Locale.US

    @Test
    fun pricesUseGroupingAndMagnitudeBasedDecimals() {
        assertEquals("67,234.50", MarketFormatters.price(BigDecimal("67234.5"), us))
        assertEquals("1.00", MarketFormatters.price(BigDecimal("1"), us))
        assertEquals("0.5234", MarketFormatters.price(BigDecimal("0.52340000"), us))
        assertEquals("0.00000123", MarketFormatters.price(BigDecimal("0.00000123"), us))
    }

    @Test
    fun priceKeepsFullPrecisionWithoutDoubleRounding() {
        assertEquals("12,345,678,901,234.57", MarketFormatters.price(BigDecimal("12345678901234.565"), us))
    }

    @Test
    fun percentIsSignedWithTrueMinus() {
        assertEquals("+2.31%", MarketFormatters.signedPercent(BigDecimal("2.31"), PriceDirection.Up, us))
        assertEquals("−0.87%", MarketFormatters.signedPercent(BigDecimal("-0.87"), PriceDirection.Down, us))
        assertEquals("0.00%", MarketFormatters.signedPercent(BigDecimal("0.00"), PriceDirection.Flat, us))
    }

    @Test
    fun percentMagnitudeDropsSign() {
        assertEquals("0.87", MarketFormatters.percentMagnitude(BigDecimal("-0.87"), us))
    }

    @Test
    fun changeThatRoundsToZeroIsFlat() {
        fun direction(change: String) =
            WatchlistItem("BTC", "USDT", BigDecimal.ONE, BigDecimal(change), BigDecimal.ONE, BigDecimal.ONE).direction
        assertEquals(PriceDirection.Flat, direction("0.004"))
        assertEquals(PriceDirection.Flat, direction("-0.004"))
        assertEquals(PriceDirection.Up, direction("0.005"))
        assertEquals(PriceDirection.Down, direction("-0.005"))
    }
}
