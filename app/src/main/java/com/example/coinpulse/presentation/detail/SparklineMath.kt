package com.example.coinpulse.presentation.detail

import com.example.coinpulse.domain.PricePoint
import java.math.RoundingMode

/**
 * Normalizes prices to 0f (lowest) .. 1f (highest) for plotting. The division is done in
 * BigDecimal; only the final fractions become Float, purely for drawing. A flat series sits in
 * the middle (0.5f) instead of dividing by zero.
 */
internal fun normalizePrices(points: List<PricePoint>): List<Float> {
    if (points.isEmpty()) return emptyList()
    val low = points.minOf { it.price }
    val high = points.maxOf { it.price }
    val span = high - low
    if (span.signum() == 0) return points.map { 0.5f }
    return points.map { (it.price - low).divide(span, 4, RoundingMode.HALF_UP).toFloat() }
}
