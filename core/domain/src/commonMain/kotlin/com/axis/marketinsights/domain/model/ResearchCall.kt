package com.axis.marketinsights.domain.model

enum class CallRating { Buy, Sell, Hold }

data class ResearchCall(
    val id: String,
    val instrumentId: String,
    val instrumentSymbol: String,
    val analyst: String,
    val rating: CallRating,
    val targetPrice: FixedPoint,
    val stopLoss: FixedPoint?,
    val horizon: String,
    val rationale: String,
    val publishedAtMillis: Long,
)
