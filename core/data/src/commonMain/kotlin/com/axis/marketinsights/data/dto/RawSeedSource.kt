package com.axis.marketinsights.data.dto

/**
 * Stand-in for what a real backend would return, in DTO shape, before anything is mapped
 * to a domain model. This is where a real HTTP client's response body would land -
 * MockResearchRepository and the instrument-master loader read from here instead of
 * building ResearchCall / Instrument objects directly, so the mapping step in
 * ResearchCallMapper / InstrumentMapper is actually exercised rather than skipped.
 */
object RawSeedSource {

    val instruments: List<InstrumentDto> = listOf(
        InstrumentDto("BD:GS2034", "GS2034", "7.10% GS 2034", "BOND", "0.0025", 4, 1, "1012.3500", "6.9425"),
        InstrumentDto("BD:GS2033", "GS2033", "7.18% GS 2033", "BOND", "0.0025", 4, 1, "1021.1000", "6.8550"),
        InstrumentDto("BD:GS2053", "GS2053", "7.30% GS 2053", "BOND", "0.0025", 4, 1, "1045.0250", "6.9500"),
        InstrumentDto("BD:MHSDL32", "MHSDL32", "7.45% MH SDL 2032", "BOND", "0.0025", 4, 1, "1018.0000", "7.1500"),
        InstrumentDto("BD:TB91D", "TB91D", "91-Day T-Bill", "BOND", "0.0025", 4, 1, "984.1500", "6.4200"),
        InstrumentDto("EQ:RELIANCE", "RELIANCE", "Reliance Industries", "EQUITY", "0.05", 2, 1, "1412.40", null),
        InstrumentDto("EQ:HDFCBANK", "HDFCBANK", "HDFC Bank", "EQUITY", "0.05", 2, 1, "1964.20", null),
        InstrumentDto("EQ:AXISBANK", "AXISBANK", "Axis Bank", "EQUITY", "0.05", 2, 1, "1165.55", null),
        InstrumentDto("EQ:INFY", "INFY", "Infosys", "EQUITY", "0.05", 2, 1, "1512.80", null),
        InstrumentDto("EQ:TCS", "TCS", "Tata Consultancy Services", "EQUITY", "0.05", 2, 1, "3398.10", null),
        InstrumentDto("EQ:ICICIBANK", "ICICIBANK", "ICICI Bank", "EQUITY", "0.05", 2, 1, "1428.35", null),
        InstrumentDto("EQ:SBIN", "SBIN", "State Bank of India", "EQUITY", "0.05", 2, 1, "812.65", null),
        InstrumentDto("EQ:ITC", "ITC", "ITC", "EQUITY", "0.05", 2, 1, "412.30", null),
        InstrumentDto("EQ:BHARTIARTL", "BHARTIARTL", "Bharti Airtel", "EQUITY", "0.05", 2, 1, "1879.90", null),
        InstrumentDto("EQ:LT", "LT", "Larsen & Toubro", "EQUITY", "0.05", 2, 1, "3612.00", null),
    )

    val researchCalls: List<ResearchCallDto> = listOf(
        ResearchCallDto(
            "RC-101", "EQ:AXISBANK", "AXISBANK", "Retail Research Desk", "BUY",
            "1320.00", "1100.00", "6-9 months",
            "Improving deposit mix and moderating credit costs support earnings visibility.",
            1_758_700_000_000L, 2,
        ),
        ResearchCallDto(
            "RC-102", "EQ:RELIANCE", "RELIANCE", "Retail Research Desk", "HOLD",
            "1480.00", null, "12 months",
            "Retail and telecom growth priced in near term; awaiting clarity on new-energy capex returns.",
            1_758_690_000_000L, 2,
        ),
        ResearchCallDto(
            "RC-103", "EQ:INFY", "INFY", "Technical Desk", "BUY",
            "1620.00", "1465.00", "2-4 weeks",
            "Breakout above consolidation range on rising volumes; momentum turning positive.",
            1_758_680_000_000L, 2,
        ),
        ResearchCallDto(
            "RC-104", "BD:GS2034", "GS2034", "Fixed Income Desk", "BUY",
            "103.0000", null, "3-6 months",
            "Expect softer yields as liquidity eases; duration favoured at the 10-year point.",
            1_758_670_000_000L, 4,
        ),
        ResearchCallDto(
            "RC-105", "EQ:ITC", "ITC", "Technical Desk", "SELL",
            "390.00", "425.00", "2-3 weeks",
            "Lower highs on the daily chart; relative strength weakening versus the benchmark.",
            1_758_660_000_000L, 2,
        ),
        ResearchCallDto(
            "RC-106", "EQ:TCS", "TCS", "Retail Research Desk", "HOLD",
            "3550.00", null, "12 months",
            "Deal wins remain steady but discretionary spending recovery is still gradual.",
            1_758_650_000_000L, 2,
        ),
    )
}
