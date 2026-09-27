package com.axis.marketinsights.domain.order

import com.axis.marketinsights.domain.model.FixedPoint
import com.axis.marketinsights.domain.model.Quote
import com.axis.marketinsights.domain.time.MarketClock
import kotlin.math.absoluteValue

enum class ValidationRule {
    UnknownInstrument,
    QuantityNotPositive,
    QuantityTooLarge,
    LotSizeMismatch,
    LimitPriceMissing,
    TickSizeMismatch,
    OutsidePriceBand,
    OrderValueExceedsLimit,
    StaleQuote,
}

data class Violation(val rule: ValidationRule, val message: String)

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Rejected(val violations: List<Violation>) : ValidationResult
}

/**
 * Runs pre-trade checks before an order goes to the server. Same class on Android and iOS,
 * so the rules can't drift between platforms. No network calls here, just math and rules,
 * which is why it's easy to unit test (see TradeValidatorTest).
 *
 * Collects every problem it finds instead of stopping at the first one, so the user isn't
 * stuck fixing errors one at a time.
 */
class TradeValidator(
    private val limits: RiskLimits,
    private val clock: MarketClock,
) {

    fun validate(order: OrderRequest, quote: Quote?): ValidationResult {
        // No quote at all, or a quote for a different instrument than the order - nothing
        // else worth checking after that.
        if (quote == null || quote.instrument.id != order.instrumentId) {
            return ValidationResult.Rejected(
                listOf(Violation(ValidationRule.UnknownInstrument, "No live quote for ${order.instrumentId}")),
            )
        }

        val problems = mutableListOf<Violation>()

        checkQuantity(order, quote, problems)

        // Market orders trade at the live price. Limit orders need their price checked
        // first - checkLimitPrice does that and hands the price back so it can be reused
        // below instead of re-parsing it.
        val price = when (order.type) {
            OrderType.Market -> quote.last
            OrderType.Limit -> checkLimitPrice(order, quote, problems)
        }

        // Skip this if the price or quantity was already invalid - no point flagging a
        // huge order value on top of a quantity that's wrong anyway.
        if (price != null && order.quantity in 1..limits.maxQuantity) {
            val orderValue = price * order.quantity
            if (orderValue > limits.maxOrderValue) {
                problems += Violation(
                    ValidationRule.OrderValueExceedsLimit,
                    "Order value ₹${orderValue.format()} exceeds the per-order limit of ₹${limits.maxOrderValue.format()}",
                )
            }
        }

        val quoteAgeMs = clock.nowMillis() - quote.timestampMillis
        if (quoteAgeMs > limits.maxQuoteAgeMillis) {
            problems += Violation(
                ValidationRule.StaleQuote,
                "Live price is $quoteAgeMs ms old. Wait for a fresh quote before trading.",
            )
        }

        return if (problems.isEmpty()) ValidationResult.Valid else ValidationResult.Rejected(problems)
    }

    private fun checkQuantity(order: OrderRequest, quote: Quote, problems: MutableList<Violation>) {
        val lotSize = quote.instrument.lotSize
        when {
            order.quantity <= 0L ->
                problems += Violation(ValidationRule.QuantityNotPositive, "Quantity must be greater than zero")
            order.quantity > limits.maxQuantity ->
                problems += Violation(ValidationRule.QuantityTooLarge, "Quantity exceeds the maximum of ${limits.maxQuantity}")
            order.quantity % lotSize != 0L ->
                // e.g. lot size 10 means 10, 20, 30 shares are fine, 15 isn't.
                problems += Violation(ValidationRule.LotSizeMismatch, "Quantity must be a multiple of the lot size ($lotSize)")
        }
    }

    private fun checkLimitPrice(order: OrderRequest, quote: Quote, problems: MutableList<Violation>): FixedPoint? {
        val instrument = quote.instrument
        val price = order.limitPrice

        if (price == null || price.unscaled <= 0L) {
            problems += Violation(
                ValidationRule.LimitPriceMissing,
                "Enter a valid limit price (up to ${instrument.priceScale} decimals)",
            )
            return null
        }

        if (!price.isMultipleOf(instrument.tickSize)) {
            problems += Violation(
                ValidationRule.TickSizeMismatch,
                "Price must move in steps of ${instrument.tickSize.format(grouping = false)}",
            )
        }

        // Distance from yesterday's close, either direction - too low is just as much a
        // fat-finger risk as too high.
        val distanceBps = price.basisPointsFrom(quote.previousClose).absoluteValue
        if (distanceBps > limits.priceBandBps) {
            problems += Violation(
                ValidationRule.OutsidePriceBand,
                "Price is outside the ±${FixedPoint(limits.priceBandBps, 2).format()}% band " +
                    "around the previous close of ${quote.previousClose.format()}",
            )
        }

        return price
    }
}
