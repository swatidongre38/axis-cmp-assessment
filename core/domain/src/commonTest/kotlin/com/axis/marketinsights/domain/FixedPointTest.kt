package com.axis.marketinsights.domain

import com.axis.marketinsights.domain.model.FixedPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FixedPointTest {

    @Test
    fun formatsWithIndianGrouping() {
        assertEquals("12,34,567.89", FixedPoint(123_456_789L, 2).format())
        assertEquals("1,412.40", FixedPoint(141_240L, 2).format())
        assertEquals("-0.05", FixedPoint(-5L, 2).format())
        assertEquals("+1.25", FixedPoint(125L, 2).format(showPlus = true))
        assertEquals("101.2350", FixedPoint(1_012_350L, 4).format())
    }

    @Test
    fun parseDoesNotRoundOrTruncateSilently() {
        assertEquals(FixedPoint(141_235L, 2), FixedPoint.parse("1,412.35", scale = 2))
        assertEquals(FixedPoint(141_200L, 2), FixedPoint.parse("1412", scale = 2))
        assertEquals(FixedPoint(50L, 2), FixedPoint.parse(".5", scale = 2))
        assertNull(FixedPoint.parse("1412.355", scale = 2)) // too many decimals for this scale
        assertNull(FixedPoint.parse("12a", scale = 2))
        assertNull(FixedPoint.parse("", scale = 2))
        assertNull(FixedPoint.parse("1.2.3", scale = 2))
    }

    @Test
    fun comparesCorrectlyAcrossDifferentScales() {
        assertTrue(FixedPoint(100L, 2).compareTo(FixedPoint(1_000L, 3)) == 0)
        assertTrue(FixedPoint(101L, 2) > FixedPoint(1_000L, 3))
    }

    @Test
    fun noDriftAfterManySmallAdditions() {
        var total = FixedPoint.zero(2)
        repeat(1_000_000) { total += FixedPoint(10L, 2) } // 0.10, a million times
        assertEquals(FixedPoint(10_000_000L, 2), total) // exactly 1,00,000.00
    }

    @Test
    fun tickGridAndBasisPointMath() {
        val tick = FixedPoint(5L, 2)
        assertTrue(FixedPoint(141_235L, 2).isMultipleOf(tick))
        assertTrue(!FixedPoint(141_233L, 2).isMultipleOf(tick))
        assertEquals(100L, FixedPoint(10_100L, 2).basisPointsFrom(FixedPoint(10_000L, 2)))
    }
}
