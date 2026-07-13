package com.nursery.core

/** Three Ready-for-sale ticks on a Repot (Tubes / Pots / Misc. — never Stock plant). */
data class ReadyForSaleFlags(
    val tubes: Boolean,
    val pots: Boolean,
    val misc: Boolean,
)

/**
 * Default Ready-for-sale ticks when opening the Repot count editor.
 *
 * Matching is case-insensitive exact: Plant Type ([group]) for Camellia / Rhododendron / Herb;
 * genus for Hosta. Exception plants with pots > 0 use the sheet's [sheetPotsForSale] instead of
 * defaulting pots to checked.
 */
object RepotReadyForSale {

    fun defaults(
        tubes: Int,
        pots: Int,
        misc: Int,
        group: String?,
        genus: String,
        sheetPotsForSale: Boolean,
    ): ReadyForSaleFlags = ReadyForSaleFlags(
        tubes = tubes > 0 && matchesExact(group, "Herb"),
        pots = when {
            pots <= 0 -> false
            isPotsException(group, genus) -> sheetPotsForSale
            else -> true
        },
        misc = misc > 0,
    )

    private fun isPotsException(group: String?, genus: String): Boolean =
        matchesExact(group, "Camellia") ||
            matchesExact(group, "Rhododendron") ||
            matchesExact(genus, "Hosta")

    private fun matchesExact(value: String?, expected: String): Boolean =
        value?.equals(expected, ignoreCase = true) == true
}
