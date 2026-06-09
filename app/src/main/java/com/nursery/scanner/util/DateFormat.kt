package com.nursery.scanner.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DTF = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())

fun formatDateTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DTF.format(Instant.ofEpochMilli(epochMs).atZone(zone))
