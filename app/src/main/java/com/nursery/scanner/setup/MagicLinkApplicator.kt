package com.nursery.scanner.setup

import android.content.Intent
import android.net.Uri
import com.nursery.core.DeviceConfig
import com.nursery.core.MagicLink
import com.nursery.core.MagicLinkConfig
import com.nursery.core.ReceiptNumbering
import com.nursery.scanner.data.settings.SettingsRepository

/**
 * Applies a setup magic link: writes prefix / URL / access code into settings and generates a
 * fresh device secret. The Users-tab claim happens on the next device-bound sync request.
 */
class MagicLinkApplicator(
    private val settings: SettingsRepository,
    private val newDeviceSecret: () -> String = { generateDeviceSecret() },
) {
    sealed class Result {
        data class Applied(val config: DeviceConfig) : Result()
        data class Invalid(val reason: String) : Result()
    }

    suspend fun apply(uri: Uri): Result {
        val parsed = parseUri(uri)
            ?: return Result.Invalid("This setup link is not valid")
        return apply(parsed)
    }

    suspend fun apply(uriString: String): Result {
        val parsed = MagicLink.parse(uriString)
            ?: return Result.Invalid("This setup link is not valid")
        return apply(parsed)
    }

    suspend fun apply(parsed: MagicLinkConfig): Result {
        val config = DeviceConfig(
            devicePrefix = parsed.devicePrefix,
            endpointUrl = parsed.endpointUrl,
            sharedSecret = parsed.accessCode,
            autoExportSeconds = DeviceConfig.DEFAULT_INTERVAL_SECONDS,
            deviceSecret = newDeviceSecret(),
        )
        settings.saveConfig(config)
        return Result.Applied(config)
    }

    companion object {
        fun uriFromIntent(intent: Intent?): Uri? {
            if (intent == null) return null
            if (intent.action != Intent.ACTION_VIEW) return null
            val uri = intent.data ?: return null
            if (!uri.scheme.equals(MagicLink.SCHEME, ignoreCase = true)) return null
            return uri
        }

        /** Prefer Android query decoding over Uri.toString() round-trips. */
        fun parseUri(uri: Uri): MagicLinkConfig? {
            if (!uri.scheme.equals(MagicLink.SCHEME, ignoreCase = true)) return null
            if (!uri.host.equals(MagicLink.HOST, ignoreCase = true)) return null
            val prefix = uri.getQueryParameter("prefix")?.trim().orEmpty()
            val url = uri.getQueryParameter("url")?.trim().orEmpty()
            val code = uri.getQueryParameter("code")?.trim().orEmpty()
            if (!ReceiptNumbering.isValidPrefix(prefix)) return null
            if (url.isEmpty() || code.isEmpty()) return null
            return MagicLinkConfig(devicePrefix = prefix, endpointUrl = url, accessCode = code)
        }
    }
}
