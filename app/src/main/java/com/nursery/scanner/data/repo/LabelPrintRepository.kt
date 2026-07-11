package com.nursery.scanner.data.repo

import com.nursery.core.DeviceConfig
import com.nursery.core.LabelPrintList
import com.nursery.core.LabelPrintRequest
import com.nursery.core.LabelPrintStatus
import com.nursery.core.ReceiptNumbering
import com.nursery.scanner.data.local.dao.LabelPrintDao
import com.nursery.scanner.data.local.entity.LabelPrintEntity
import com.nursery.scanner.data.local.toCore
import com.nursery.scanner.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

/** Local label print requests. Written here first (offline-first) then exported later. */
class LabelPrintRepository(
    private val labelPrintDao: LabelPrintDao,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    val requests: Flow<List<LabelPrintRequest>> =
        labelPrintDao.observeRequests().map { list -> LabelPrintList.grouped(list.map { it.toCore() }) }

    suspend fun saveRequest(
        accession: String,
        name: String,
        copies: Int,
        config: DeviceConfig,
    ): LabelPrintRequest {
        val createdAt = now()
        val todayEpochDay = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate().toEpochDay()
        val seq = settings.nextReceiptSeq(todayEpochDay)
        val queueId = ReceiptNumbering(config.devicePrefix).format(createdAt / 1000, seq)
        val entity = LabelPrintEntity(
            queueId = queueId,
            createdAtEpochMs = createdAt,
            status = LabelPrintStatus.PENDING.name,
            accession = accession,
            name = name,
            copies = copies,
        )
        val id = labelPrintDao.insert(entity)
        return entity.copy(localId = id).toCore()
    }
}
