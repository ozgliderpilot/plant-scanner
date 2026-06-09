package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceConfigTest {

    @Test fun `complete config is connectable`() {
        val cfg = DeviceConfig("07", "https://script.google.com/exec", "s3cr3t", 60)
        assertTrue(cfg.isComplete)
    }

    @Test fun `blank url or secret means not complete`() {
        assertFalse(DeviceConfig("07", "", "s3cr3t", 60).isComplete)
        assertFalse(DeviceConfig("07", "https://x", "", 60).isComplete)
    }

    @Test fun `bad prefix throws`() {
        assertFailsWith<IllegalArgumentException> { DeviceConfig("7", "https://x", "s", 60) }
    }

    @Test fun `interval below minimum throws`() {
        assertFailsWith<IllegalArgumentException> { DeviceConfig("07", "https://x", "s", 5) }
    }

    @Test fun `default is valid but not complete`() {
        val d = DeviceConfig.default()
        assertEquals("00", d.devicePrefix)
        assertEquals(60, d.autoExportSeconds)
        assertFalse(d.isComplete)
    }
}
