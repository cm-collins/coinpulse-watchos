package com.example.coinpulse.data.remote

object Fixtures {
    fun load(name: String): String =
        requireNotNull(Fixtures::class.java.classLoader?.getResource("binance/$name")) { "Missing fixture $name" }
            .readText()
}
