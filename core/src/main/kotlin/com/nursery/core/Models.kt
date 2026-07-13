package com.nursery.core

/** Lifecycle of a receipt as it moves from the cart to the cloud. */
enum class ReceiptStatus {
    /** Still being built in the Sell flow; not yet saved. */
    OPEN,

    /** Saved locally, pending export to Google Sheets. */
    SAVED,

    /** Confirmed appended to Google Sheets; never re-sent. */
    EXPORTED,
}

/**
 * A plant from the cached plant list (pulled wholesale from Google Sheets).
 *
 * The label's Code 128 barcode encodes the **accession number** itself — barcode and accession are
 * the same value, so there is no separate barcode field. The sheet also carries no price column
 * (spec decision #6), so there is no price here.
 */
data class Plant(
    val accession: String,
    val name: String,
    val genus: String = "",
    val species: String = "",
    val cultivar: String = "",
    val commonName: String = "",
    val group: String?,
    val light: String?,
    val potsInNursery: Int = 0,
    val tubesInNursery: Int = 0,
    val miscInNursery: Int = 0,
    val stockInNursery: Int = 0,
    val potsForSale: Boolean = false,
    val tubesForSale: Boolean = false,
    val miscForSale: Boolean = false,
)

/**
 * One line on a receipt. [accession] is the scanned/typed value (== the barcode). When it matched a
 * plant, [name] is that plant's name; for a not-found "sell as unknown" line (decision #7) the same
 * scanned [accession] is kept and [name] is "unknown" so it can be reconciled later. [qty] is the
 * count of [unit]s (Pots/Tubes/Misc).
 * Unit price is always keyed at sale (decision #6); discount is a percentage 0..100 (decision #5).
 * [itemSeq] is the 1-based position of this line within its receipt, stamped at save; together with
 * the receipt number it is the primary key of a Sales-sheet row. 0 means not-yet-stamped (cart stage).
 */
data class LineItem(
    val accession: String,
    val name: String,
    val genus: String = "",
    val species: String = "",
    val cultivar: String = "",
    val commonName: String = "",
    val group: String = "",
    val qty: Int,
    val unitPriceCents: Long,
    val discountPct: Int,
    val unit: SaleUnit = SaleUnit.POTS,
    val itemSeq: Int = 0,
)

/** A receipt = one customer (decision #4); number is `PP-NNN` per device (decision #11). */
data class Receipt(
    val localId: Long,
    val receiptNo: String,
    val createdAtEpochMs: Long,
    val status: ReceiptStatus,
    val lines: List<LineItem>,
    val paymentMethod: PaymentMethod = PaymentMethod.CARD,
)
