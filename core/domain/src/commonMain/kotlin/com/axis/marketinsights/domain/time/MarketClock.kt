package com.axis.marketinsights.domain.time

/**
 * Injected wherever time matters so tests can control it directly instead of dealing with
 * real elapsed time.
 */
interface MarketClock {
    fun nowMillis(): Long
}

object SystemMarketClock : MarketClock {
    override fun nowMillis(): Long = epochMillis()
}

internal expect fun epochMillis(): Long
