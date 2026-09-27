package com.axis.marketinsights.domain.order

import com.axis.marketinsights.domain.model.Quote
import com.axis.marketinsights.domain.repository.OrderRepository
import com.axis.marketinsights.domain.util.RetryPolicy
import com.axis.marketinsights.domain.util.retryWithBackoff
import com.axis.marketinsights.domain.util.suspendRunCatching

sealed interface PlaceOrderResult {
    data class Accepted(val ack: OrderAck) : PlaceOrderResult
    data class Rejected(val violations: List<Violation>) : PlaceOrderResult
    data class Failed(val reason: String) : PlaceOrderResult
}

/**
 * Validates locally first, then submits - with retry. This is the one place in the app that
 * retries a MUTATING call, so it's also the one place that has to get idempotency right: the
 * same key is generated once and reused across every retry attempt (see IdempotencyKey.kt),
 * so a retried request can never double-place an order even if the first attempt actually
 * succeeded and only the response was lost.
 *
 * Retry only fires for TransientNetworkException (timeouts, 5xx-equivalents) - a rejection
 * from TradeValidator never reaches this point at all, and any other failure the repository
 * throws is treated as non-retryable on purpose: retrying something we don't understand is
 * riskier than surfacing it to the user.
 */
class PlaceOrderUseCase(
    private val validator: TradeValidator,
    private val orders: OrderRepository,
    private val retryPolicy: RetryPolicy = RetryPolicy(maxAttempts = 3, initialDelayMillis = 300L),
) {
    suspend operator fun invoke(order: OrderRequest, quote: Quote?): PlaceOrderResult {
        val validation = validator.validate(order, quote)
        if (validation is ValidationResult.Rejected) return PlaceOrderResult.Rejected(validation.violations)

        val idempotencyKey = newIdempotencyKey()
        return suspendRunCatching {
            retryWithBackoff(retryPolicy) { orders.placeOrder(order, idempotencyKey) }
        }.fold(
            onSuccess = { PlaceOrderResult.Accepted(it) },
            onFailure = { PlaceOrderResult.Failed(it.message ?: "Order could not be placed. Please retry.") },
        )
    }
}
