package com.axis.marketinsights.data.repository

import com.axis.marketinsights.domain.order.OrderAck
import com.axis.marketinsights.domain.order.OrderRequest
import com.axis.marketinsights.domain.repository.OrderRepository
import com.axis.marketinsights.domain.time.MarketClock
import com.axis.marketinsights.domain.time.SystemMarketClock
import com.axis.marketinsights.domain.util.TransientNetworkException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Stands in for a real "POST /orders" call, including the two things that actually matter
 * for a trading app's order endpoint:
 *
 *  - Idempotency: [idempotencyKey] is checked against acks already issued BEFORE doing any
 *    work. If PlaceOrderUseCase retried the same order after a timeout, this returns the
 *    exact same OrderAck instead of creating a second order - this is what makes retrying a
 *    mutating call actually safe rather than just "usually fine".
 *  - Transient failures: [transientFailureRate] fails a fraction of NEW orders with
 *    TransientNetworkException, so PlaceOrderUseCase's retry logic has something real to do.
 *    A retried order always hits the idempotency check first, so it never re-rolls the dice
 *    on an order that actually already succeeded.
 */
class MockOrderRepository(
    private val clock: MarketClock = SystemMarketClock,
    private val simulatedLatencyMillis: Long = 300L,
    private val transientFailureRate: Double = 0.15,
    private val random: Random = Random.Default,
) : OrderRepository {

    private val lock = Mutex()
    private var orderCounter = 0L
    private val acksByIdempotencyKey = mutableMapOf<String, OrderAck>()

    override suspend fun placeOrder(order: OrderRequest, idempotencyKey: String): OrderAck {
        lock.withLock { acksByIdempotencyKey[idempotencyKey] }?.let { return it } // already placed - don't redo it

        delay(simulatedLatencyMillis)
        if (random.nextDouble() < transientFailureRate) {
            throw TransientNetworkException("simulated order-endpoint timeout")
        }

        return lock.withLock {
            // Check again inside the lock - two retries could both pass the check above before
            // either one finished, and we only want one of them to actually create the order.
            acksByIdempotencyKey[idempotencyKey]?.let { return@withLock it }
            val ack = OrderAck(
                orderId = "AXS-" + (++orderCounter).toString().padStart(6, '0'),
                acceptedAtMillis = clock.nowMillis(),
            )
            acksByIdempotencyKey[idempotencyKey] = ack
            ack
        }
    }
}
