package com.nursery.scanner.data.remote

import com.nursery.core.DeviceConfig
import com.nursery.core.Plant
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

    private fun postRaw(url: String, body: String): String {
        val request = Request.Builder().url(url).post(body.toRequestBody(mediaType)).build()
        client.newCall(request).execute().use { response ->
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
                ),
            )
            val resp = json.decodeFromString<GetPlantsResponse>(postRaw(config.endpointUrl, requestBody))
            FetchPlantsResult(
                ok = resp.ok,
                unchanged = resp.unchanged,
                plants = resp.plants?.map {
                    Plant(
                        accession = it.accession,
                        name = it.name,
                        genus = it.genus,
                        species = it.species,
                        cultivar = it.cultivar,
                        commonName = it.commonName,
                        group = it.group,
                        light = it.light,
                        potsInNursery = it.potsInNursery,
                        tubesInNursery = it.tubesInNursery,
                        miscInNursery = it.miscInNursery,
                        stockInNursery = it.stockInNursery,
                        potsForSale = it.potsForSale,
                        tubesForSale = it.tubesForSale,
                        miscForSale = it.miscForSale,
                    )
                },
                plantListFingerprint = resp.plantListFingerprint,
                error = resp.error,
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
                ),
            )
            val resp = json.decodeFromString<AppendExportResponse>(postRaw(config.endpointUrl, requestBody))
            if (!resp.ok) error(resp.error ?: rejectMessage)
            AppendOutcome(appended = resp.appended, skipped = resp.skipped)
        }
    }
}
