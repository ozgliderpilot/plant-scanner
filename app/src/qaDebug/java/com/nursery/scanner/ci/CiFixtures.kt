package com.nursery.scanner.ci

import com.nursery.core.CullReason
import com.nursery.core.CullRecord
import com.nursery.core.CullStatus
import com.nursery.core.DeviceConfig
import com.nursery.core.LineItem
import com.nursery.core.PaymentMethod
import com.nursery.core.Plant
import com.nursery.core.Receipt
import com.nursery.core.ReceiptStatus
import com.nursery.core.SaleUnit

/** Fixed fixture values for the CI screenshot gallery (#72). */
object CiFixtures {
    const val DEVICE_PREFIX = "99"
    const val ENDPOINT_URL = "https://ci.invalid/exec"
    const val SHARED_SECRET = "ci-secret"

    /**
     * Accessions Maestro types on the sell path (first is the walked sale).
     * Numeric so they work with the "Accession number" Number keyboard.
     */
    val ACCESSIONS = listOf("1001", "1002", "1003")

    const val WALK_ACCESSION = "1001"

    val PLANTS: List<Plant> = listOf(
        Plant(
            accession = "1001",
            name = "Grevillea rosmarinifolia",
            genus = "Grevillea",
            species = "rosmarinifolia",
            cultivar = "",
            commonName = "Rosemary Grevillea",
            group = "Shrubs",
            light = "Full sun",
            potsInNursery = 12,
            // Tubes > 0 so the gallery walk can add the same Grevillea as a tube line.
            tubesInNursery = 6,
            miscInNursery = 0,
            stockInNursery = 0,
        ),
        Plant(
            accession = "1002",
            name = "Westringia fruticosa",
            genus = "Westringia",
            species = "fruticosa",
            cultivar = "",
            commonName = "Coastal Rosemary",
            group = "Shrubs",
            light = "Full sun",
            potsInNursery = 8,
            tubesInNursery = 4,
            miscInNursery = 0,
            stockInNursery = 1,
        ),
        Plant(
            accession = "1003",
            name = "Lomandra longifolia",
            genus = "Lomandra",
            species = "longifolia",
            cultivar = "Tanika",
            commonName = "Spiny-headed Mat-rush",
            group = "Grasses",
            light = "Part shade",
            potsInNursery = 20,
            tubesInNursery = 0,
            miscInNursery = 0,
            stockInNursery = 0,
        ),
    )

    /** Deterministic epoch so receipt numbers are stable across screenshot runs. */
    const val SEED_EPOCH_MS = 1_700_000_000_000L

    /** How many daily seq slots the seeded receipt + cull occupy (walked sale uses the next). */
    const val SEEDED_SEQ_COUNT = 2

    fun seededReceipt(): Receipt = Receipt(
        localId = 0,
        receiptNo = "99-1700000000-1",
        createdAtEpochMs = SEED_EPOCH_MS,
        status = ReceiptStatus.SAVED,
        lines = listOf(
            LineItem(
                accession = "1002",
                name = "Westringia fruticosa",
                genus = "Westringia",
                species = "fruticosa",
                cultivar = "",
                commonName = "Coastal Rosemary",
                group = "Shrubs",
                qty = 2,
                unitPriceCents = 800,
                discountPct = 10,
                unit = SaleUnit.POTS,
                itemSeq = 1,
            ),
        ),
        paymentMethod = PaymentMethod.CARD,
    )

    fun seededCull(): CullRecord = CullRecord(
        localId = 0,
        cullNo = "99-1700000000-2",
        createdAtEpochMs = SEED_EPOCH_MS + 60_000L,
        status = CullStatus.PENDING,
        accession = "1003",
        name = "Lomandra longifolia",
        genus = "Lomandra",
        species = "longifolia",
        cultivar = "Tanika",
        commonName = "Spiny-headed Mat-rush",
        group = "Grasses",
        isUnknown = false,
        qty = 1,
        unit = SaleUnit.POTS,
        reason = CullReason.DEAD,
        notes = "CI fixture",
    )

    fun deviceConfig(): DeviceConfig = DeviceConfig(
        devicePrefix = DEVICE_PREFIX,
        endpointUrl = ENDPOINT_URL,
        sharedSecret = SHARED_SECRET,
        autoExportSeconds = DeviceConfig.DEFAULT_INTERVAL_SECONDS,
    )
}
