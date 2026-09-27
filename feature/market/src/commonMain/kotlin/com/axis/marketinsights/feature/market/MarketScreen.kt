@file:OptIn(ExperimentalMaterial3Api::class)

package com.axis.marketinsights.feature.market

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.axis.marketinsights.designsystem.tabularNumbers
import com.axis.marketinsights.domain.order.OrderSide
import com.axis.marketinsights.feature.market.components.OrderTicketSheet
import com.axis.marketinsights.feature.market.components.QuoteRow
import com.axis.marketinsights.feature.market.components.ResearchCallCard

/** Owns collection from the ViewModel; everything else is a plain stateless composable. */
@Composable
fun MarketRoute(viewModel: MarketViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Collected here, but not READ here. Only the leaf composables below call quotes(), so
    // a 20Hz update never touches the scaffold, tabs or filter chips.
    val quotesState = viewModel.quotes.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is MarketEffect.OrderPlaced -> "Order ${effect.orderId} placed"
                is MarketEffect.OrderFailed -> effect.reason
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    MarketScreen(
        state = state,
        quotes = { quotesState.value },
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
fun MarketScreen(
    state: MarketUiState,
    quotes: () -> QuotesUiState,
    onIntent: (MarketIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Hoisted above the tab switch so each list keeps its own scroll position when you
    // switch tabs and come back.
    val quotesListState = rememberLazyListState()
    val researchListState = rememberLazyListState()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Market Insights") },
                actions = { FeedStats(quotes, Modifier.padding(end = 12.dp)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            if (maxWidth >= 840.dp) {
                // Tablet / foldable / iPad width - show both panes side by side.
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1.6f)) {
                        FilterRow(state.filter, onIntent)
                        QuotesList(quotes, quotesListState, onIntent, Modifier.weight(1f))
                    }
                    VerticalDivider()
                    ResearchPane(state.research, quotes, researchListState, onIntent, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Live market") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Research") })
                    }
                    if (selectedTab == 0) {
                        FilterRow(state.filter, onIntent)
                        QuotesList(quotes, quotesListState, onIntent, Modifier.weight(1f))
                    } else {
                        ResearchPane(state.research, quotes, researchListState, onIntent, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    state.ticket?.let { ticket -> OrderTicketSheet(ticket, quotes, onIntent) }
}

@Composable
private fun FeedStats(quotes: () -> QuotesUiState, modifier: Modifier = Modifier) {
    // Reads the fast lane in its own tiny scope, so only this text redraws on a tick.
    Text(
        text = "${quotes().ticksApplied} ticks",
        style = MaterialTheme.typography.labelSmall.tabularNumbers(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun FilterRow(selected: AssetFilter, onIntent: (MarketIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssetFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onIntent(MarketIntent.FilterChanged(filter)) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun QuotesList(
    quotes: () -> QuotesUiState,
    listState: LazyListState,
    onIntent: (MarketIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openTicket: (String) -> Unit = remember(onIntent) { { id -> onIntent(MarketIntent.OpenTicket(id)) } }
    LazyColumn(modifier = modifier.fillMaxWidth(), state = listState) {
        val visibleQuotes = quotes().visible // deferred read - only invalidates the item list, not the parent
        items(
            items = visibleQuotes,
            key = { it.instrument.id },                  // stable id so rows move instead of rebuilding
            contentType = { it.instrument.assetClass },   // bonds and equities recycle separately
        ) { quote ->
            QuoteRow(quote = quote, onOpenTicket = openTicket)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun ResearchPane(
    research: ResearchState,
    quotes: () -> QuotesUiState,
    listState: LazyListState,
    onIntent: (MarketIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (research) {
        ResearchState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ResearchState.Failed -> Column(
            modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(research.message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onIntent(MarketIntent.RetryResearch) }) { Text("Retry") }
        }
        is ResearchState.Loaded -> {
            val onTrade: (String, OrderSide) -> Unit =
                remember(onIntent) { { id, side -> onIntent(MarketIntent.OpenTicket(id, side)) } }
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(research.calls, key = { it.id }) { call ->
                    ResearchCallCard(
                        call = call,
                        liveQuote = { quotes().bySymbol[call.instrumentSymbol] },
                        onTrade = onTrade,
                    )
                }
            }
        }
    }
}
