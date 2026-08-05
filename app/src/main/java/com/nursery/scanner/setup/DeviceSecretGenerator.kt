package com.nursery.scanner.setup

import com.nursery.core.DeviceSecret
import java.security.SecureRandom

/** Generate a fresh per-device secret for Users-tab claim (ADR-0017). */
fun generateDeviceSecret(random: SecureRandom = SecureRandom()): String {
    val bytes = ByteArray(DeviceSecret.BYTE_LENGTH)
    random.nextBytes(bytes)
    return DeviceSecret.fromBytes(bytes)
}
