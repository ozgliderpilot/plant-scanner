package com.nursery.core

/**
 * Receipt numbers are purely local, namespaced by a per-device 2-digit prefix (decision #11):
 * `PP-NNN`, e.g. prefix "07" + sequence 241 -> "07-241". Prefixes keep numbers unique across
 * devices in the shared Sheet without any central coordination.
 */
class ReceiptNumbering(val prefix: String) {

    init {
        require(isValidPrefix(prefix)) { "device prefix must be exactly two digits, was '$prefix'" }
    }

    fun format(seq: Int): String {
        require(seq >= 0) { "sequence must be >= 0, was $seq" }
        return "$prefix-$seq"
    }

    fun next(current: Int): Int = current + 1

    companion object {
        private val PREFIX = Regex("\\d{2}")
        fun isValidPrefix(value: String): Boolean = PREFIX.matches(value)
    }
}
