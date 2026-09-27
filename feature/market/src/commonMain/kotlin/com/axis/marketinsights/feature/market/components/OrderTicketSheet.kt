@file:OptIn(ExperimentalMaterial3Api::class)

package com.axis.marketinsights.feature.market.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.axis.marketinsights.designsystem.LocalMarketColors
import com.axis.marketinsights.designsystem.tabularNumbers
import com.axis.marketinsights.domain.order.OrderSide
import com.axis.marketinsights.domain.order.OrderType
import com.axis.marketinsights.feature.market.MarketIntent
import com.axis.marketinsights.feature.market.OrderTicketState
import com.axis.marketinsights.feature.market.QuotesUiState

@Composable
fun OrderTicketSheet(
    ticket: OrderTicketState,
    quotes: () -> QuotesUiState,
    onIntent: (MarketIntent) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = LocalMarketColors.current

    // Text field state lives here, not in the ViewModel, and is keyed by instrument id so
    // switching to a different instrument resets the form, but rotating keeps what was typed.
    var side by rememberSaveable(ticket.instrumentId) { mutableStateOf(ticket.initialSide) }
    var type by rememberSaveable(ticket.instrumentId) { mutableStateOf(OrderType.Limit) }
    var quantity by rememberSaveable(ticket.instrumentId) { mutableStateOf("") }
    var limitPrice by rememberSaveable(ticket.instrumentId) { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = { onIntent(MarketIntent.DismissTicket) }, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${ticket.symbol} · ${ticket.instrumentName}", style = MaterialTheme.typography.titleLarge)
            TicketLivePrice(ticket.symbol, quotes)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrderSide.entries.forEach { s ->
                    FilterChip(selected = side == s, onClick = { side = s }, label = { Text(s.name) })
                }
                Spacer(Modifier.width(16.dp))
                OrderType.entries.forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name) })
                }
            }

            OutlinedTextField(
                value = quantity,
                onValueChange = { input -> quantity = input.filter { it.isDigit() }.take(9) },
                label = { Text("Quantity (lot ${ticket.lotSize})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (type == OrderType.Limit) {
                OutlinedTextField(
                    value = limitPrice,
                    onValueChange = { input -> limitPrice = input.filter { it.isDigit() || it == '.' }.take(14) },
                    label = { Text("Limit price (tick ${ticket.tickSizeLabel})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ticket.violations.forEach { violation ->
                Text(
                    "• ${violation.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val actionColor: Color = if (side == OrderSide.Buy) colors.up else colors.down
            Button(
                onClick = { onIntent(MarketIntent.SubmitOrder(side, type, quantity, limitPrice)) },
                enabled = !ticket.isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (ticket.isSubmitting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("${side.name} ${ticket.symbol}")
                }
            }
        }
    }
}

@Composable
private fun TicketLivePrice(symbol: String, quotes: () -> QuotesUiState) {
    val quote = quotes().bySymbol[symbol]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("LTP ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(quote?.last?.format() ?: "--", style = MaterialTheme.typography.titleMedium.tabularNumbers())
    }
}
