package com.nursery.core

/**
 * Retention policy for exported rows on the device. Pending rows are kept forever; exported rows
 * are eligible for purge once older than [RETENTION_MS] from [createdAtEpochMs].
 */
object Retention {

    const val RETENTION_MS: Long = 72L * 60 * 60 * 1000

    fun isEligibleForPurge(createdAtEpochMs: Long, status: CullStatus, now: Long): Boolean =
        status == CullStatus.EXPORTED && now - createdAtEpochMs > RETENTION_MS

    fun isEligibleForPurge(createdAtEpochMs: Long, status: ReceiptStatus, now: Long): Boolean =
        status == ReceiptStatus.EXPORTED && now - createdAtEpochMs > RETENTION_MS
}
