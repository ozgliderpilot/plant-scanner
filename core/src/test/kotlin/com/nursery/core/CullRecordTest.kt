package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CullRecordTest {

    private fun record(qty: Int = 1, notes: String? = null) = CullRecord(
        localId = 1,
        cullNo = "PP-1",
        createdAtEpochMs = 0,
        status = CullStatus.PENDING,
        accession = "2021-0001",
        name = "Banksia",
        group = null,
        isUnknown = false,
        qty = qty,
        unit = SaleUnit.TUBES,
        reason = CullReason.DEAD,
        notes = notes,
    )

    @Test fun `default reason is Dead`() {
        assertEquals(CullReason.DEAD, CullReason.DEFAULT)
        assertEquals("Dead", CullReason.DEAD.label)
    }

    @Test fun `reason labels match spec`() {
        assertEquals("Poor quality", CullReason.POOR_QUALITY.label)
        assertEquals("Pest", CullReason.PEST.label)
        assertEquals("Disease", CullReason.DISEASE.label)
        assertEquals("Other", CullReason.OTHER.label)
    }

    @Test fun `qty must be at least 1`() {
        assertEquals("Quantity must be at least 1", CullRecord.validationError(record(qty = 0)))
        assertNull(CullRecord.validationError(record(qty = 1)))
    }

    @Test fun `notes max 200 chars`() {
        assertNull(CullRecord.validationError(record(notes = "a".repeat(200))))
        assertEquals(
            "Notes must be at most 200 characters",
            CullRecord.validationError(record(notes = "a".repeat(201))),
        )
    }

    @Test fun `notes cannot contain bracket or brace characters`() {
        assertNull(CullRecord.validationError(record(notes = "aphids on tips")))
        assertNull(CullRecord.validationError(record(notes = "Stock plant")))
        val message = "Notes cannot contain [, ], {, or }"
        for (char in listOf('[', ']', '{', '}')) {
            assertEquals(message, CullRecord.validationError(record(notes = "note$char")))
        }
    }
}
