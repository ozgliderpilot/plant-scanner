package com.nursery.scanner.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val ACTIONS = "actions"
    const val HISTORY = "history"
    const val PLANTS = "plants"
    const val SETTINGS = "settings"
    const val RECEIPTS = "receipts"

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
    TabItem(Routes.HISTORY, "History", Icons.Filled.History),
    TabItem(Routes.PLANTS, "Plants", Icons.Filled.LocalFlorist),
)

/** Routes that show the bottom tabs (full-screen sub-flows hide them). Each tab owns its header. */
val TabRoutes = setOf(Routes.ACTIONS, Routes.HISTORY, Routes.PLANTS)
