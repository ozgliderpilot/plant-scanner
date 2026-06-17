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

    suspend fun fetchPlants(config: DeviceConfig): Result<List<Plant>> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = json.encodeToString(GetPlantsRequest(secret = config.sharedSecret))
            val resp = json.decodeFromString<GetPlantsResponse>(postRaw(config.endpointUrl, requestBody))
            if (!resp.ok) error(resp.error ?: "Server rejected the request")
            resp.plants.map {
                Plant(
                    accession = it.accession,
                    name = it.name,
                    group = it.group,
                    light = it.light,
                    potsInNursery = it.potsInNursery,
                    tubesInNursery = it.tubesInNursery,
                    miscInNursery = it.miscInNursery,
                )
            }
        }
    }

    suspend fun appendSales(
        config: DeviceConfig,
        header: List<String>,
        rows: List<List<String>>,
    ): Result<AppendOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = json.encodeToString(
                AppendSalesRequest(secret = config.sharedSecret, header = header, rows = rows),
            )
            val resp = json.decodeFromString<AppendSalesResponse>(postRaw(config.endpointUrl, requestBody))
            if (!resp.ok) error(resp.error ?: "Server rejected the export")
            AppendOutcome(appended = resp.appended, skipped = resp.skipped)
        }
    }
}
