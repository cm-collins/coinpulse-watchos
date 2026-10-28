package com.example.coinpulse.presentation.watchlist

import org.junit.Assert.assertTrue

inline fun <reified T> assertIs(value: Any?): T {
    assertTrue("Expected ${T::class.simpleName} but was $value", value is T)
    return value as T
}
