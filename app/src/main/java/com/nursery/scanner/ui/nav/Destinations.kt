package com.nursery.scanner.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val ACTIONS = "actions"
    const val RECEIPTS = "receipts"
    const val SYNC = "sync"
    const val SETTINGS = "settings"
    const val PLANTS = "plants"

    // Sell flow lives in a nested graph so one SellViewModel is shared across its screens.
    const val SELL_GRAPH = "sell"
    const val SELL_SCAN = "sell/scan"
    const val SELL_LINE = "sell/lineitem"
    const val SELL_CART = "sell/cart"
    const val SELL_CONFIRM = "sell/confirm"

    const val CULL_GRAPH = "cull"
    const val CULL_SCAN = "cull/scan"
    const val CULL_INFO = "cull/info"
    const val CULL_SUCCESS = "cull/success"
    const val CULLS = "culls"

    const val RECEIPT_DETAIL = "receipt/{id}"
    fun receiptDetail(id: Long) = "receipt/$id"
}

/** The three bottom-bar tabs. Icon + text label always shown (never icon-only). */
data class TabItem(val route: String, val label: String, val icon: ImageVector)

val BottomTabs = listOf(
    TabItem(Routes.ACTIONS, "Actions", Icons.Filled.GridView),
    TabItem(Routes.RECEIPTS, "Receipts", Icons.Filled.ReceiptLong),
    TabItem(Routes.SYNC, "Sync", Icons.Filled.Sync),
)

/** Routes that show the top status bar + bottom tabs (full-screen sub-flows hide them). */
val TabRoutes = setOf(Routes.ACTIONS, Routes.RECEIPTS, Routes.SYNC)
