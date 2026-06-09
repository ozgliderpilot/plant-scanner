package com.nursery.scanner.util

/** Coarse "x ago" label for the status chip. */
fun relativeTime(epochMs: Long?, now: Long): String {
    if (epochMs == null) return "never"
    val d = now - epochMs
    return when {
        d < 60_000 -> "just now"
        d < 3_600_000 -> "${d / 60_000}m ago"
        d < 86_400_000 -> "${d / 3_600_000}h ago"
        else -> "${d / 86_400_000}d ago"
    }
}
