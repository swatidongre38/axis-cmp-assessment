package com.axis.marketinsights.data

import com.axis.marketinsights.data.feed.MarketFeed
import com.axis.marketinsights.data.repository.DefaultMarketRepository
import com.axis.marketinsights.data.seed.SeedData
import com.axis.marketinsights.domain.model.FixedPoint
import com.axis.marketinsights.domain.model.Tick
import com.axis.marketinsights.domain.time.MarketClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultMarketRepositoryTest {

    private val seeds = SeedData.instruments(syntheticCount = 0).filter { it.instrument.id == "EQ:AXISBANK" }
    private val axisBank = seeds.single()
    private val fixedClock = object : MarketClock { override fun nowMillis() = 0L }

    @Test
    fun conflatesUpdatesButNeverDropsAnAppliedTick() = runTest {
        val tickCount = 500
        // 500 ticks 1ms apart in virtual time = ~500 ticks/sec.
        val fakeFeed = MarketFeed {
            flow {
                repeat(tickCount) { i ->
                    val n = i + 1L
                    emit(Tick(axisBank.instrument.id, FixedPoint(axisBank.previousClose.unscaled + n * 5, 2), null, n, n))
                    delay(1)
                }
            }
        }

        val repository = DefaultMarketRepository(
            feed = fakeFeed,
            seeds = seeds,
            conflationWindowMillis = 50L,
            clock = fixedClock,
            dispatcher = StandardTestDispatcher(testScheduler), // keeps virtual time deterministic
        )

        val snapshots = repository.observeMarket().toList()

        assertTrue(snapshots.isNotEmpty())
        assertTrue(snapshots.size <= tickCount / 40, "expected roughly 10 snapshots, got ${snapshots.size}")

        snapshots.forEach { snapshot ->
            val quote = snapshot.quotes.single()
            // Every snapshot should reflect ALL ticks applied so far - conflation only drops
            // intermediate frames, never the underlying data.
            assertEquals(axisBank.previousClose.unscaled + snapshot.ticksApplied * 5, quote.last.unscaled)
        }
        assertTrue(snapshots.zipWithNext().all { (a, b) -> b.ticksApplied > a.ticksApplied })
    }
}
