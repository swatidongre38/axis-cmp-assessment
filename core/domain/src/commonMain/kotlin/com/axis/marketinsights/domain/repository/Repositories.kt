package com.axis.marketinsights.domain.repository

import com.axis.marketinsights.domain.model.MarketSnapshot
import com.axis.marketinsights.domain.model.ResearchCall
import com.axis.marketinsights.domain.order.OrderAck
import com.axis.marketinsights.domain.order.OrderRequest
import kotlinx.coroutines.flow.Flow

interface MarketRepository {
    /** Cold stream of conflated snapshots - main-safe, all the real work happens off the main thread. */
    fun observeMarket(): Flow<MarketSnapshot>
}

interface ResearchRepository {
    suspend fun getResearchCalls(): List<ResearchCall>
}

interface OrderRepository {
    /**
     * [idempotencyKey] is generated once per order attempt by PlaceOrderUseCase and passed
     * through unchanged on every retry - a real backend would dedupe on this key so a retried
     * request can never create a second order.
     */
    suspend fun placeOrder(order: OrderRequest, idempotencyKey: String): OrderAck
}
