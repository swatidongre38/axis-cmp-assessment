package com.axis.marketinsights.feature.market.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.axis.marketinsights.designsystem.LocalMarketColors
import com.axis.marketinsights.designsystem.tabularNumbers
import com.axis.marketinsights.domain.model.CallRating
import com.axis.marketinsights.domain.model.FixedPoint
import com.axis.marketinsights.domain.model.Quote
import com.axis.marketinsights.domain.model.ResearchCall
import com.axis.marketinsights.domain.order.OrderSide

/**
 * Mostly static content plus one live element (the current price / distance to target).
 * That live bit reads the fast lane through [liveQuote] inside its own small composable, so
 * a tick never recomposes the whole card.
 */
@Composable
fun ResearchCallCard(
    call: ResearchCall,
    liveQuote: () -> Quote?,
    onTrade: (String, OrderSide) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMarketColors.current
    val ratingColor = when (call.rating) {
        CallRating.Buy -> colors.up
        CallRating.Sell -> colors.down
        CallRating.Hold -> colors.neutral
    }
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = ratingColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        call.rating.name.uppercase(),
                        color = ratingColor,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(call.instrumentSymbol, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(call.horizon, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(call.rationale, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LabeledValue("Target", call.targetPrice.format())
                call.stopLoss?.let { LabeledValue("Stop loss", it.format()) }
                LiveDistanceToTarget(call.targetPrice, liveQuote)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(call.analyst, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (call.rating != CallRating.Hold) {
                    val side = if (call.rating == CallRating.Buy) OrderSide.Buy else OrderSide.Sell
                    FilledTonalButton(onClick = { onTrade(call.instrumentId, side) }) {
                        Text(if (side == OrderSide.Buy) "Buy" else "Sell")
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveDistanceToTarget(target: FixedPoint, liveQuote: () -> Quote?) {
    val quote = liveQuote() ?: return
    val distance = FixedPoint(target.basisPointsFrom(quote.last), 2)
    LabeledValue("LTP · to target", "${quote.last.format()} · ${distance.format(showPlus = true)}%")
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.tabularNumbers())
    }
}
