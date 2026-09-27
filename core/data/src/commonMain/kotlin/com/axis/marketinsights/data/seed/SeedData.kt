package com.axis.marketinsights.data.seed

import com.axis.marketinsights.data.dto.RawSeedSource
import com.axis.marketinsights.data.mapper.toDomain
import com.axis.marketinsights.domain.model.AssetClass
import com.axis.marketinsights.domain.model.FixedPoint
import com.axis.marketinsights.domain.model.Instrument

data class SeededInstrument(
    val instrument: Instrument,
    val previousClose: FixedPoint,
    val initialYield: FixedPoint? = null,
)

/**
 * Mock data for the demo, but built the way real data would be: raw DTOs (RawSeedSource) get
 * mapped to domain models here, the same pipeline a real instrument-master API response
 * would go through. Only the extra synthetic equities below are generated directly, since
 * they exist purely to stress-test the list with more rows and were never meant to look
 * like real API data.
 */
object SeedData {

    private val coreInstruments: List<SeededInstrument> =
        RawSeedSource.instruments.mapNotNull { it.toDomain() }

    private fun syntheticEquity(symbol: String, name: String, close: Long) = SeededInstrument(
        Instrument("EQ:$symbol", symbol, name, AssetClass.Equity, FixedPoint(5L, 2), lotSize = 1),
        previousClose = FixedPoint(close, 2),
    )

    /** [syntheticCount] extra generated equities, mainly to stress-test the list with more rows. */
    fun instruments(syntheticCount: Int = 35): List<SeededInstrument> =
        coreInstruments + (1..syntheticCount).map { i ->
            val n = i.toString().padStart(2, '0')
            syntheticEquity("MOCK$n", "Synthetic Equity $n", 10_000L + i * 3_755L)
        }
}
