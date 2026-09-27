package com.axis.marketinsights.domain.model

import kotlin.math.absoluteValue

/**
 * Money as an exact integer instead of a Double. value = unscaled / 10^scale.
 *
 * I didn't want Double anywhere near prices — 0.1 + 0.2 isn't exactly 0.3 in binary
 * floating point, and that kind of drift adds up fast across a lot of ticks, or ends up
 * off the exchange's tick grid by a hair. A Long gives plenty of digits for anything we
 * deal with here.
 *
 * Two FixedPoints at different scales aren't equal by `==` even if they represent the
 * same value (1.0 at scale 1 vs 1.00 at scale 2) - use compareTo for that.
 */
data class FixedPoint(val unscaled: Long, val scale: Int) : Comparable<FixedPoint> {

    init {
        require(scale in 0..MAX_SCALE) { "scale must be in 0..$MAX_SCALE but was $scale" }
    }

    fun rescaled(newScale: Int): FixedPoint {
        require(newScale >= scale) { "would lose precision going to a smaller scale" }
        return if (newScale == scale) this else FixedPoint(unscaled * pow10(newScale - scale), newScale)
    }

    operator fun plus(other: FixedPoint): FixedPoint = aligned(other) { a, b, s -> FixedPoint(a + b, s) }

    operator fun minus(other: FixedPoint): FixedPoint = aligned(other) { a, b, s -> FixedPoint(a - b, s) }

    operator fun times(quantity: Long): FixedPoint {
        val product = unscaled * quantity
        check(quantity == 0L || product / quantity == unscaled) { "overflow multiplying price by quantity" }
        return FixedPoint(product, scale)
    }

    override fun compareTo(other: FixedPoint): Int = aligned(other) { a, b, _ -> a.compareTo(b) }

    /** True if this sits exactly on the grid defined by [step] (e.g. the exchange tick size). */
    fun isMultipleOf(step: FixedPoint): Boolean = aligned(step) { a, b, _ -> b != 0L && a % b == 0L }

    /** Change from [base], in basis points (1 bp = 0.01%), truncated toward zero. */
    fun basisPointsFrom(base: FixedPoint): Long =
        aligned(base) { a, b, _ -> if (b == 0L) 0L else (a - b) * 10_000L / b }

    /** Indian-style grouping (12,34,567.89) since that's what our users expect. */
    fun format(grouping: Boolean = true, showPlus: Boolean = false): String {
        val absValue = unscaled.absoluteValue
        val divisor = pow10(scale)
        val whole = (absValue / divisor).toString()
        val groupedWhole = if (grouping) groupIndian(whole) else whole
        val sign = when {
            unscaled < 0 -> "-"
            showPlus && unscaled > 0 -> "+"
            else -> ""
        }
        if (scale == 0) return sign + groupedWhole
        val fraction = (absValue % divisor).toString().padStart(scale, '0')
        return "$sign$groupedWhole.$fraction"
    }

    override fun toString(): String = format(grouping = false)

    private inline fun <R> aligned(other: FixedPoint, op: (Long, Long, Int) -> R): R {
        val commonScale = maxOf(scale, other.scale)
        return op(rescaled(commonScale).unscaled, other.rescaled(commonScale).unscaled, commonScale)
    }

    companion object {
        const val MAX_SCALE = 8
        private const val MAX_WHOLE_DIGITS = 12

        fun zero(scale: Int): FixedPoint = FixedPoint(0L, scale)

        /**
         * Parses text like "1,412.35" from a text field. Returns null instead of rounding
         * when there are more decimals than [scale] allows - I'd rather reject bad input
         * than quietly change a price the user typed.
         */
        fun parse(text: String, scale: Int): FixedPoint? {
            val cleaned = text.trim().replace(",", "")
            val isNegative = cleaned.startsWith("-")
            val body = cleaned.removePrefix("-").removePrefix("+")
            if (body.none { it.isDigit() }) return null

            val parts = body.split('.')
            if (parts.size > 2) return null

            val wholePart = parts[0].ifEmpty { "0" }
            val fractionPart = parts.getOrElse(1) { "" }
            if (!wholePart.all { it.isDigit() } || !fractionPart.all { it.isDigit() }) return null
            if (fractionPart.length > scale || wholePart.length > MAX_WHOLE_DIGITS) return null

            val whole = wholePart.toLongOrNull() ?: return null
            val fraction = fractionPart.padEnd(scale, '0').ifEmpty { "0" }.toLongOrNull() ?: return null
            val unscaled = whole * pow10(scale) + fraction
            return FixedPoint(if (isNegative) -unscaled else unscaled, scale)
        }

        private fun groupIndian(digits: String): String {
            if (digits.length <= 3) return digits
            val lastThree = digits.takeLast(3)
            val rest = digits.dropLast(3)
                .reversed()
                .chunked(2)
                .map { it.reversed() }
                .reversed()
                .joinToString(",")
            return "$rest,$lastThree"
        }
    }
}

internal fun pow10(exponent: Int): Long {
    var result = 1L
    repeat(exponent) { result *= 10L }
    return result
}
