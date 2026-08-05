package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MagicLinkTest {

    @Test
    fun `parse round-trips build`() {
        val uri = MagicLink.build(
            prefix = "07",
            endpointUrl = "https://script.google.com/macros/s/ABC/exec",
            accessCode = "nursery-secret",
        )
        val parsed = MagicLink.parse(uri)!!
        assertEquals("07", parsed.devicePrefix)
        assertEquals("https://script.google.com/macros/s/ABC/exec", parsed.endpointUrl)
        assertEquals("nursery-secret", parsed.accessCode)
    }

    @Test
    fun `parse decodes urlencoded query values`() {
        val uri = "plantscanner://setup?prefix=12&url=https%3A%2F%2Fexample.com%2Fexec&code=a%2Bb"
        val parsed = MagicLink.parse(uri)!!
        assertEquals("12", parsed.devicePrefix)
        assertEquals("https://example.com/exec", parsed.endpointUrl)
        assertEquals("a+b", parsed.accessCode)
    }

    @Test
    fun `parse rejects wrong scheme host or bad prefix`() {
        assertNull(MagicLink.parse("https://setup?prefix=07&url=https://x&code=y"))
        assertNull(MagicLink.parse("plantscanner://claim?prefix=07&url=https://x&code=y"))
        assertNull(MagicLink.parse("plantscanner://setup?prefix=7&url=https://x&code=y"))
        assertNull(MagicLink.parse("plantscanner://setup?prefix=07&url=&code=y"))
        assertNull(MagicLink.parse("plantscanner://setup?prefix=07&url=https://x&code="))
    }
}
