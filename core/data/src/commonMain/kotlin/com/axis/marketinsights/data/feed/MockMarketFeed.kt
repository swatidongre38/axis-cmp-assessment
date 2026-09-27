package com.axis.marketinsights.data.feed

import com.axis.marketinsights.data.seed.SeededInstrument
import com.axis.marketinsights.domain.model.FixedPoint
import com.axis.marketinsights.domain.model.Tick
import com.axis.marketinsights.domain.time.MarketClock
import com.axis.marketinsights.domain.time.SystemMarketClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.random.Random

/**
 * Fakes a bursty exchange feed - wakes up every [tickIntervalMillis] and fires off 1 to 3
 * ticks, so roughly 250-750 ticks/sec across the whole watchlist. Prices random-walk on the
 * instrument's own tick grid, and bond yields move in the opposite direction to price.
 *
 * All the mutable state lives inside the flow builder, so two collectors never share the
 * same Random or arrays.
 */
class MockMarketFeed(
    private val seeds: List<SeededInstrument>,
    private val tickIntervalMillis: Long = 4L,
    private val clock: MarketClock = SystemMarketClock,
    private val randomSeed: Int = 42,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MarketFeed {

    override fun ticks(): Flow<Tick> = flow {
        require(seeds.isNotEmpty()) { "need at least one instrument to generate a feed" }
        val random = Random(randomSeed)
        val prices = LongArray(seeds.size) { seeds[it].previousClose.unscaled }
        val yields = LongArray(seeds.size) { seeds[it].initialYield?.unscaled ?: 0L }
        var sequence = 0L

        while (true) {
            delay(tickIntervalMillis)
            repeat(random.nextInt(1, 4)) {
                val i = random.nextInt(seeds.size)
                val seed = seeds[i]
                val tickSize = seed.instrument.tickSize
                val steps = random.nextInt(-3, 4)
                prices[i] = (prices[i] + steps * tickSize.unscaled).coerceAtLeast(tickSize.unscaled)

                val yieldPct = seed.initialYield?.let {
                    yields[i] -= steps * 5L // roughly inverse to price movement
                    FixedPoint(yields[i], it.scale)
                }

                emit(
                    Tick(
                        instrumentId = seed.instrument.id,
                        price = FixedPoint(prices[i], tickSize.scale),
                        yieldPct = yieldPct,
                        sequence = ++sequence,
                        timestampMillis = clock.nowMillis(),
                    ),
                )
            }
        }
    }.flowOn(dispatcher)
}
