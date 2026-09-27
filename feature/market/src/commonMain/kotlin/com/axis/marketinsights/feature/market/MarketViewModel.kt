package com.axis.marketinsights.feature.market

import androidx.lifecycle.SavedStateHandle
import com.axis.marketinsights.domain.model.AssetClass
import com.axis.marketinsights.domain.model.FixedPoint
import com.axis.marketinsights.domain.model.MarketSnapshot
import com.axis.marketinsights.domain.model.Quote
import com.axis.marketinsights.domain.order.OrderRequest
import com.axis.marketinsights.domain.order.OrderSide
import com.axis.marketinsights.domain.order.OrderType
import com.axis.marketinsights.domain.order.PlaceOrderResult
import com.axis.marketinsights.domain.order.PlaceOrderUseCase
import com.axis.marketinsights.domain.repository.MarketRepository
import com.axis.marketinsights.domain.repository.ResearchRepository
import com.axis.marketinsights.domain.util.suspendRunCatching
import com.axis.marketinsights.mvi.BaseViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * State/intent/effect plumbing (the _state, the effects Channel, setState/sendEffect) all
 * comes from BaseViewModel - this class only implements what's specific to this screen:
 * what the state actually contains, how each intent is handled, and the extra fast-lane
 * `quotes` stream that BaseViewModel intentionally doesn't try to model.
 *
 * [savedStateHandle] lets an open order ticket survive process death: we don't persist the
 * whole OrderTicketState (it isn't trivially saveable), just the instrument id and side
 * needed to rebuild it once a live Quote for that instrument is available again.
 */
class MarketViewModel(
    marketRepository: MarketRepository,
    private val researchRepository: ResearchRepository,
    private val placeOrder: PlaceOrderUseCase,
    savedStateHandle: SavedStateHandle? = null,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<MarketUiState, MarketIntent, MarketEffect>(MarketUiState(), savedStateHandle) {

    /**
     * WhileSubscribed(5s) means a rotation doesn't restart the feed, but the app going to the
     * background for more than 5 seconds does stop it - no point burning battery and data on
     * a screen nobody's looking at.
     */
    private val market: StateFlow<MarketSnapshot> = marketRepository.observeMarket()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FEED_STOP_DELAY_MS), MarketSnapshot.Empty)

    /**
     * The fast lane. BaseViewModel gives every screen ONE state stream by design - this is
     * the deliberate exception: quotes changes ~20x/sec and mixing it into `state` would mean
     * every recomposition-sensitive part of the screen re-evaluates on every tick.
     */
    val quotes: StateFlow<QuotesUiState> =
        combine(market, state.map { it.filter }.distinctUntilChanged()) { snapshot, filter ->
            QuotesUiState(
                visible = snapshot.quotes.filteredBy(filter),
                bySymbol = snapshot.quotes.associateBy { it.instrument.symbol }.toImmutableMap(),
                ticksApplied = snapshot.ticksApplied,
            )
        }
            .flowOn(computeDispatcher) // keep this mapping off the main thread
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FEED_STOP_DELAY_MS), QuotesUiState())

    private var researchJob: Job? = null

    init {
        loadResearch()
        restoreTicketIfAny()
    }

    override fun onIntent(intent: MarketIntent) {
        when (intent) {
            is MarketIntent.FilterChanged -> setState { copy(filter = intent.filter) }
            MarketIntent.RetryResearch -> loadResearch()
            is MarketIntent.OpenTicket -> openTicket(intent.instrumentId, intent.side)
            is MarketIntent.SubmitOrder -> submitOrder(intent)
            MarketIntent.DismissTicket -> closeTicket()
        }
    }

    private fun loadResearch() {
        researchJob?.cancel() // a retry should win over whatever request is already in flight
        researchJob = viewModelScope.launch {
            setState { copy(research = ResearchState.Loading) }
            val research = suspendRunCatching { researchRepository.getResearchCalls() }.fold(
                onSuccess = { ResearchState.Loaded(it.toImmutableList()) },
                onFailure = { ResearchState.Failed("Couldn't load research calls. Check your connection and retry.") },
            )
            setState { copy(research = research) }
        }
    }

    private fun openTicket(instrumentId: String, side: OrderSide) {
        val instrument = quoteFor(instrumentId)?.instrument ?: return
        saveField(KEY_TICKET_INSTRUMENT_ID, instrumentId)
        saveField(KEY_TICKET_SIDE, side.name)
        setState {
            copy(
                ticket = OrderTicketState(
                    instrumentId = instrument.id,
                    symbol = instrument.symbol,
                    instrumentName = instrument.name,
                    lotSize = instrument.lotSize,
                    tickSizeLabel = instrument.tickSize.format(grouping = false),
                    initialSide = side,
                ),
            )
        }
    }

    private fun closeTicket() {
        saveField<String>(KEY_TICKET_INSTRUMENT_ID, null)
        saveField<String>(KEY_TICKET_SIDE, null)
        setState { copy(ticket = null) }
    }

    /**
     * Runs once, at ViewModel construction. If the process was killed with a ticket open, the
     * instrument id + side survived in SavedStateHandle even though everything else didn't.
     * We wait (briefly) for the feed to produce a Quote for that instrument, then rebuild the
     * ticket exactly like a fresh OpenTicket intent would. If the instrument never shows up
     * (bad saved data, or the feed is unusually slow to start), we just give up quietly rather
     * than blocking the screen - the user can always open a new ticket themselves.
     */
    private fun restoreTicketIfAny() {
        val savedInstrumentId = restoreField<String>(KEY_TICKET_INSTRUMENT_ID) ?: return
        val savedSide = restoreField<String>(KEY_TICKET_SIDE)?.let { runCatching { OrderSide.valueOf(it) }.getOrNull() }
            ?: OrderSide.Buy
        viewModelScope.launch {
            val hasQuote = withTimeoutOrNull(RESTORE_TIMEOUT_MS) {
                market.first { snapshot -> snapshot.quotes.any { it.instrument.id == savedInstrumentId } }
            }
            if (hasQuote != null) openTicket(savedInstrumentId, savedSide)
        }
    }

    private fun submitOrder(intent: MarketIntent.SubmitOrder) {
        val ticket = currentState.ticket ?: return
        if (ticket.isSubmitting) return // guard against a double tap firing the order twice

        // Validate against whatever the freshest snapshot is right now, not whatever it was
        // when the ticket was first opened.
        val quote = quoteFor(ticket.instrumentId)
        val order = OrderRequest(
            instrumentId = ticket.instrumentId,
            side = intent.side,
            type = intent.type,
            quantity = intent.quantityText.toLongOrNull() ?: 0L,
            limitPrice = if (intent.type == OrderType.Limit) {
                FixedPoint.parse(intent.limitPriceText, quote?.instrument?.priceScale ?: 2)
            } else {
                null
            },
        )
        setState { copy(ticket = ticket.copy(isSubmitting = true, violations = persistentListOf())) }

        viewModelScope.launch {
            when (val result = placeOrder(order, quote)) {
                is PlaceOrderResult.Accepted -> {
                    closeTicket()
                    sendEffectNow(MarketEffect.OrderPlaced(result.ack.orderId))
                }
                is PlaceOrderResult.Rejected -> setState {
                    copy(ticket = this.ticket?.copy(isSubmitting = false, violations = result.violations.toImmutableList()))
                }
                is PlaceOrderResult.Failed -> {
                    setState { copy(ticket = this.ticket?.copy(isSubmitting = false)) }
                    sendEffectNow(MarketEffect.OrderFailed(result.reason))
                }
            }
        }
    }

    private fun quoteFor(instrumentId: String): Quote? =
        market.value.quotes.firstOrNull { it.instrument.id == instrumentId }

    private fun ImmutableList<Quote>.filteredBy(assetFilter: AssetFilter): ImmutableList<Quote> = when (assetFilter) {
        AssetFilter.All -> this // same list instance, no copying on the hot path
        AssetFilter.Equities -> filter { it.instrument.assetClass == AssetClass.Equity }.toImmutableList()
        AssetFilter.Bonds -> filter { it.instrument.assetClass == AssetClass.Bond }.toImmutableList()
    }

    private companion object {
        const val FEED_STOP_DELAY_MS = 5_000L
        const val RESTORE_TIMEOUT_MS = 3_000L
        const val KEY_TICKET_INSTRUMENT_ID = "ticket_instrument_id"
        const val KEY_TICKET_SIDE = "ticket_side"
    }
}
