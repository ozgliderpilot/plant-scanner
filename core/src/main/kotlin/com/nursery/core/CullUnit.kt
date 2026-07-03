package com.nursery.core

/**
 * Pot-type default for a freshly scanned cull. Priority is **Tube → Pot → Misc** (the opposite of
 * [SaleUnit.defaultFor] used at sale). Returns a [SaleUnit] because the same labels are written to
 * the export sheet's unit column.
 */
object CullUnit {

    fun defaultFor(tubes: Int, pots: Int, misc: Int): SaleUnit = when {
        tubes > 0 -> SaleUnit.TUBES
        pots > 0 -> SaleUnit.POTS
        misc > 0 -> SaleUnit.MISC
        else -> SaleUnit.TUBES
    }
}
