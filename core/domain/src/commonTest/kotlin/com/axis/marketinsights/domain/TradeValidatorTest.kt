package com.axis.marketinsights.domain

import com.axis.marketinsights.domain.model.AssetClass
import com.axis.marketinsights.domain.model.FixedPoint
import com.axis.marketinsights.domain.model.Instrument
import com.axis.marketinsights.domain.model.Quote
import com.axis.marketinsights.domain.model.TickDirection
import com.axis.marketinsights.domain.order.OrderRequest
import com.axis.marketinsights.domain.order.OrderSide
import com.axis.marketinsights.domain.order.OrderType
import com.axis.marketinsights.domain.order.RiskLimits
import com.axis.marketinsights.domain.order.TradeValidator
import com.axis.marketinsights.domain.order.ValidationResult
import com.axis.marketinsights.domain.order.ValidationRule
import com.axis.marketinsights.domain.time.MarketClock
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TradeValidatorTest {

    private val now = 1_000_000L
    private val fixedClock = object : MarketClock { override fun nowMillis() = now }
    private val validator = TradeValidator(RiskLimits.standard, fixedClock)

    private val axisBank = Instrument(
        id = "EQ:AXISBANK", symbol = "AXISBANK", name = "Axis Bank",
        assetClass = AssetClass.Equity, tickSize = FixedPoint(5L, 2), lotSize = 1,
    )

    private fun quoteFor(
        last: Long = 116_555L,
        timestamp: Long = now - 100,
        lotSize: Int = 1,
    ) = Quote(
        instrument = axisBank.copy(lotSize = lotSize),
        last = FixedPoint(last, 2),
        previousClose = FixedPoint(116_555L, 2),
        yieldPct = null,
        direction = TickDirection.Unchanged,
        sequence = 1L,
        timestampMillis = timestamp,
        history = persistentListOf(),
    )

    private fun limitOrder(qty: Long, price: String) = OrderRequest(
        instrumentId = axisBank.id, side = OrderSide.Buy, type = OrderType.Limit,
        quantity = qty, limitPrice = FixedPoint.parse(price, 2),
    )

    private fun rulesIn(result: ValidationResult): List<ValidationRule> =
        (result as? ValidationResult.Rejected)?.violations?.map { it.rule }.orEmpty()

    @Test
    fun validLimitOrderPasses() {
        assertIs<ValidationResult.Valid>(validator.validate(limitOrder(10, "1165.50"), quoteFor()))
    }

    @Test
    fun rejectsPriceOffTheTickGrid() {
        assertEquals(listOf(ValidationRule.TickSizeMismatch), rulesIn(validator.validate(limitOrder(10, "1165.52"), quoteFor())))
    }

    @Test
    fun rejectsQuantityThatIsNotAMultipleOfLotSize() {
        assertEquals(listOf(ValidationRule.LotSizeMismatch), rulesIn(validator.validate(limitOrder(15, "1165.50"), quoteFor(lotSize = 10))))
    }

    @Test
    fun rejectsPriceOutsideTheBand() {
        assertEquals(listOf(ValidationRule.OutsidePriceBand), rulesIn(validator.validate(limitOrder(1, "1500.00"), quoteFor())))
    }

    @Test
    fun rejectsStaleQuotes() {
        val result = validator.validate(limitOrder(1, "1165.50"), quoteFor(timestamp = now - 5_000))
        assertEquals(listOf(ValidationRule.StaleQuote), rulesIn(result))
    }

    @Test
    fun collectsEveryViolationInOnePass() {
        val order = OrderRequest(axisBank.id, OrderSide.Sell, OrderType.Limit, quantity = 0, limitPrice = null)
        assertEquals(
            listOf(ValidationRule.QuantityNotPositive, ValidationRule.LimitPriceMissing),
            rulesIn(validator.validate(order, quoteFor())),
        )
    }

    @Test
    fun rejectsOrderValueOverTheLimit() {
        val order = OrderRequest(axisBank.id, OrderSide.Buy, OrderType.Market, quantity = 9_000, limitPrice = null)
        assertEquals(listOf(ValidationRule.OrderValueExceedsLimit), rulesIn(validator.validate(order, quoteFor())))
    }

    @Test
    fun rejectsWhenThereIsNoQuoteForTheInstrument() {
        assertEquals(listOf(ValidationRule.UnknownInstrument), rulesIn(validator.validate(limitOrder(1, "1165.50"), null)))
    }
}
