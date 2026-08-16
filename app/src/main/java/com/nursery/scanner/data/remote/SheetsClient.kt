package com.nursery.scanner.data.remote

import com.nursery.core.DeviceConfig
import com.nursery.core.Plant
import com.nursery.core.PlantListSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Result of an export push. */
data class AppendOutcome(val appended: Int, val skipped: Int)

/** Wire result of `getPlants` before apply-vs-keep-cache policy. */
data class FetchPlantsResult(
    val ok: Boolean,
    val unchanged: Boolean = false,
    /** Null when the server omitted plant rows (unchanged or malformed). Empty list = empty nursery. */
    val plants: List<Plant>? = null,
    val plantListFingerprint: String? = null,
    val error: String? = null,
)

/**
 * Talks to the Apps Script Web App over plain HTTPS + JSON using OkHttp directly (no Retrofit — the
 * two calls are simple and this avoids a fragile converter dependency). Apps Script answers with a
 * 302 to a googleusercontent URL; OkHttp follows it automatically. All network/parse failures surface
 * as a failed [Result] so callers (silent auto-export, manual sync) can react appropriately.
 */
class SheetsClient(
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** Longer read: one POST covers four queues plus the plant list (ADR-0018). */
    private val plantListSyncClient = client.newBuilder()
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun postRaw(url: String, body: String, http: OkHttpClient = client): String {
        val request = Request.Builder().url(url).post(body.toRequestBody(mediaType)).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return text
        }
    }

    suspend fun fetchPlants(
        config: DeviceConfig,
        plantListFingerprint: String? = null,
    ): Result<FetchPlantsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = json.encodeToString(
                GetPlantsRequest(
                    secret = config.sharedSecret,
                    plantListFingerprint = plantListFingerprint,
                    devicePrefix = config.devicePrefix,
                    deviceSecret = config.deviceSecret,
                ),
            )
            val resp = json.decodeFromString<GetPlantsResponse>(postRaw(config.endpointUrl, requestBody))
            FetchPlantsResult(
                ok = resp.ok,
                unchanged = resp.unchanged,
                plants = resp.plants?.map { it.toPlant() },
                plantListFingerprint = resp.plantListFingerprint,
                error = resp.error,
            )
        }
    }

    suspend fun plantListSync(
        config: DeviceConfig,
        request: PlantListSync.Request,
    ): Result<PlantListSync.Response> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = json.encodeToString(
                PlantListSyncRequestDto(
                    secret = config.sharedSecret,
                    plantListFingerprint = request.plantListFingerprint,
                    devicePrefix = config.devicePrefix,
                    deviceSecret = config.deviceSecret,
                    sales = request.sales?.let { QueueRowsDto(it) },
                    culls = request.culls?.let { QueueRowsDto(it) },
                    printLabels = request.printLabels?.let { QueueRowsDto(it) },
                    repots = request.repots?.let { QueueRowsDto(it) },
                ),
            )
            val resp = json.decodeFromString<PlantListSyncResponseDto>(
                postRaw(config.endpointUrl, requestBody, plantListSyncClient),
            )
            PlantListSync.Response(
                ok = resp.ok,
                error = resp.error,
                sales = resp.sales?.toCore(),
                culls = resp.culls?.toCore(),
                printLabels = resp.printLabels?.toCore(),
                repots = resp.repots?.toCore(),
                unchanged = resp.unchanged,
                plantListFingerprint = resp.plantListFingerprint,
                plants = resp.plants?.map { it.toPlant() },
            )
        }
    }

    suspend fun appendSales(
        config: DeviceConfig,
        header: List<String>,
        rows: List<List<String>>,
    ): Result<AppendOutcome> =
        appendRows(config, action = "appendSales", header = header, rows = rows,
            rejectMessage = "Server rejected the export")

    suspend fun appendCulls(
        config: DeviceConfig,
        header: List<String>,
        rows: List<List<String>>,
    ): Result<AppendOutcome> =
        appendRows(config, action = "appendCulls", header = header, rows = rows,
            rejectMessage = "Server rejected the cull export")

    suspend fun appendPrintLabels(
        config: DeviceConfig,
        header: List<String>,
        rows: List<List<String>>,
    ): Result<AppendOutcome> =
        appendRows(config, action = "appendPrintLabels", header = header, rows = rows,
            rejectMessage = "Server rejected the print label export")

    suspend fun appendRepots(
        config: DeviceConfig,
        header: List<String>,
        rows: List<List<String>>,
    ): Result<AppendOutcome> =
        appendRows(config, action = "appendRepots", header = header, rows = rows,
            rejectMessage = "Server rejected the repot export")

    private suspend fun appendRows(
        config: DeviceConfig,
        action: String,
        header: List<String>,
        rows: List<List<String>>,
        rejectMessage: String,
    ): Result<AppendOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = json.encodeToString(
                AppendExportRequest(
                    secret = config.sharedSecret,
                    header = header,
                    rows = rows,
                    action = action,
                    devicePrefix = config.devicePrefix,
                    deviceSecret = config.deviceSecret,
                ),
            )
            val resp = json.decodeFromString<AppendExportResponse>(postRaw(config.endpointUrl, requestBody))
            if (!resp.ok) error(resp.error ?: rejectMessage)
            AppendOutcome(appended = resp.appended, skipped = resp.skipped)
        }
    }
}

private fun PlantDto.toPlant(): Plant = Plant(
    accession = accession,
    name = name,
    genus = genus,
    species = species,
    cultivar = cultivar,
    commonName = commonName,
    group = group,
    light = light,
    potsInNursery = potsInNursery,
    tubesInNursery = tubesInNursery,
    miscInNursery = miscInNursery,
    stockInNursery = stockInNursery,
    potsForSale = potsForSale,
    tubesForSale = tubesForSale,
    miscForSale = miscForSale,
)

private fun QueueSyncResultDto.toCore(): PlantListSync.QueueResult = PlantListSync.QueueResult(
    appended = appended,
    skipped = skipped,
    error = error,
)
