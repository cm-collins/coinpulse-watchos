package com.example.coinpulse.presentation.watchlist

import com.example.coinpulse.domain.CHANGE_PERCENT_DECIMALS
import com.example.coinpulse.domain.PriceDirection
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Pure, locale-aware formatting. All values stay BigDecimal; nothing is converted to Double. */
object MarketFormatters {
    private const val MINUS = "−"
    private const val PLUS = "+"

    /** >= 1 -> 2 decimals; 0.01 to < 1 -> 4; smaller -> 8, so sub-cent prices keep their digits. */
    fun price(value: BigDecimal, locale: Locale): String {
        val decimals =
            when {
                value.compareTo(BigDecimal.ONE) >= 0 -> 2
                value.compareTo(BigDecimal("0.01")) >= 0 -> 4
                else -> 8
            }
        return format(value, decimals, locale)
    }

    /** Magnitude only, e.g. "2.31"; use [signedPercent] for display with a sign. */
    fun percentMagnitude(value: BigDecimal, locale: Locale): String =
        format(value.abs(), CHANGE_PERCENT_DECIMALS, locale)

    /** "+2.31%", "−0.87%" or "0.00%" (flat). */
    fun signedPercent(value: BigDecimal, direction: PriceDirection, locale: Locale): String {
        val sign =
            when (direction) {
                PriceDirection.Up -> PLUS
                PriceDirection.Down -> MINUS
                PriceDirection.Flat -> ""
            }
        return "$sign${percentMagnitude(value, locale)}%"
    }

    private fun format(value: BigDecimal, decimals: Int, locale: Locale): String =
        NumberFormat.getNumberInstance(locale)
            .apply {
                minimumFractionDigits = decimals
                maximumFractionDigits = decimals
                isGroupingUsed = true
            }
            .format(value.setScale(decimals, RoundingMode.HALF_UP))
}
