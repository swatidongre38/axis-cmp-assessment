package com.axis.marketinsights.feature.market.components

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.axis.marketinsights.designsystem.LocalMarketColors
import com.axis.marketinsights.designsystem.tabularNumbers
import com.axis.marketinsights.domain.model.AssetClass
import com.axis.marketinsights.domain.model.FixedPoint
import com.axis.marketinsights.domain.model.Quote
import com.axis.marketinsights.domain.model.TickDirection

/**
 * One row in the watchlist. A few things keep this smooth at ~20 updates/sec:
 *  - skipped entirely when its Quote hasn't changed (stable data class + QuoteBook sharing)
 *  - the flash on a new tick only touches the draw phase, never triggers recomposition
 *  - a small custom layout with fixed columns, so a price change never reflows the row
 */
@Composable
fun QuoteRow(
    quote: Quote,
    onOpenTicket: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMarketColors.current
    val trendColor = when {
        quote.changeBps > 0 -> colors.up
        quote.changeBps < 0 -> colors.down
        else -> colors.neutral
    }

    val flash = remember { Animatable(Color.Transparent) }
    val firstSeenSequence = remember { quote.sequence } // don't flash just because a row scrolled into view
    LaunchedEffect(quote.sequence) {
        if (quote.sequence == firstSeenSequence) return@LaunchedEffect
        val target = when (quote.direction) {
            TickDirection.Up -> colors.upFlash
            TickDirection.Down -> colors.downFlash
            TickDirection.Unchanged -> return@LaunchedEffect
        }
        flash.snapTo(target) // a fresh tick cancels this effect and restarts the flash cleanly
        flash.animateTo(Color.Transparent, animationSpec = tween(durationMillis = 450))
    }

    QuoteRowLayout(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenTicket(quote.instrument.id) }
            .drawBehind { drawRect(flash.value) } // reading state inside draw => draw-only invalidation
            .padding(horizontal = 16.dp, vertical = 10.dp),
        symbol = {
            Column {
                Text(
                    quote.instrument.symbol,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = if (quote.instrument.assetClass == AssetClass.Bond) {
                    "YTM ${quote.yieldPct?.format() ?: "--"}%"
                } else {
                    quote.instrument.name
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        sparkline = { Sparkline(points = quote.history, color = trendColor) },
        price = {
            Text(
                quote.last.format(),
                style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        },
        change = { ChangeChip(changeBps = quote.changeBps, color = trendColor) },
    )
}

@Composable
private fun ChangeChip(changeBps: Long, color: Color) {
    val arrow = when {
        changeBps > 0 -> "▲"
        changeBps < 0 -> "▼"
        else -> "•"
    }
    Text(
        text = "$arrow ${FixedPoint(changeBps, 2).format(showPlus = true)}%",
        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/**
 * Fixed columns: [ symbol (flex) | sparkline 56dp | price 104dp | change 88dp ]. Each slot
 * gets measured once with fixed constraints, so changing text in one column never forces
 * the others to re-measure - Row/Column can't guarantee that on their own.
 */
@Composable
private fun QuoteRowLayout(
    symbol: @Composable () -> Unit,
    sparkline: @Composable () -> Unit,
    price: @Composable () -> Unit,
    change: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(symbol, sparkline, price, change), modifier = modifier) {
            (symbolMeasurables, sparkMeasurables, priceMeasurables, changeMeasurables), constraints ->
        val gap = 12.dp.roundToPx()
        val sparklineWidth = 56.dp.roundToPx()
        val sparklineHeight = 28.dp.roundToPx()
        val priceWidth = 104.dp.roundToPx()
        val changeWidth = 88.dp.roundToPx()
        val rowWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 360.dp.roundToPx()
        val symbolWidth = (rowWidth - sparklineWidth - priceWidth - changeWidth - gap * 3).coerceAtLeast(0)

        val symbolPlaceable = symbolMeasurables.single().measure(Constraints(maxWidth = symbolWidth))
        val sparklinePlaceable = sparkMeasurables.single().measure(Constraints.fixed(sparklineWidth, sparklineHeight))
        val pricePlaceable = priceMeasurables.single().measure(Constraints(minWidth = priceWidth, maxWidth = priceWidth))
        val changePlaceable = changeMeasurables.single().measure(Constraints(maxWidth = changeWidth))

        val rowHeight = maxOf(symbolPlaceable.height, sparklinePlaceable.height, pricePlaceable.height, changePlaceable.height)
            .coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(rowWidth, rowHeight) {
            symbolPlaceable.place(0, (rowHeight - symbolPlaceable.height) / 2)
            sparklinePlaceable.place(symbolWidth + gap, (rowHeight - sparklinePlaceable.height) / 2)
            pricePlaceable.place(symbolWidth + sparklineWidth + gap * 2, (rowHeight - pricePlaceable.height) / 2)
            changePlaceable.place(rowWidth - changePlaceable.width, (rowHeight - changePlaceable.height) / 2)
        }
    }
}
