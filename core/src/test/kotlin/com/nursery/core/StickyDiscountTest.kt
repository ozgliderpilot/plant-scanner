package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class StickyDiscountTest {

    @Test fun `new receipt defaults to zero`() {
        assertEquals(0, StickyDiscount().pct)
    }

    @Test fun `after commit next default is that discount`() {
        assertEquals(25, StickyDiscount().afterCommit(25).pct)
    }

    @Test fun `after edit-commit sticky updates to the new discount`() {
        val afterAdd = StickyDiscount().afterCommit(10)
        assertEquals(40, afterAdd.afterCommit(40).pct)
    }

    @Test fun `reset returns next default to zero`() {
        assertEquals(0, StickyDiscount().afterCommit(50).reset().pct)
    }
}
