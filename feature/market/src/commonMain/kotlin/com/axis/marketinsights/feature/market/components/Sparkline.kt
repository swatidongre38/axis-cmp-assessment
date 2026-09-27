package com.axis.marketinsights.feature.market.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

/**
 * Tiny line chart drawn on a Canvas. The Path is rebuilt inside drawWithCache, so it only
 * happens when the points or the size actually change - drawing itself is one drawPath call.
 * Floats here are only ever pixel coordinates, never prices.
 */
@Composable
fun Sparkline(points: ImmutableList<Long>, color: Color, modifier: Modifier = Modifier) {
    Spacer(
        modifier.drawWithCache {
            val path = Path()
            if (points.size >= 2) {
                var lowest = Long.MAX_VALUE
                var highest = Long.MIN_VALUE
                for (point in points) {
                    if (point < lowest) lowest = point
                    if (point > highest) highest = point
                }
                val range = (highest - lowest).coerceAtLeast(1L).toFloat()
                val stepX = size.width / (points.size - 1)
                points.forEachIndexed { index, point ->
                    val x = index * stepX
                    val y = size.height - ((point - lowest) / range) * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
            }
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind { drawPath(path, color, style = stroke) }
        },
    )
}
