package com.axis.marketinsights.data

import com.axis.marketinsights.data.repository.MockOrderRepository
import com.axis.marketinsights.domain.model.AssetClass
import com.axis.marketinsights.domain.model.FixedPoint
import com.axis.marketinsights.domain.order.OrderRequest
import com.axis.marketinsights.domain.order.OrderSide
import com.axis.marketinsights.domain.order.OrderType
import com.axis.marketinsights.domain.time.MarketClock
import com.axis.marketinsights.domain.util.TransientNetworkException
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class MockOrderRepositoryTest {

    private val clock = object : MarketClock { override fun nowMillis() = 0L }
    private val order = OrderRequest(
        instrumentId = "EQ:TEST", side = OrderSide.Buy, type = OrderType.Limit,
        quantity = 10, limitPrice = FixedPoint(100_00L, 2),
    )

    @Test
    fun sameIdempotencyKey_returnsTheSameAck_insteadOfPlacingASecondOrder() = runTest {
        // transientFailureRate = 0.0 so this test is deterministic - it's testing dedupe, not retry.
        val repo = MockOrderRepository(clock = clock, simulatedLatencyMillis = 0L, transientFailureRate = 0.0)
        val key = "same-key-simulating-a-retried-request"

        val first = repo.placeOrder(order, key)
        val second = repo.placeOrder(order, key) // pretend this is a client retry after a lost response

        assertEquals(first.orderId, second.orderId) // NOT two different orders
    }

    @Test
    fun differentIdempotencyKeys_produceDifferentOrders() = runTest {
        val repo = MockOrderRepository(clock = clock, simulatedLatencyMillis = 0L, transientFailureRate = 0.0)

        val first = repo.placeOrder(order, "key-a")
        val second = repo.placeOrder(order, "key-b")

        assertNotEquals(first.orderId, second.orderId)
    }

    @Test
    fun alwaysFails_whenTransientFailureRateIsOne() = runTest {
        val repo = MockOrderRepository(clock = clock, simulatedLatencyMillis = 0L, transientFailureRate = 1.0)
        assertFailsWith<TransientNetworkException> { repo.placeOrder(order, "any-key") }
    }
}
