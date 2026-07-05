package com.nursery.core

/** How the customer intends to pay. Recorded on the receipt; the app does not process payments. */
enum class PaymentMethod(val exportLabel: String, val displayLabel: String) {
    CARD("card", "Card"),
    CASH("cash", "Cash"),
}
