package com.nursery.scanner.data.repo

import com.nursery.core.DeviceConfig
import com.nursery.core.ReceiptNumbering
import com.nursery.core.RepotList
import com.nursery.core.RepotRecord
import com.nursery.core.RepotStatus
import com.nursery.scanner.data.local.dao.RepotDao
import com.nursery.scanner.data.local.entity.RepotEntity
import com.nursery.scanner.data.local.toCore
import com.nursery.scanner.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

/** Local repot history. Written here first (offline-first) then exported later. */
class RepotRepository(
    private val repotDao: RepotDao,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    val repots: Flow<List<RepotRecord>> =
        repotDao.observeRepots().map { list -> RepotList.grouped(list.map { it.toCore() }) }

    /**
     * Allocate the next `PP-<epoch>-<seq>` number (shared daily counter with sales), save as
     * PENDING. Returns the persisted record.
     */
    suspend fun saveRepot(
        accession: String,
        name: String,
        genus: String,
        species: String,
        cultivar: String,
        commonName: String,
        group: String?,
        tubesBefore: Int,
        potsBefore: Int,
        miscBefore: Int,
        stockBefore: Int,
        tubes: Int,
        pots: Int,
        misc: Int,
        stock: Int,
        tubesForSale: Boolean,
        potsForSale: Boolean,
        miscForSale: Boolean,
        config: DeviceConfig,
    ): RepotRecord {
        val createdAt = now()
        val todayEpochDay = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate().toEpochDay()
        val seq = settings.nextReceiptSeq(todayEpochDay)
        val repotNo = ReceiptNumbering(config.devicePrefix).format(createdAt / 1000, seq)
        val entity = RepotEntity(
            repotNo = repotNo,
            createdAtEpochMs = createdAt,
            status = RepotStatus.PENDING.name,
            accession = accession,
            name = name,
            genus = genus,
            species = species,
            cultivar = cultivar,
            commonName = commonName,
            group = group,
            tubesBefore = tubesBefore,
            potsBefore = potsBefore,
            miscBefore = miscBefore,
            stockBefore = stockBefore,
            tubes = tubes,
            pots = pots,
            misc = misc,
            stock = stock,
            tubesForSale = tubesForSale,
            potsForSale = potsForSale,
            miscForSale = miscForSale,
        )
        val id = repotDao.insert(entity)
        return entity.copy(localId = id).toCore()
    }
}
