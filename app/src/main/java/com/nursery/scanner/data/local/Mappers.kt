package com.nursery.scanner.data.local

import com.nursery.core.LineItem
import com.nursery.core.Plant
import com.nursery.core.Receipt
import com.nursery.core.ReceiptStatus
import com.nursery.core.SaleUnit
import com.nursery.scanner.data.local.entity.LineItemEntity
import com.nursery.scanner.data.local.entity.PlantEntity
import com.nursery.scanner.data.local.entity.ReceiptEntity
import com.nursery.scanner.data.local.entity.ReceiptWithLines

// ---- Plant ----

fun PlantEntity.toCore(): Plant =
    Plant(
        accession = accession, name = name, group = group, light = light,
        potsInNursery = potsInNursery, tubesInNursery = tubesInNursery, miscInNursery = miscInNursery,
    )

fun Plant.toEntity(): PlantEntity =
    PlantEntity(
        accession = accession, name = name, group = group, light = light,
        potsInNursery = potsInNursery, tubesInNursery = tubesInNursery, miscInNursery = miscInNursery,
    )

// ---- LineItem ----

fun LineItemEntity.toCore(): LineItem =
    LineItem(
        accession = accession,
        name = name,
        qty = pots,
        unitPriceCents = unitPriceCents,
        discountPct = discountPct,
        unit = runCatching { SaleUnit.valueOf(unit) }.getOrDefault(SaleUnit.POTS),
    )

fun LineItem.toEntity(receiptId: Long): LineItemEntity =
    LineItemEntity(
        receiptId = receiptId,
        accession = accession,
        name = name,
        pots = qty,
        unitPriceCents = unitPriceCents,
        discountPct = discountPct,
        unit = unit.name,
    )

// ---- Receipt ----

fun ReceiptWithLines.toCore(): Receipt =
    Receipt(
        localId = receipt.localId,
        receiptNo = receipt.receiptNo,
        createdAtEpochMs = receipt.createdAtEpochMs,
        status = ReceiptStatus.valueOf(receipt.status),
        lines = lines.map { it.toCore() },
    )

/** Header row for a not-yet-saved receipt (localId 0 lets Room autogenerate). */
fun Receipt.toEntity(): ReceiptEntity =
    ReceiptEntity(
        localId = localId,
        receiptNo = receiptNo,
        createdAtEpochMs = createdAtEpochMs,
        status = status.name,
    )
