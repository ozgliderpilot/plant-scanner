package com.nursery.scanner.data.repo

import com.nursery.core.DeviceConfig
import com.nursery.core.LineItem
import com.nursery.core.Receipt
import com.nursery.core.ReceiptNumbering
import com.nursery.core.ReceiptStatus
import com.nursery.scanner.data.local.dao.ReceiptDao
import com.nursery.scanner.data.local.entity.ReceiptEntity
import com.nursery.scanner.data.local.toCore
import com.nursery.scanner.data.local.toEntity
import com.nursery.scanner.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

/** Local sales history. Receipts are written here first (offline-first) then exported later. */
class ReceiptRepository(
    private val receiptDao: ReceiptDao,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    val receipts: Flow<List<Receipt>> =
        receiptDao.observeReceipts().map { list -> list.map { it.toCore() } }

    /**
     * Allocate the next `PP-NNN` number, save the receipt + lines as SAVED (pending export).
     * Returns the persisted receipt.
     */
    suspend fun saveReceipt(lines: List<LineItem>, config: DeviceConfig): Receipt {
        val createdAt = now()
        val todayEpochDay = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate().toEpochDay()
        val seq = settings.nextReceiptSeq(todayEpochDay)
        val receiptNo = ReceiptNumbering(config.devicePrefix).format(createdAt / 1000, seq)
        val header = ReceiptEntity(
            receiptNo = receiptNo,
            createdAtEpochMs = createdAt,
            status = ReceiptStatus.SAVED.name,
        )
        val stamped = lines.mapIndexed { index, line -> line.copy(itemSeq = index + 1) }
        val id = receiptDao.saveReceipt(header, stamped.map { it.toEntity(0) })
        return Receipt(
            localId = id,
            receiptNo = receiptNo,
            createdAtEpochMs = createdAt,
            status = ReceiptStatus.SAVED,
            lines = stamped,
        )
    }

    suspend fun receiptById(id: Long): Receipt? = receiptDao.receiptById(id)?.toCore()
}
