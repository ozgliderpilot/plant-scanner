package com.nursery.core

/**
 * The cached plant list, indexed for scan lookup by accession number (which is also the barcode
 * value). A scan that misses becomes a "sell as unknown" line (decision #7) rather than being lost.
 */
class PlantBook(plants: List<Plant>) {

    private val byAccession: Map<String, Plant> = plants.associateBy { it.accession }

    val size: Int = plants.size

    /** Look up a scanned or typed accession number. */
    fun findByScan(code: String): Plant? = byAccession[code]

    /** Build a line for a found plant. */
    fun toLine(plant: Plant, qty: Int, unitPriceCents: Long, discountPct: Int, unit: SaleUnit = SaleUnit.POTS): LineItem =
        LineItem(
            accession = plant.accession,
            name = plant.name,
            qty = qty,
            unitPriceCents = unitPriceCents,
            discountPct = discountPct,
            unit = unit,
        )

    companion object {
        const val UNKNOWN_NAME = "unknown"

        /** Build a "sell as unknown" line: keep the scanned code as the accession, name = "unknown". */
        fun toUnknownLine(code: String, qty: Int, unitPriceCents: Long, discountPct: Int, unit: SaleUnit = SaleUnit.POTS): LineItem =
            LineItem(
                accession = code,
                name = UNKNOWN_NAME,
                qty = qty,
                unitPriceCents = unitPriceCents,
                discountPct = discountPct,
                unit = unit,
            )

        /** A line is "unknown" when its plant was not in the list at sale time. */
        fun isUnknown(line: LineItem): Boolean = line.name == UNKNOWN_NAME
    }
}
