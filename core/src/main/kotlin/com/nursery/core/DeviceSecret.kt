package com.nursery.core

/**
 * Format a cryptographically random byte array as a hex device secret for the Users-tab claim
 * (ADR-0017). Entropy is supplied by the caller (Android: SecureRandom) so this stays pure JVM.
 */
object DeviceSecret {
    const val BYTE_LENGTH = 32

    fun fromBytes(bytes: ByteArray): String {
        require(bytes.size == BYTE_LENGTH) {
            "device secret requires $BYTE_LENGTH bytes, was ${bytes.size}"
        }
        return bytes.joinToString("") { b ->
            val v = b.toInt() and 0xff
            "%02x".format(v)
        }
    }
}
