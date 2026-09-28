package com.nursery.scanner.data.remote

import kotlinx.serialization.Serializable

/** Wire format shared with the Apps Script backend (see backend/Code.gs). */

@Serializable
data class PlantDto(
    val accession: String,
    val name: String,
    val genus: String = "",
    val species: String = "",
    val cultivar: String = "",
    val commonName: String = "",
    val group: String? = null,
    val light: String? = null,
    val potsInNursery: Int = 0,
    val tubesInNursery: Int = 0,
    val miscInNursery: Int = 0,
    val stockInNursery: Int = 0,
    val potsForSale: Boolean = false,
    val tubesForSale: Boolean = false,
    val miscForSale: Boolean = false,
)

@Serializable
data class GetPlantsRequest(
    val secret: String,
    val action: String = "getPlants",
    val plantListFingerprint: String? = null,
    val devicePrefix: String,
    val deviceSecret: String,
)

@Serializable
data class GetPlantsResponse(
    val ok: Boolean,
    val plants: List<PlantDto>? = null,
    val count: Int = 0,
    val updatedAt: String? = null,
    val error: String? = null,
    val unchanged: Boolean = false,
    val plantListFingerprint: String? = null,
)

@Serializable
data class AppendExportRequest(
    val secret: String,
    val header: List<String>,
    val rows: List<List<String>>,
    val action: String,
    val devicePrefix: String,
    val deviceSecret: String,
)

@Serializable
data class AppendExportResponse(
    val ok: Boolean,
    val appended: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
)

@Serializable
data class QueueRowsDto(
    val rows: List<List<String>>,
)

@Serializable
data class PlantListSyncRequestDto(
    val secret: String,
    val action: String = "plantListSync",
    val devicePrefix: String,
    val deviceSecret: String,
    val plantListFingerprint: String? = null,
    val sales: QueueRowsDto? = null,
    val culls: QueueRowsDto? = null,
    val printLabels: QueueRowsDto? = null,
    val repots: QueueRowsDto? = null,
)

@Serializable
data class QueueSyncResultDto(
    val appended: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
)

@Serializable
data class PlantListSyncResponseDto(
    val ok: Boolean,
    val sales: QueueSyncResultDto? = null,
    val culls: QueueSyncResultDto? = null,
    val printLabels: QueueSyncResultDto? = null,
    val repots: QueueSyncResultDto? = null,
    val unchanged: Boolean = false,
    val plantListFingerprint: String? = null,
    val plants: List<PlantDto>? = null,
    val count: Int = 0,
    val error: String? = null,
)
