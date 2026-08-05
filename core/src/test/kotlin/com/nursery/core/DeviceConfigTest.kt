package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceConfigTest {

    @Test
    fun `complete when url access code and device secret are set`() {
        val cfg = DeviceConfig("07", "https://script.google.com/exec", "s3cr3t", 60, "devsecret")
        assertTrue(cfg.isComplete)
    }

    @Test
    fun `incomplete when url access code or device secret blank`() {
        assertFalse(DeviceConfig("07", "", "s3cr3t", 60, "dev").isComplete)
        assertFalse(DeviceConfig("07", "https://x", "", 60, "dev").isComplete)
        assertFalse(DeviceConfig("07", "https://x", "s3cr3t", 60, "").isComplete)
    }

    @Test
    fun `rejects bad prefix`() {
        assertFailsWith<IllegalArgumentException> { DeviceConfig("7", "https://x", "s", 60) }
    }

    @Test
    fun `rejects interval below minimum`() {
        assertFailsWith<IllegalArgumentException> { DeviceConfig("07", "https://x", "s", 5) }
    }

    @Test
    fun `default is incomplete prefix 00`() {
        val d = DeviceConfig.default()
        assertEquals("00", d.devicePrefix)
        assertEquals("", d.deviceSecret)
        assertFalse(d.isComplete)
    }
}
