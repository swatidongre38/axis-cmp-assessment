package com.axis.marketinsights

import androidx.lifecycle.SavedStateHandle
import com.axis.marketinsights.data.feed.MockMarketFeed
import com.axis.marketinsights.data.repository.DefaultMarketRepository
import com.axis.marketinsights.data.repository.MockOrderRepository
import com.axis.marketinsights.data.repository.MockResearchRepository
import com.axis.marketinsights.data.seed.SeedData
import com.axis.marketinsights.domain.order.PlaceOrderUseCase
import com.axis.marketinsights.domain.order.RiskLimits
import com.axis.marketinsights.domain.order.TradeValidator
import com.axis.marketinsights.domain.repository.MarketRepository
import com.axis.marketinsights.domain.repository.OrderRepository
import com.axis.marketinsights.domain.repository.ResearchRepository
import com.axis.marketinsights.domain.time.SystemMarketClock
import com.axis.marketinsights.feature.market.MarketViewModel

/**
 * Manual dependency graph - everything constructor-injected by hand. Kept simple on purpose
 * for this exercise; I'd bring in Hilt or Koin once there's more than a handful of bindings
 * to wire up, but for this it would just be extra ceremony.
 */
object AppGraph {
    private val instrumentSeeds by lazy { SeedData.instruments() }

    val marketRepository: MarketRepository by lazy {
        DefaultMarketRepository(feed = MockMarketFeed(instrumentSeeds), seeds = instrumentSeeds)
    }
    val researchRepository: ResearchRepository by lazy { MockResearchRepository() }
    private val orderRepository: OrderRepository by lazy { MockOrderRepository() }

    val tradeValidator: TradeValidator by lazy { TradeValidator(RiskLimits.standard, SystemMarketClock) }

    fun marketViewModel(savedStateHandle: SavedStateHandle? = null): MarketViewModel = MarketViewModel(
        marketRepository = marketRepository,
        researchRepository = researchRepository,
        placeOrder = PlaceOrderUseCase(tradeValidator, orderRepository),
        savedStateHandle = savedStateHandle,
    )
}
