package com.nursery.core

/**
 * Policy for conditional plant-list import: what fingerprint to send on `getPlants`,
 * and whether a response means replace the local cache or keep it.
 *
 * The device never hashes the list — it only echoes an opaque plant-list fingerprint
 * from the server. See ADR-0016.
 */
object PlantListImport {

    /**
     * Fingerprint to include on a `getPlants` request, or `null` to force a full pull.
     *
     * Manual refresh and an empty local cache always omit the fingerprint.
     */
    fun fingerprintForRequest(
        forceFullPull: Boolean,
        localPlantCount: Int,
        storedFingerprint: String?,
    ): String? {
        if (forceFullPull) return null
        if (localPlantCount <= 0) return null
        return storedFingerprint?.trim()?.takeIf { it.isNotEmpty() }
    }

    sealed interface Outcome {
        /** Replace the local plant cache with [plants] and persist [fingerprintToStore]. */
        data class Apply(
            val plants: List<Plant>,
            val fingerprintToStore: String,
        ) : Outcome

        /** Keep the local cache; import still succeeded (unchanged list). */
        data object KeepCache : Outcome

        data class Err(val message: String) : Outcome
    }

    /**
     * Decide how to apply a `getPlants` response to the local plant cache.
     *
     * [plants] must be non-null on a full pull (empty list is valid for an empty nursery).
     * Null [plants] means the payload omitted the list — not a successful apply.
     */
    fun decide(
        ok: Boolean,
        unchanged: Boolean,
        plants: List<Plant>?,
        fingerprint: String?,
        error: String?,
    ): Outcome {
        if (!ok) return Outcome.Err(error?.takeIf { it.isNotBlank() } ?: "Server rejected the request")
        if (unchanged) return Outcome.KeepCache
        if (plants == null) return Outcome.Err("Missing plant list in response")
        val fp = fingerprint?.trim()?.takeIf { it.isNotEmpty() }
            ?: return Outcome.Err("Missing plant-list fingerprint")
        return Outcome.Apply(plants = plants, fingerprintToStore = fp)
    }
}
