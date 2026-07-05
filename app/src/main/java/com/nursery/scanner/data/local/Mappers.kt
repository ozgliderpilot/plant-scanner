package com.nursery.scanner.data.local

import com.nursery.core.CullReason
import com.nursery.core.CullRecord
import com.nursery.core.CullStatus
import com.nursery.core.LineItem
import com.nursery.core.PaymentMethod
import com.nursery.core.Plant
import com.nursery.core.Receipt
import com.nursery.core.ReceiptStatus
import com.nursery.core.SaleUnit
import com.nursery.scanner.data.local.entity.CullEntity
import com.nursery.scanner.data.local.entity.LineItemEntity
import com.nursery.scanner.data.local.entity.PlantEntity
import com.nursery.scanner.data.local.entity.ReceiptEntity
import com.nursery.scanner.data.local.entity.ReceiptWithLines

// ---- Plant ----

fun PlantEntity.toCore(): Plant =
    Plant(
        accession = accession, name = name, genus = genus, species = species,
        cultivar = cultivar, commonName = commonName, group = group, light = light,
        potsInNursery = potsInNursery, tubesInNursery = tubesInNursery, miscInNursery = miscInNursery,
        stockInNursery = stockInNursery,
    )

fun Plant.toEntity(): PlantEntity =
    PlantEntity(
        accession = accession, name = name, genus = genus, species = species,
        cultivar = cultivar, commonName = commonName, group = group, light = light,
        potsInNursery = potsInNursery, tubesInNursery = tubesInNursery, miscInNursery = miscInNursery,
        stockInNursery = stockInNursery,
    )

// ---- LineItem ----

fun LineItemEntity.toCore(): LineItem =
    LineItem(
        accession = accession,
        name = name,
        genus = genus,
        species = species,
        cultivar = cultivar,
        commonName = commonName,
        group = group,
        qty = qty,
        unitPriceCents = unitPriceCents,
        discountPct = discountPct,
        unit = runCatching { SaleUnit.valueOf(unit) }.getOrDefault(SaleUnit.POTS),
        itemSeq = itemSeq,
    )

fun LineItem.toEntity(receiptId: Long): LineItemEntity =
    LineItemEntity(
        receiptId = receiptId,
        accession = accession,
        name = name,
        genus = genus,
        species = species,
        cultivar = cultivar,
        commonName = commonName,
        group = group,
        qty = qty,
        unitPriceCents = unitPriceCents,
        discountPct = discountPct,
        unit = unit.name,
        itemSeq = itemSeq,
    )

// ---- Receipt ----

fun ReceiptWithLines.toCore(): Receipt =
    Receipt(
        localId = receipt.localId,
        receiptNo = receipt.receiptNo,
        createdAtEpochMs = receipt.createdAtEpochMs,
        status = ReceiptStatus.valueOf(receipt.status),
        lines = lines.map { it.toCore() },
        paymentMethod = runCatching { PaymentMethod.valueOf(receipt.paymentMethod) }
            .getOrDefault(PaymentMethod.CARD),
    )

/** Header row for a not-yet-saved receipt (localId 0 lets Room autogenerate). */
fun Receipt.toEntity(): ReceiptEntity =
    ReceiptEntity(
        localId = localId,
        receiptNo = receiptNo,
        createdAtEpochMs = createdAtEpochMs,
        status = status.name,
        paymentMethod = paymentMethod.name,
    )

// ---- Cull ----

fun CullEntity.toCore(): CullRecord =
    CullRecord(
        localId = localId,
        cullNo = cullNo,
        createdAtEpochMs = createdAtEpochMs,
        status = CullStatus.valueOf(status),
        accession = accession,
        name = name,
        genus = genus,
        species = species,
        cultivar = cultivar,
        commonName = commonName,
        group = group,
        isUnknown = isUnknown,
        qty = qty,
        unit = runCatching { SaleUnit.valueOf(unit) }.getOrDefault(SaleUnit.TUBES),
        reason = runCatching { CullReason.valueOf(reason) }.getOrDefault(CullReason.DEFAULT),
        notes = notes,
    )

fun CullRecord.toEntity(): CullEntity =
    CullEntity(
        localId = localId,
        cullNo = cullNo,
        createdAtEpochMs = createdAtEpochMs,
        status = status.name,
        accession = accession,
        name = name,
        genus = genus,
        species = species,
        cultivar = cultivar,
        commonName = commonName,
        group = group,
        isUnknown = isUnknown,
        qty = qty,
        unit = unit.name,
        reason = reason.name,
        notes = notes,
    )
