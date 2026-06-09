package com.nursery.scanner.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nursery.scanner.di.AppContainer
import com.nursery.scanner.ui.components.NurseryBottomBar
import com.nursery.scanner.ui.components.NurseryTopBar
import com.nursery.scanner.ui.home.HomeScreen
import com.nursery.scanner.ui.nav.Routes
import com.nursery.scanner.ui.nav.TabRoutes
import com.nursery.scanner.ui.receipts.ReceiptDetailScreen
import com.nursery.scanner.ui.receipts.ReceiptsScreen
import com.nursery.scanner.ui.receipts.ReceiptsViewModel
import com.nursery.scanner.ui.sell.CartScreen
import com.nursery.scanner.ui.sell.ConfirmScreen
import com.nursery.scanner.ui.sell.LineItemScreen
import com.nursery.scanner.ui.sell.ScanScreen
import com.nursery.scanner.ui.sell.SellViewModel
import com.nursery.scanner.ui.settings.SettingsScreen
import com.nursery.scanner.ui.settings.SettingsViewModel
import com.nursery.scanner.ui.sync.SyncScreen
import com.nursery.scanner.ui.sync.SyncViewModel
import com.nursery.scanner.ui.theme.NurseryTheme

@Composable
fun NurseryRoot(container: AppContainer) {
    NurseryTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val route = backStackEntry?.destination?.route
        val showBars = route in TabRoutes
        val syncState by container.syncRepository.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = {
                if (showBars) NurseryTopBar(titleFor(route), syncState, System.currentTimeMillis())
            },
            bottomBar = {
                if (showBars) {
                    NurseryBottomBar(currentRoute = route) { dest ->
                        navController.navigate(dest) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            },
        ) { padding ->
            NurseryNavHost(navController, container, Modifier.padding(padding))
        }
    }
}

private fun titleFor(route: String?): String = when (route) {
    Routes.RECEIPTS -> "Receipts"
    Routes.SYNC -> "Sync"
    else -> "Nursery"
}

@Composable
private fun NurseryNavHost(
    navController: NavHostController,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = Routes.ACTIONS, modifier = modifier) {

        composable(Routes.ACTIONS) {
            HomeScreen(onSell = { navController.navigate(Routes.SELL_GRAPH) })
        }

        composable(Routes.RECEIPTS) {
            val vm: ReceiptsViewModel = viewModel(factory = container.viewModelFactory)
            ReceiptsScreen(vm, onOpen = { id -> navController.navigate(Routes.receiptDetail(id)) })
        }

        composable(
            Routes.RECEIPT_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val vm: ReceiptsViewModel = viewModel(factory = container.viewModelFactory)
            ReceiptDetailScreen(vm, receiptId = id, onBack = { navController.popBackStack() })
        }

        composable(Routes.SYNC) {
            val vm: SyncViewModel = viewModel(factory = container.viewModelFactory)
            SyncScreen(vm, onSettings = { navController.navigate(Routes.SETTINGS) })
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = container.viewModelFactory)
            SettingsScreen(vm, onBack = { navController.popBackStack() })
        }

        // Sell flow — nested graph so one SellViewModel is shared across its screens.
        navigation(startDestination = Routes.SELL_SCAN, route = Routes.SELL_GRAPH) {
            composable(Routes.SELL_SCAN) { entry ->
                val vm = sellViewModel(navController, container, entry)
                ScanScreen(
                    vm = vm,
                    onResolved = { navController.navigate(Routes.SELL_LINE) },
                    onClose = { navController.popBackStack(Routes.ACTIONS, inclusive = false) },
                )
            }
            composable(Routes.SELL_LINE) { entry ->
                val vm = sellViewModel(navController, container, entry)
                LineItemScreen(
                    vm = vm,
                    onAdded = {
                        navController.navigate(Routes.SELL_CART) {
                            popUpTo(Routes.SELL_SCAN) { inclusive = false }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SELL_CART) { entry ->
                val vm = sellViewModel(navController, container, entry)
                CartScreen(
                    vm = vm,
                    onScanAnother = {
                        navController.navigate(Routes.SELL_SCAN) {
                            popUpTo(Routes.SELL_CART) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onEditLine = { navController.navigate(Routes.SELL_LINE) },
                    onSaved = { navController.navigate(Routes.SELL_CONFIRM) },
                    onBack = { navController.popBackStack(Routes.ACTIONS, inclusive = false) },
                )
            }
            composable(Routes.SELL_CONFIRM) { entry ->
                val vm = sellViewModel(navController, container, entry)
                val syncState by container.syncRepository.state.collectAsStateWithLifecycle()
                ConfirmScreen(
                    vm = vm,
                    pendingCount = syncState.pendingCount,
                    onNewSale = {
                        // Navigate first, THEN reset: nulling `saved` while Confirm is still composed
                        // would otherwise let its empty-state guard fire onDone and bounce to Home.
                        navController.navigate(Routes.SELL_SCAN) {
                            popUpTo(Routes.SELL_GRAPH) { inclusive = false }
                            launchSingleTop = true
                        }
                        vm.reset()
                    },
                    onDone = {
                        navController.popBackStack(Routes.ACTIONS, inclusive = false)
                        vm.reset()
                    },
                )
            }
        }
    }
}

/** Shared SellViewModel scoped to the sell nav graph back-stack entry. */
@Composable
private fun sellViewModel(
    navController: NavHostController,
    container: AppContainer,
    entry: NavBackStackEntry,
): SellViewModel {
    val parentEntry = androidx.compose.runtime.remember(entry) {
        navController.getBackStackEntry(Routes.SELL_GRAPH)
    }
    return viewModel(viewModelStoreOwner = parentEntry, factory = container.viewModelFactory)
}
