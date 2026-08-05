package com.nursery.core

/**
 * Payload carried by a setup magic link (`plantscanner://setup?prefix=&url=&code=`).
 * The app generates a fresh [DeviceConfig.deviceSecret] when applying the link; the link itself
 * never contains the per-device secret (ADR-0017).
 */
data class MagicLinkConfig(
    val devicePrefix: String,
    val endpointUrl: String,
    val accessCode: String,
)

/**
 * Parse and build custom-scheme magic links for one-time device setup.
 *
 * Format: `plantscanner://setup?prefix=07&url=<urlencoded>&code=<urlencoded>`
 */
object MagicLink {
    const val SCHEME = "plantscanner"
    const val HOST = "setup"

    /**
     * Parse a magic-link URI. Returns null when the scheme/host/required query params are missing
     * or the prefix is not two digits.
     */
    fun parse(uri: String): MagicLinkConfig? {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return null

        val withoutScheme = when {
            trimmed.startsWith("$SCHEME://", ignoreCase = true) ->
                trimmed.substring(SCHEME.length + 3)
            else -> return null
        }
        val q = withoutScheme.indexOf('?')
        val authorityAndPath = if (q >= 0) withoutScheme.substring(0, q) else withoutScheme
        val query = if (q >= 0) withoutScheme.substring(q + 1) else ""
        val host = authorityAndPath.substringBefore('/').substringBefore('#').lowercase()
        if (host != HOST) return null

        val params = parseQuery(query)
        val prefix = params["prefix"]?.trim().orEmpty()
        val url = params["url"]?.trim().orEmpty()
        val code = params["code"]?.trim().orEmpty()
        if (!ReceiptNumbering.isValidPrefix(prefix)) return null
        if (url.isEmpty() || code.isEmpty()) return null
        return MagicLinkConfig(devicePrefix = prefix, endpointUrl = url, accessCode = code)
    }

    /** Build a magic-link URI for the given setup fields (values are form-urlencoded). */
    fun build(prefix: String, endpointUrl: String, accessCode: String): String {
        require(ReceiptNumbering.isValidPrefix(prefix)) { "prefix must be two digits" }
        return "$SCHEME://$HOST?prefix=$prefix" +
            "&url=${formEncode(endpointUrl)}" +
            "&code=${formEncode(accessCode)}"
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        val out = linkedMapOf<String, String>()
        for (part in query.split('&')) {
            if (part.isEmpty()) continue
            val eq = part.indexOf('=')
            val rawKey = if (eq >= 0) part.substring(0, eq) else part
            val rawVal = if (eq >= 0) part.substring(eq + 1) else ""
            val key = formDecode(rawKey)
            if (key.isEmpty()) continue
            out[key] = formDecode(rawVal)
        }
        return out
    }

    /** application/x-www-form-urlencoded decode (plus → space, UTF-8). */
    private fun formDecode(s: String): String {
        val withSpaces = s.replace('+', ' ')
        val bytes = ArrayList<Byte>(withSpaces.length)
        var i = 0
        while (i < withSpaces.length) {
            val c = withSpaces[i]
            if (c == '%' && i + 2 < withSpaces.length) {
                val hi = withSpaces[i + 1].digitToIntOrNull(16)
                val lo = withSpaces[i + 2].digitToIntOrNull(16)
                if (hi != null && lo != null) {
                    bytes.add((hi * 16 + lo).toByte())
                    i += 3
                    continue
                }
            }
            // Literal ASCII code unit (form-urlencoded is byte-oriented).
            bytes.add(c.code.toByte())
            i++
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private fun formEncode(s: String): String {
        val sb = StringBuilder(s.length * 2)
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val v = b.toInt() and 0xff
            val c = v.toChar()
            when {
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '*' ->
                    sb.append(c)
                c == ' ' -> sb.append('+')
                else -> {
                    sb.append('%')
                    sb.append("0123456789ABCDEF"[v ushr 4])
                    sb.append("0123456789ABCDEF"[v and 0x0f])
                }
            }
        }
        return sb.toString()
    }
}
