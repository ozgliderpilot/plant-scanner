package com.nursery.core

import kotlin.math.abs

/**
 * All monetary math in integer cents to avoid floating-point drift.
 *
 * Line total = pots × unitPrice × (1 − discountPct/100), with the *net* rounded half-up to the
 * nearest cent (spec sale-flow formula). Everything is non-negative.
 */
object Money {

    /** @throws IllegalArgumentException on out-of-range inputs (defensive — UI validates first). */
    fun lineTotalCents(pots: Int, unitPriceCents: Long, discountPct: Int): Long {
        require(pots >= 0) { "pots must be >= 0, was $pots" }
        require(unitPriceCents >= 0) { "unitPriceCents must be >= 0, was $unitPriceCents" }
        require(discountPct in 0..100) { "discountPct must be 0..100, was $discountPct" }

        val gross = pots.toLong() * unitPriceCents              // exact, in cents
        val remainingNumerator = gross * (100 - discountPct)    // cents × percent-points
        // Round half-up when dividing by 100 (all values non-negative).
        return (remainingNumerator + 50) / 100
    }

    fun receiptTotalCents(lines: List<LineItem>): Long =
        lines.sumOf { lineTotalCents(it.pots, it.unitPriceCents, it.discountPct) }

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
