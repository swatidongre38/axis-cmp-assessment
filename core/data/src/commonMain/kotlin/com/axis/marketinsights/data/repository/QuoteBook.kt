package com.axis.marketinsights.data.repository

import com.axis.marketinsights.data.seed.SeededInstrument
import com.axis.marketinsights.domain.model.MarketSnapshot
import com.axis.marketinsights.domain.model.Quote
import com.axis.marketinsights.domain.model.Tick
import com.axis.marketinsights.domain.model.TickDirection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList

/**
 * Order-preserving, immutable book of quotes. Applying a tick returns a new QuoteBook that
 * shares every untouched Quote with the previous one - that's what makes Compose's "skip if
 * unchanged" check basically free: unchanged rows are literally the same object as before.
 */
internal class QuoteBook private constructor(
    private val order: ImmutableList<String>,
    private val quotes: PersistentMap<String, Quote>,
    private val lastSequence: Long,
    private val ticksApplied: Long,
    private val historySize: Int,
) {

    fun apply(tick: Tick): QuoteBook {
        val existing = quotes[tick.instrumentId] ?: return this // instrument we don't track, ignore
        if (tick.sequence <= existing.sequence) return this        // stale or duplicate tick

        val direction = when {
            tick.price > existing.last -> TickDirection.Up
            tick.price < existing.last -> TickDirection.Down
            else -> TickDirection.Unchanged
        }

        val history = existing.history.toPersistentList()
            .add(tick.price.unscaled)
            .let { if (it.size > historySize) it.removeAt(0) else it }

        val updated = existing.copy(
            last = tick.price,
            yieldPct = tick.yieldPct ?: existing.yieldPct,
            direction = direction,
            sequence = tick.sequence,
            timestampMillis = tick.timestampMillis,
            history = history,
        )

        return QuoteBook(order, quotes.put(tick.instrumentId, updated), tick.sequence, ticksApplied + 1, historySize)
    }

    fun toSnapshot(): MarketSnapshot = MarketSnapshot(
        quotes = order.map { quotes.getValue(it) }.toImmutableList(),
        lastSequence = lastSequence,
        ticksApplied = ticksApplied,
    )

    companion object {
        fun initial(seeds: List<SeededInstrument>, historySize: Int, nowMillis: Long): QuoteBook {
            var map = persistentMapOf<String, Quote>()
            seeds.forEach { seed ->
                map = map.put(
                    seed.instrument.id,
                    Quote(
                        instrument = seed.instrument,
                        last = seed.previousClose,
                        previousClose = seed.previousClose,
                        yieldPct = seed.initialYield,
                        direction = TickDirection.Unchanged,
                        sequence = 0L,
                        timestampMillis = nowMillis,
                        history = persistentListOf(seed.previousClose.unscaled),
                    ),
                )
            }
            return QuoteBook(seeds.map { it.instrument.id }.toImmutableList(), map, 0L, 0L, historySize)
        }
    }
}
