package com.axis.marketinsights.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

private val AxisBurgundy = Color(0xFF97144D)

/** Colours for up/down/flat, plus a lighter "flash" variant for the tick animation. Never
 * the only signal on its own - always paired with a ▲/▼ glyph. */
@Immutable
data class MarketColors(
    val up: Color,
    val down: Color,
    val neutral: Color,
    val upFlash: Color,
    val downFlash: Color,
)

private val LightMarketColors = MarketColors(
    up = Color(0xFF1B8A5A),
    down = Color(0xFFC62839),
    neutral = Color(0xFF6B7280),
    upFlash = Color(0x331B8A5A),
    downFlash = Color(0x33C62839),
)

private val DarkMarketColors = MarketColors(
    up = Color(0xFF3DDC97),
    down = Color(0xFFFF6B81),
    neutral = Color(0xFF9CA3AF),
    upFlash = Color(0x333DDC97),
    downFlash = Color(0x33FF6B81),
)

// staticCompositionLocalOf since the theme almost never changes - no need to pay for
// per-read tracking on something this stable.
val LocalMarketColors = staticCompositionLocalOf { LightMarketColors }

@Composable
fun AxisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) {
        darkColorScheme(primary = Color(0xFFFF8AB8), secondary = Color(0xFFE7B6C9))
    } else {
        lightColorScheme(primary = AxisBurgundy, secondary = Color(0xFF6D2E46))
    }
    CompositionLocalProvider(LocalMarketColors provides if (darkTheme) DarkMarketColors else LightMarketColors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * Fixed-width digits, so "1,111.10" and "8,888.80" take up exactly the same space. Without
 * this, a changing price shifts everything next to it every time it updates.
 */
fun TextStyle.tabularNumbers(): TextStyle = merge(TextStyle(fontFeatureSettings = "tnum"))
