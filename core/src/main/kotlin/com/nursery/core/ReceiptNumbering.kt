package com.nursery.core

/**
 * Receipt numbers are purely local yet globally unique without any central coordination:
 * `PP-<epochSeconds>-<seq>`, e.g. prefix "07" created at 1718000000 as the day's first sale -> "07-1718000000-1".
 *
 *  - `PP`           : per-device 2-digit prefix (decision #11) — namespaces devices in the shared Sheet.
 *  - `epochSeconds` : receipt creation time (UTC). It survives app reinstall / "clear data", so a reset
 *                     [seq] can never collide with an already-exported number on a fresh install.
 *  - `seq`          : a per-device sequence that resets each new day (see [nextDailySeq]).
 *
 * Uniqueness holds because within any single second [seq] is strictly increasing — a day boundary is
 * also a second boundary, so the daily reset never lands mid-second.
 */
class ReceiptNumbering(val prefix: String) {

    init {
        require(isValidPrefix(prefix)) { "device prefix must be exactly two digits, was '$prefix'" }
    }

    fun format(epochSeconds: Long, seq: Int): String {
        require(epochSeconds >= 0) { "epochSeconds must be >= 0, was $epochSeconds" }
        require(seq >= 0) { "sequence must be >= 0, was $seq" }
        return "$prefix-$epochSeconds-$seq"
    }

    companion object {
        private val PREFIX = Regex("\\d{2}")
        fun isValidPrefix(value: String): Boolean = PREFIX.matches(value)

        /**
         * Next per-device sequence with a daily reset: 1 on the first receipt of [todayEpochDay]
         * (or the first ever), otherwise [lastSeq] + 1. [lastDayEpochDay] is the day the last receipt
         * was issued (null when none yet); days are epoch-days in the device's local zone.
         */
        fun nextDailySeq(lastDayEpochDay: Long?, lastSeq: Int, todayEpochDay: Long): Int =
            if (lastDayEpochDay == todayEpochDay) lastSeq + 1 else 1
    }
}
