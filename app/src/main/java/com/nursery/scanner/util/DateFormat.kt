package com.nursery.scanner.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Built per call (not cached at class-load) so a mid-session device locale change is honoured.
fun formatDateTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMs).atZone(zone))

/** Calendar date for day-total footers, e.g. "3 July 2026". */
fun formatEpochDay(epochDay: Long): String =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
        .format(java.time.LocalDate.ofEpochDay(epochDay))
