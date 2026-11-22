package com.moneyrecords.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moneyrecords.app.ui.components.AccentFab
import com.moneyrecords.app.ui.components.BottomBarItem
import com.moneyrecords.app.ui.components.MrBottomBar
import com.moneyrecords.app.ui.feature.accounts.AccountsScreen
import com.moneyrecords.app.ui.feature.budgets.BudgetsScreen
import com.moneyrecords.app.ui.feature.designsystem.DesignSystemScreen
import com.moneyrecords.app.ui.feature.home.HomeScreen
import com.moneyrecords.app.ui.feature.records.RecordsScreen
import com.moneyrecords.app.ui.feature.settings.SettingsScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object HomeRoute
@Serializable data object RecordsRoute
@Serializable data object AccountsRoute
@Serializable data object BudgetsRoute
@Serializable data object SettingsRoute
@Serializable data object DesignSystemRoute

private enum class Tab(val route: Any, val routeClass: KClass<*>, val item: BottomBarItem) {
    Home(HomeRoute, HomeRoute::class, BottomBarItem("Home", Icons.Outlined.Home)),
    Records(RecordsRoute, RecordsRoute::class, BottomBarItem("Records", Icons.AutoMirrored.Outlined.ReceiptLong)),
    Accounts(AccountsRoute, AccountsRoute::class, BottomBarItem("Accounts", Icons.Outlined.AccountBalanceWallet)),
    Budgets(BudgetsRoute, BudgetsRoute::class, BottomBarItem("Budgets", Icons.Outlined.PieChart)),
}

private fun NavDestination?.isOn(routeClass: KClass<*>) =
    this?.hierarchy?.any { it.hasRoute(routeClass) } == true

@Composable
fun MoneyRecordsRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selectedTab = Tab.entries.indexOfFirst { destination.isOn(it.routeClass) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selectedTab >= 0) {
                MrBottomBar(
                    items = Tab.entries.map { it.item },
                    selectedIndex = selectedTab,
                    onSelect = { index ->
                        navController.navigate(Tab.entries[index].route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (destination.isOn(HomeRoute::class) || destination.isOn(RecordsRoute::class)) {
                AccentFab(
                    onClick = { scope.launch { snackbar.showSnackbar("Adding records arrives in the next build") } },
                    icon = Icons.Outlined.Add,
                    contentDescription = "Add record",
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            val openSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } }
            composable<HomeRoute> { HomeScreen(onOpenSettings = openSettings) }
            composable<RecordsRoute> { RecordsScreen(onOpenSettings = openSettings) }
            composable<AccountsRoute> { AccountsScreen(onOpenSettings = openSettings) }
            composable<BudgetsRoute> { BudgetsScreen(onOpenSettings = openSettings) }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDesignSystem = { navController.navigate(DesignSystemRoute) },
                )
            }
            composable<DesignSystemRoute> {
                DesignSystemScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
