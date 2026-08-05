package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeviceSecretTest {

    @Test
    fun `fromBytes encodes 32 bytes as 64 hex chars`() {
        val bytes = ByteArray(32) { i -> i.toByte() }
        val hex = DeviceSecret.fromBytes(bytes)
        assertEquals(64, hex.length)
        assertEquals("000102030405060708090a0b0c0d0e0f", hex.take(32))
    }

    @Test
    fun `fromBytes rejects wrong length`() {
        assertFailsWith<IllegalArgumentException> { DeviceSecret.fromBytes(ByteArray(16)) }
    }
}
