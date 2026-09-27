package com.axis.marketinsights.data.dto

/**
 * Shape of one instrument as it would come from an instrument-master / reference-data
 * endpoint. Asset class and tick size arrive as raw strings/decimals here, same reasoning
 * as ResearchCallDto - the wire format shouldn't leak domain types.
 */
data class InstrumentDto(
    val id: String,
    val symbol: String,
    val name: String,
    /** "EQUITY" / "BOND" from the API. Mapped to AssetClass. */
    val assetClass: String,
    val tickSize: String,
    val tickScale: Int,
    val lotSize: Int,
    val previousClose: String,
    /** Bonds only - null for equities. */
    val initialYieldPct: String?,
)
