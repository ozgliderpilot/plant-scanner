package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentMethodTest {

    @Test fun `export labels are lowercase sheet values`() {
        assertEquals("card", PaymentMethod.CARD.exportLabel)
        assertEquals("cash", PaymentMethod.CASH.exportLabel)
    }

    @Test fun `display labels are volunteer-facing`() {
        assertEquals("Card", PaymentMethod.CARD.displayLabel)
        assertEquals("Cash", PaymentMethod.CASH.displayLabel)
    }
}
