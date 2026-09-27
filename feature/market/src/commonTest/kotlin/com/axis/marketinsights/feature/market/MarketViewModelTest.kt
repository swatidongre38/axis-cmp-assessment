package com.axis.marketinsights.feature.market

import com.axis.marketinsights.domain.model.AssetClass
import com.axis.marketinsights.domain.model.FixedPoint
import com.axis.marketinsights.domain.model.Instrument
import com.axis.marketinsights.domain.model.MarketSnapshot
import com.axis.marketinsights.domain.model.Quote
import com.axis.marketinsights.domain.model.ResearchCall
import com.axis.marketinsights.domain.model.TickDirection
import com.axis.marketinsights.domain.order.OrderAck
import com.axis.marketinsights.domain.order.OrderRequest
import com.axis.marketinsights.domain.order.OrderSide
import com.axis.marketinsights.domain.order.OrderType
import com.axis.marketinsights.domain.order.PlaceOrderUseCase
import com.axis.marketinsights.domain.order.RiskLimits
import com.axis.marketinsights.domain.order.TradeValidator
import com.axis.marketinsights.domain.order.ValidationRule
import com.axis.marketinsights.domain.repository.MarketRepository
import com.axis.marketinsights.domain.repository.OrderRepository
import com.axis.marketinsights.domain.repository.ResearchRepository
import com.axis.marketinsights.domain.time.MarketClock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MarketViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testInstrument = Instrument("EQ:TEST", "TEST", "Test Ltd", AssetClass.Equity, FixedPoint(5L, 2), lotSize = 10)
    private val testQuote = Quote(
        testInstrument, FixedPoint(10_000L, 2), FixedPoint(10_000L, 2), null,
        TickDirection.Unchanged, sequence = 1L, timestampMillis = 1_000L, history = persistentListOf(),
    )
    private val marketRepository = object : MarketRepository {
        override fun observeMarket(): Flow<MarketSnapshot> = flowOf(MarketSnapshot(persistentListOf(testQuote), 1L, 1L))
    }
    private val researchRepository = object : ResearchRepository {
        override suspend fun getResearchCalls(): List<ResearchCall> = emptyList()
    }
    private val fixedClock = object : MarketClock { override fun nowMillis() = 1_500L }

    @BeforeTest fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun buildViewModel(orders: OrderRepository) = MarketViewModel(
        marketRepository, researchRepository,
        PlaceOrderUseCase(TradeValidator(RiskLimits.standard, fixedClock), orders),
        computeDispatcher = testDispatcher,
    )

    @Test
    fun invalidLotSize_isRejectedLocally_withoutHittingTheNetwork() = runTest(testDispatcher) {
        val orders = object : OrderRepository {
            override suspend fun placeOrder(order: OrderRequest, idempotencyKey: String): OrderAck = error("placeOrder should not be called")
        }
        val viewModel = buildViewModel(orders)
        backgroundScope.launch { viewModel.quotes.collect {} } // simulate the UI subscribing
        advanceUntilIdle()

        viewModel.onIntent(MarketIntent.OpenTicket("EQ:TEST"))
        viewModel.onIntent(MarketIntent.SubmitOrder(OrderSide.Buy, OrderType.Limit, quantityText = "15", limitPriceText = "100.00"))
        advanceUntilIdle()

        val ticket = assertNotNull(viewModel.state.value.ticket)
        assertEquals(listOf(ValidationRule.LotSizeMismatch), ticket.violations.map { it.rule })
        assertFalse(ticket.isSubmitting)
    }

    @Test
    fun validOrder_closesTheTicket_andSendsOneEffect() = runTest(testDispatcher) {
        val orders = object : OrderRepository {
            override suspend fun placeOrder(order: OrderRequest, idempotencyKey: String) = OrderAck("AXS-000001", 1_500L)
        }
        val viewModel = buildViewModel(orders)
        backgroundScope.launch { viewModel.quotes.collect {} }
        advanceUntilIdle()

        viewModel.onIntent(MarketIntent.OpenTicket("EQ:TEST"))
        viewModel.onIntent(MarketIntent.SubmitOrder(OrderSide.Buy, OrderType.Limit, quantityText = "20", limitPriceText = "100.05"))
        advanceUntilIdle()

        assertNull(viewModel.state.value.ticket)

        var receivedEffect: MarketEffect? = null
        backgroundScope.launch { viewModel.effects.collect { receivedEffect = it } }
        advanceUntilIdle()
        assertIs<MarketEffect.OrderPlaced>(receivedEffect)
    }
}
