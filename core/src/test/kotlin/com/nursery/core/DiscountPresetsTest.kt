package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscountPresetsTest {
    @Test fun `quick picks are ten fifteen and twenty percent`() {
        assertEquals(listOf(10, 15, 20), DiscountPresets.pcts)
    }

    @Test fun `every quick pick is a valid discount percent`() {
        assertTrue(DiscountPresets.pcts.all { it in 0..100 })
    }
}
