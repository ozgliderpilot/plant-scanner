package com.nursery.core

/**
 * Per-device configuration: the 2-digit receipt prefix (decision #11), the Apps Script Web App URL +
 * nursery access code (shared secret), a per-device secret claimed via the Users tab (ADR-0017), and
 * the auto-export interval (default 60s, #10).
 *
 * A device can exist before it is fully connected (prefix + interval are always valid; URL / access
 * code / device secret may be blank until configured). [isComplete] gates whether sync is possible.
 */
data class DeviceConfig(
    val devicePrefix: String,
    val endpointUrl: String,
    val sharedSecret: String,
    val autoExportSeconds: Int,
    val deviceSecret: String = "",
) {
    init {
        require(ReceiptNumbering.isValidPrefix(devicePrefix)) {
            "devicePrefix must be exactly two digits, was '$devicePrefix'"
        }
        require(autoExportSeconds >= MIN_INTERVAL_SECONDS) {
            "autoExportSeconds must be >= $MIN_INTERVAL_SECONDS, was $autoExportSeconds"
        }
    }

    /** True when the device is wired to a backend and can pull/push. */
    val isComplete: Boolean
        get() = endpointUrl.isNotBlank() && sharedSecret.isNotBlank() && deviceSecret.isNotBlank()

    companion object {
        const val MIN_INTERVAL_SECONDS = 10
        const val DEFAULT_INTERVAL_SECONDS = 60
        const val DEFAULT_PREFIX = "00"

        /** A blank-but-valid starting config (prefix "00", not yet connected). */
        fun default(): DeviceConfig = DeviceConfig(
            devicePrefix = DEFAULT_PREFIX,
            endpointUrl = "",
            sharedSecret = "",
            autoExportSeconds = DEFAULT_INTERVAL_SECONDS,
            deviceSecret = "",
        )
    }
}
