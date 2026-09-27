package com.axis.marketinsights.data.repository

import com.axis.marketinsights.data.feed.MarketFeed
import com.axis.marketinsights.data.seed.SeededInstrument
import com.axis.marketinsights.domain.model.MarketSnapshot
import com.axis.marketinsights.domain.repository.MarketRepository
import com.axis.marketinsights.domain.time.MarketClock
import com.axis.marketinsights.domain.time.SystemMarketClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.scan

/**
 * The feed can push ~500 ticks/sec, way more than a screen needs to redraw. Every tick still
 * gets folded into the book via scan(), so nothing is ever lost - but sample() only lets a
 * snapshot through every [conflationWindowMillis], so the UI gets updates at a rate a person
 * can actually read, no matter how busy the market gets.
 */
@OptIn(FlowPreview::class)
class DefaultMarketRepository(
    private val feed: MarketFeed,
    private val seeds: List<SeededInstrument>,
    private val conflationWindowMillis: Long = 50L,
    private val historySize: Int = 40,
    private val clock: MarketClock = SystemMarketClock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MarketRepository {

    override fun observeMarket(): Flow<MarketSnapshot> = flow {
        val initialBook = QuoteBook.initial(seeds, historySize, clock.nowMillis())
        emitAll(
            feed.ticks()
                .scan(initialBook) { book, tick -> book.apply(tick) }
                .sample(conflationWindowMillis)
                .map { it.toSnapshot() },
        )
    }.flowOn(dispatcher)
}
