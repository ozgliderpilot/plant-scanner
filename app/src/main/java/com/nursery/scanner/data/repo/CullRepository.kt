package com.nursery.scanner.data.repo

import com.nursery.core.CullList
import com.nursery.core.CullRecord
import com.nursery.core.CullStatus
import com.nursery.core.CullReason
import com.nursery.core.DeviceConfig
import com.nursery.core.ReceiptNumbering
import com.nursery.core.SaleUnit
import com.nursery.scanner.data.local.dao.CullDao
import com.nursery.scanner.data.local.entity.CullEntity
import com.nursery.scanner.data.local.toCore
import com.nursery.scanner.data.local.toEntity
import com.nursery.scanner.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

/** Local cull history. Culls are written here first (offline-first) then exported later. */
class CullRepository(
    private val cullDao: CullDao,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    val culls: Flow<List<CullRecord>> =
        cullDao.observeCulls().map { list -> CullList.grouped(list.map { it.toCore() }) }

    /**
     * Allocate the next `PP-<epoch>-<seq>` number (shared daily counter with sales), save as
     * PENDING. Returns the persisted record.
     */
    suspend fun saveCull(
        accession: String,
        name: String,
        genus: String,
        species: String,
        cultivar: String,
        commonName: String,
        group: String?,
        isUnknown: Boolean,
        qty: Int,
        unit: SaleUnit,
        reason: CullReason,
        notes: String?,
        config: DeviceConfig,
    ): CullRecord {
        val createdAt = now()
        val todayEpochDay = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate().toEpochDay()
        val seq = settings.nextReceiptSeq(todayEpochDay)
        val cullNo = ReceiptNumbering(config.devicePrefix).format(createdAt / 1000, seq)
        val entity = CullEntity(
            cullNo = cullNo,
            createdAtEpochMs = createdAt,
            status = CullStatus.PENDING.name,
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
            notes = notes?.takeIf { it.isNotBlank() },
        )
        val id = cullDao.insert(entity)
        return entity.copy(localId = id).toCore()
    }

    /** Insert a pre-numbered cull (CI seed path; does not allocate a new seq). */
    suspend fun insertSeeded(cull: CullRecord): Long =
        cullDao.insert(cull.copy(localId = 0).toEntity())
}
