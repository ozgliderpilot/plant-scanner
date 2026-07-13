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
)

@Serializable
data class AppendExportResponse(
    val ok: Boolean,
    val appended: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
)
