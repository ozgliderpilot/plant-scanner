package com.nursery.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetentionTest {

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-07-05T12:00:00Z").toEpochMilli()

    @Test fun `pending rows are never eligible`() {
        val oldEnough = Instant.parse("2026-07-01T12:00:00Z").toEpochMilli()
        assertFalse(Retention.isEligibleForPurge(oldEnough, CullStatus.PENDING, now, zone))
        assertFalse(Retention.isEligibleForPurge(oldEnough, ReceiptStatus.SAVED, now, zone))
        assertFalse(Retention.isEligibleForPurge(oldEnough, ReceiptStatus.OPEN, now, zone))
    }

    @Test fun `exported rows purge when created more than 3 calendar days ago`() {
        val fourDaysAgo = Instant.parse("2026-07-01T23:59:00Z").toEpochMilli()
        val exactlyThreeDaysAgoMorning = Instant.parse("2026-07-02T00:00:00Z").toEpochMilli()
        val exactlyThreeDaysAgoEvening = Instant.parse("2026-07-02T23:59:00Z").toEpochMilli()
        val yesterday = Instant.parse("2026-07-04T12:00:00Z").toEpochMilli()

        assertTrue(Retention.isEligibleForPurge(fourDaysAgo, CullStatus.EXPORTED, now, zone))
        assertTrue(Retention.isEligibleForPurge(fourDaysAgo, ReceiptStatus.EXPORTED, now, zone))
        assertFalse(Retention.isEligibleForPurge(exactlyThreeDaysAgoMorning, CullStatus.EXPORTED, now, zone))
        assertFalse(Retention.isEligibleForPurge(exactlyThreeDaysAgoEvening, ReceiptStatus.EXPORTED, now, zone))
        assertFalse(Retention.isEligibleForPurge(yesterday, CullStatus.EXPORTED, now, zone))
    }

    @Test fun `records from the same local day are purged together`() {
        val morning = Instant.parse("2026-07-01T08:00:00Z").toEpochMilli()
        val evening = Instant.parse("2026-07-01T20:00:00Z").toEpochMilli()
        assertTrue(Retention.isEligibleForPurge(morning, ReceiptStatus.EXPORTED, now, zone))
        assertTrue(Retention.isEligibleForPurge(evening, ReceiptStatus.EXPORTED, now, zone))
    }

    @Test fun `purge cutoff is start of local day three days before now`() {
        val cutoff = Retention.purgeCutoffEpochMs(now, zone)
        assertEquals(Instant.parse("2026-07-02T00:00:00Z").toEpochMilli(), cutoff)
    }
}
