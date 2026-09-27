package com.axis.marketinsights.domain.order

import com.axis.marketinsights.domain.model.FixedPoint

enum class OrderSide { Buy, Sell }

enum class OrderType { Market, Limit }

data class OrderRequest(
    val instrumentId: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: Long,
    /** Only used for Limit orders. */
    val limitPrice: FixedPoint?,
)

data class OrderAck(val orderId: String, val acceptedAtMillis: Long)

/**
 * Client-side pre-trade limits. The server has the final say, but checking these locally
 * first means the user doesn't have to wait on a round trip just to find out their price
 * is way off.
 */
data class RiskLimits(
    val maxOrderValue: FixedPoint,
    /** How far a limit price can sit from the previous close, in bps (2000 = 20%). */
    val priceBandBps: Long,
    val maxQuoteAgeMillis: Long,
    val maxQuantity: Long,
) {
    init {
        require(maxQuantity > 0) { "maxQuantity must be positive" }
    }

    companion object {
        val standard = RiskLimits(
            maxOrderValue = FixedPoint(10_000_000_00L, 2), // ₹1 crore
            priceBandBps = 2_000L,
            maxQuoteAgeMillis = 2_000L,
            maxQuantity = 10_000_000L,
        )
    }
}
