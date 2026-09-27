package com.axis.marketinsights.data.mapper

import com.axis.marketinsights.data.dto.ResearchCallDto
import com.axis.marketinsights.domain.model.CallRating
import com.axis.marketinsights.domain.model.FixedPoint
import com.axis.marketinsights.domain.model.ResearchCall

/**
 * Converts wire-shaped DTOs into domain models. Nothing outside core:data ever sees a
 * ResearchCallDto - MockResearchRepository maps before returning, so the rest of the app
 * only ever deals with ResearchCall.
 *
 * Returns null instead of throwing on a bad record, so one malformed row from the backend
 * doesn't take down the whole research feed - the repository logs and drops it.
 */
fun ResearchCallDto.toDomain(): ResearchCall? {
    val rating = when (rating.uppercase()) {
        "BUY" -> CallRating.Buy
        "SELL" -> CallRating.Sell
        "HOLD" -> CallRating.Hold
        else -> return null
    }
    val target = FixedPoint.parse(targetPrice, priceScale) ?: return null
    val stop = stopLoss?.let { FixedPoint.parse(it, priceScale) }

    return ResearchCall(
        id = id,
        instrumentId = instrumentId,
        instrumentSymbol = instrumentSymbol,
        analyst = analyst,
        rating = rating,
        targetPrice = target,
        stopLoss = stop,
        horizon = horizon,
        rationale = rationale,
        publishedAtMillis = publishedAtMillis,
    )
}
