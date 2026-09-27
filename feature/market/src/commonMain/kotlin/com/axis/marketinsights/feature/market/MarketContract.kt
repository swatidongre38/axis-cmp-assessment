package com.axis.marketinsights.feature.market

import com.axis.marketinsights.domain.model.Quote
import com.axis.marketinsights.domain.model.ResearchCall
import com.axis.marketinsights.domain.order.OrderSide
import com.axis.marketinsights.domain.order.OrderType
import com.axis.marketinsights.domain.order.Violation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/*
 * MVI-ish contract for this screen. State is split into two pieces on purpose:
 *  - MarketUiState  changes on user action / research loading - drives the screen's structure.
 *  - QuotesUiState  changes ~20x/sec - only read by the leaf composables that actually show prices.
 */

enum class AssetFilter(val label: String) { All("All"), Equities("Equities"), Bonds("Bonds") }

data class MarketUiState(
    val filter: AssetFilter = AssetFilter.All,
    val research: ResearchState = ResearchState.Loading,
    val ticket: OrderTicketState? = null,
)

sealed interface ResearchState {
    data object Loading : ResearchState
    data class Loaded(val calls: ImmutableList<ResearchCall>) : ResearchState
    data class Failed(val message: String) : ResearchState
}

/**
 * Only what the ViewModel actually owns. The quantity/price text the user is typing lives in
 * the UI (rememberSaveable) instead - routing every keystroke through an async StateFlow
 * causes cursor jumps and feels laggy.
 */
data class OrderTicketState(
    val instrumentId: String,
    val symbol: String,
    val instrumentName: String,
    val lotSize: Int,
    val tickSizeLabel: String,
    val initialSide: OrderSide,
    val violations: ImmutableList<Violation> = persistentListOf(),
    val isSubmitting: Boolean = false,
)

data class QuotesUiState(
    val visible: ImmutableList<Quote> = persistentListOf(),
    val bySymbol: ImmutableMap<String, Quote> = persistentMapOf(),
    val ticksApplied: Long = 0L,
)

sealed interface MarketIntent {
    data class FilterChanged(val filter: AssetFilter) : MarketIntent
    data object RetryResearch : MarketIntent
    data class OpenTicket(val instrumentId: String, val side: OrderSide = OrderSide.Buy) : MarketIntent
    data class SubmitOrder(
        val side: OrderSide,
        val type: OrderType,
        val quantityText: String,
        val limitPriceText: String,
    ) : MarketIntent
    data object DismissTicket : MarketIntent
}

/** One-off events - delivered once through a channel, never replayed after rotation. */
sealed interface MarketEffect {
    data class OrderPlaced(val orderId: String) : MarketEffect
    data class OrderFailed(val reason: String) : MarketEffect
}
