package com.axis.marketinsights.data.mapper

import com.axis.marketinsights.data.dto.InstrumentDto
import com.axis.marketinsights.data.seed.SeededInstrument
import com.axis.marketinsights.domain.model.AssetClass
import com.axis.marketinsights.domain.model.FixedPoint
import com.axis.marketinsights.domain.model.Instrument

fun InstrumentDto.toDomain(): SeededInstrument? {
    val assetClass = when (assetClass.uppercase()) {
        "EQUITY" -> AssetClass.Equity
        "BOND" -> AssetClass.Bond
        else -> return null
    }
    val tick = FixedPoint.parse(tickSize, tickScale) ?: return null
    val previousClose = FixedPoint.parse(this.previousClose, tickScale) ?: return null
    val yield = initialYieldPct?.let { FixedPoint.parse(it, 4) }

    return SeededInstrument(
        instrument = Instrument(id, symbol, name, assetClass, tick, lotSize),
        previousClose = previousClose,
        initialYield = yield,
    )
}
