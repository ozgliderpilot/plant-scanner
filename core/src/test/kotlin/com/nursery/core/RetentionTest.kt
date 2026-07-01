package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetentionTest {

    private val now = 1_000_000L
    private val oldEnough = now - Retention.RETENTION_MS - 1
    private val tooRecent = now - Retention.RETENTION_MS + 1

    @Test fun `pending rows are never eligible`() {
        assertFalse(Retention.isEligibleForPurge(oldEnough, CullStatus.PENDING, now))
        assertFalse(Retention.isEligibleForPurge(oldEnough, ReceiptStatus.SAVED, now))
        assertFalse(Retention.isEligibleForPurge(oldEnough, ReceiptStatus.OPEN, now))
    }

    @Test fun `exported rows purge after retention window`() {
        assertTrue(Retention.isEligibleForPurge(oldEnough, CullStatus.EXPORTED, now))
        assertTrue(Retention.isEligibleForPurge(oldEnough, ReceiptStatus.EXPORTED, now))
        assertFalse(Retention.isEligibleForPurge(tooRecent, CullStatus.EXPORTED, now))
        assertFalse(Retention.isEligibleForPurge(tooRecent, ReceiptStatus.EXPORTED, now))
    }
}
