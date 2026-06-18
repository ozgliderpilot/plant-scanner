package com.nursery.scanner.data.remote

import kotlinx.serialization.Serializable

/** Wire format shared with the Apps Script backend (see backend/Code.gs). */

@Serializable
data class PlantDto(
    val accession: String,
    val name: String,
    val group: String? = null,
    val light: String? = null,
    val potsInNursery: Int = 0,
    val tubesInNursery: Int = 0,
    val miscInNursery: Int = 0,
    val stockInNursery: Int = 0,
)

@Serializable
data class GetPlantsRequest(
    val secret: String,
    val action: String = "getPlants",
)

@Serializable
data class GetPlantsResponse(
    val ok: Boolean,
    val plants: List<PlantDto> = emptyList(),
    val count: Int = 0,
    val updatedAt: String? = null,
    val error: String? = null,
)

@Serializable
data class AppendSalesRequest(
    val secret: String,
    val header: List<String>,
    val rows: List<List<String>>,
    val action: String = "appendSales",
)

@Serializable
data class AppendSalesResponse(
    val ok: Boolean,
    val appended: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
)
