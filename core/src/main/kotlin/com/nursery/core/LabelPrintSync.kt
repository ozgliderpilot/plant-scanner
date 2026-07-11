package com.nursery.core

/**
 * Label-print sync selection + status transitions. Mirrors [CullSync]: only PENDING requests are
 * exported; once EXPORTED they are excluded from every later push.
 */
object LabelPrintSync {

    fun pending(requests: List<LabelPrintRequest>): List<LabelPrintRequest> =
        requests.filter { it.status == LabelPrintStatus.PENDING }

    fun pendingCount(requests: List<LabelPrintRequest>): Int =
        requests.count { it.status == LabelPrintStatus.PENDING }

    fun markExported(request: LabelPrintRequest): LabelPrintRequest =
        request.copy(status = LabelPrintStatus.EXPORTED)
}
