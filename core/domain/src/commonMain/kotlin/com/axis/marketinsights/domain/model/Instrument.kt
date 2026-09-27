package com.axis.marketinsights.domain.model

enum class AssetClass { Equity, Bond }

data class Instrument(
    val id: String,
    val symbol: String,
    val name: String,
    val assetClass: AssetClass,
    /** Smallest price move allowed. Its scale is also the price scale for this instrument. */
    val tickSize: FixedPoint,
    val lotSize: Int,
) {
    init {
        require(lotSize > 0) { "lotSize must be positive" }
        require(tickSize.unscaled > 0) { "tickSize must be positive" }
    }

    val priceScale: Int get() = tickSize.scale
}
