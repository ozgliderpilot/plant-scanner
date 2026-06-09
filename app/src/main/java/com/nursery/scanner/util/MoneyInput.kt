package com.nursery.scanner.util

import java.math.BigDecimal
import java.math.RoundingMode

/** Parse a user-typed dollar amount ("12", "12.5", "12.50") into integer cents; null if invalid. */
fun parseDollarsToCents(text: String): Long? {
    val t = text.trim().removePrefix("$").trim()
    if (t.isEmpty()) return null
    return try {
        BigDecimal(t).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
            .takeIf { it >= 0 }
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}

/** Cents -> an editable plain string like "12.50" (empty for 0 so the field starts blank). */
fun centsToEditable(cents: Long): String =
    if (cents <= 0) "" else BigDecimal(cents).movePointLeft(2).toPlainString()
