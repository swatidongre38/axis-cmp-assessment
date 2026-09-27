package com.axis.marketinsights.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class TickDirection { Up, Down, Unchanged }

/** One price update coming off the feed. */
data class Tick(
    val instrumentId: String,
    val price: FixedPoint,
    /** Bonds only - yield to maturity in percent, scale 4 (e.g. 6.9425%). */
    val yieldPct: FixedPoint?,
    /** Increases across the whole feed, not per instrument - lets us spot gaps after a reconnect. */
    val sequence: Long,
    val timestampMillis: Long,
)

/**
 * What we currently know about one instrument. Kept fully immutable on purpose - Compose
 * skips redrawing a row whose Quote object didn't change, and that only works if nothing
 * here can be mutated after the fact.
 */
data class Quote(
    val instrument: Instrument,
    val last: FixedPoint,
    val previousClose: FixedPoint,
    val yieldPct: FixedPoint?,
    val direction: TickDirection,
    /** Sequence number of the last tick applied to this instrument specifically. */
    val sequence: Long,
    val timestampMillis: Long,
    /** Short trailing history of prices for the sparkline, capped in QuoteBook. */
    val history: ImmutableList<Long>,
) {
    val change: FixedPoint get() = last - previousClose
    val changeBps: Long get() = last.basisPointsFrom(previousClose)
}

/** A consistent snapshot of the whole watchlist at one point in time. */
data class MarketSnapshot(
    val quotes: ImmutableList<Quote>,
    val lastSequence: Long,
    val ticksApplied: Long,
) {
    companion object {
        val Empty = MarketSnapshot(persistentListOf(), lastSequence = 0L, ticksApplied = 0L)
    }
}
