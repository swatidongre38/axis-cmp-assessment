package com.axis.marketinsights.data.feed

import com.axis.marketinsights.domain.model.Tick
import kotlinx.coroutines.flow.Flow

/** Whatever produces ticks - a real feed would be a WebSocket, here it's MockMarketFeed. */
fun interface MarketFeed {
    fun ticks(): Flow<Tick>
}
