package com.nursery.core

import java.time.Instant
import java.time.ZoneId

/**
 * Retention policy for exported rows on the device. Pending rows are kept forever; exported rows
 * are eligible for purge once their local creation date is more than [RETENTION_DAYS] calendar days
 * before [nowEpochMs] (same-day records are removed together).
 */
object Retention {

    const val RETENTION_DAYS: Long = 3L

    /** Epoch ms of the start of the local day [RETENTION_DAYS] before [nowEpochMs]. */
    fun purgeCutoffEpochMs(nowEpochMs: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        return today.minusDays(RETENTION_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun isEligibleForPurge(createdAtEpochMs: Long, status: CullStatus, now: Long, zone: ZoneId): Boolean =
        status == CullStatus.EXPORTED && createdAtEpochMs < purgeCutoffEpochMs(now, zone)

    fun isEligibleForPurge(createdAtEpochMs: Long, status: ReceiptStatus, now: Long, zone: ZoneId): Boolean =
        status == ReceiptStatus.EXPORTED && createdAtEpochMs < purgeCutoffEpochMs(now, zone)
}
