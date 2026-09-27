package com.axis.marketinsights.data.dto

/**
 * Shape of one research call as it would come back from a real research-calls endpoint -
 * everything as primitives (strings, longs), the way JSON actually arrives over the wire.
 * Nothing here knows about FixedPoint, CallRating, or any other domain type - that
 * translation happens in ResearchCallMapper, not here.
 */
data class ResearchCallDto(
    val id: String,
    val instrumentId: String,
    val instrumentSymbol: String,
    val analyst: String,
    /** Raw string from the API - "BUY" / "SELL" / "HOLD". Mapped to CallRating. */
    val rating: String,
    /** Price as a decimal string, e.g. "1320.00" - never a Double over the wire. */
    val targetPrice: String,
    val stopLoss: String?,
    val horizon: String,
    val rationale: String,
    val publishedAtMillis: Long,
    /** How many decimal places targetPrice/stopLoss carry for this instrument. */
    val priceScale: Int,
)
