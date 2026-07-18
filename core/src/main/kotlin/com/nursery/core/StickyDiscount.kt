package com.nursery.core

/**
 * Receipt-scoped default discount % for new sell lines: last committed discount on this receipt,
 * or 0 when nothing has been committed yet / after [reset].
 */
data class StickyDiscount(val pct: Int = 0) {

    /** Remember the discount just committed (add or edit); next new line defaults to this. */
    fun afterCommit(committedPct: Int): StickyDiscount = StickyDiscount(committedPct)

    /** Clear for a new sale. */
    fun reset(): StickyDiscount = StickyDiscount()
}
