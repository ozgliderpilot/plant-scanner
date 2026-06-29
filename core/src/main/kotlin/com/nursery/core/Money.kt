package com.nursery.core

import kotlin.math.abs

/**
 * All monetary math in integer cents to avoid floating-point drift.
 *
 * Line total = qty × unitPrice × (1 − discountPct/100), with the *net* rounded half-up to the
 * nearest cent (spec sale-flow formula). Everything is non-negative.
 */
object Money {

    /** @throws IllegalArgumentException on out-of-range inputs (defensive — UI validates first). */
    fun lineTotalCents(qty: Int, unitPriceCents: Long, discountPct: Int): Long {
        require(qty >= 0) { "qty must be >= 0, was $qty" }
        require(unitPriceCents >= 0) { "unitPriceCents must be >= 0, was $unitPriceCents" }
        require(discountPct in 0..100) { "discountPct must be 0..100, was $discountPct" }

        // multiplyExact throws ArithmeticException on overflow rather than silently wrapping to a
        // negative/garbage total (the UI validates magnitudes first, so this is purely defensive).
        val gross = Math.multiplyExact(qty.toLong(), unitPriceCents)        // exact, in cents
        val remainingNumerator = Math.multiplyExact(gross, (100 - discountPct).toLong()) // cents × percent-points
        // Round half-up when dividing by 100 (all values non-negative).
        return (remainingNumerator + 50) / 100
    }

    /** True when the line total is $0, by any path (forgotten price or 100% discount). */
    fun isFreeLine(qty: Int, unitPriceCents: Long, discountPct: Int): Boolean =
        lineTotalCents(qty, unitPriceCents, discountPct) == 0L

    fun receiptTotalCents(lines: List<LineItem>): Long =
        lines.sumOf { lineTotalCents(it.qty, it.unitPriceCents, it.discountPct) }

    /** "$12.30" — for display. */
    fun formatAud(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val a = abs(cents)
        return "$sign\$${a / 100}.${(a % 100).toString().padStart(2, '0')}"
    }

    /** "12.30" — plain decimal for writing into a spreadsheet cell (no currency symbol). */
    fun formatPlain(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val a = abs(cents)
        return "$sign${a / 100}.${(a % 100).toString().padStart(2, '0')}"
    }
}
