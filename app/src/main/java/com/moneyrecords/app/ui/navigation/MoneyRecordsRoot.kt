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
import androidx.compose.material3.SnackbarResult
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.moneyrecords.app.ui.feature.accounts.AccountEditorScreen
import com.moneyrecords.app.ui.feature.accounts.AccountsScreen
import com.moneyrecords.app.ui.feature.budgets.BudgetsScreen
import com.moneyrecords.app.ui.feature.designsystem.DesignSystemScreen
import com.moneyrecords.app.ui.feature.categories.CategoriesScreen
import com.moneyrecords.app.ui.feature.categories.CategoryEditorScreen
import com.moneyrecords.app.ui.feature.home.HomeScreen
import com.moneyrecords.app.ui.components.QuickCalculatorOverlay
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.fillMaxSize
import com.moneyrecords.app.ui.feature.record.RecordEditorScreen
import com.moneyrecords.app.ui.feature.records.RecordsScreen
import com.moneyrecords.app.ui.feature.settings.SettingsScreen
import com.moneyrecords.app.ui.feature.settings.NotificationSettingsScreen
import com.moneyrecords.app.ui.feature.recurring.RecurringEditorScreen
import com.moneyrecords.app.ui.feature.recurring.RecurringScreen
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private enum class Tab(val route: Any, val routeClass: KClass<*>, val item: BottomBarItem) {
    Home(HomeRoute, HomeRoute::class, BottomBarItem("Home", Icons.Outlined.Home)),
    Records(RecordsRoute, RecordsRoute::class, BottomBarItem("Records", Icons.AutoMirrored.Outlined.ReceiptLong)),
    Accounts(AccountsRoute, AccountsRoute::class, BottomBarItem("Accounts", Icons.Outlined.AccountBalanceWallet)),
    Budgets(BudgetsRoute, BudgetsRoute::class, BottomBarItem("Budgets", Icons.Outlined.PieChart)),
}

private fun NavDestination?.isOn(routeClass: KClass<*>) =
    this?.hierarchy?.any { it.hasRoute(routeClass) } == true

@Composable
fun MoneyRecordsRoot(
    requests: Flow<AppRequest> = emptyFlow(),
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selectedTab = Tab.entries.indexOfFirst { destination.isOn(it.routeClass) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val calculator by appViewModel.calculator.collectAsStateWithLifecycle()
    // Notification "✗ Edit" → Budgets tab (for the current month).
    LaunchedEffect(requests) {
        requests.collect { request ->
            when (request) {
                AppRequest.OpenBudgets -> navController.navigate(BudgetsRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
                AppRequest.AddRecord -> navController.navigate(RecordEditorRoute())
                is AppRequest.EditDue -> navController.navigate(RecordEditorRoute(fromRuleId = request.ruleId))
            }
        }
    }
    // Bumped on every scroll anywhere in the app; the calculator tab hides while it keeps changing.
    val scrollSignal = remember { mutableIntStateOf(0) }
    val scrollWatcher = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) scrollSignal.intValue++
                return Offset.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize().nestedScroll(scrollWatcher)) {
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
                        onClick = { navController.navigate(RecordEditorRoute()) },
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
                val openRecord: (Long) -> Unit = { id -> navController.navigate(RecordEditorRoute(recordId = id)) }
                val duplicateRecord: (Long) -> Unit = { id -> navController.navigate(RecordEditorRoute(duplicateOf = id)) }
                composable<HomeRoute> {
                    HomeScreen(
                        onOpenSettings = openSettings,
                        onOpenRecord = openRecord,
                        onDuplicateRecord = duplicateRecord,
                        onSeeAllRecords = {
                            navController.navigate(RecordsRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onAddAccount = { navController.navigate(AccountEditorRoute()) },
                        onEditDue = { id -> navController.navigate(RecordEditorRoute(fromRuleId = id)) },
                        onOpenBudgets = {
                            navController.navigate(BudgetsRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable<RecordsRoute> {
                    RecordsScreen(onOpenSettings = openSettings, onOpenRecord = openRecord, onDuplicateRecord = duplicateRecord)
                }
                val showMessage: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
                composable<AccountsRoute> {
                    AccountsScreen(
                        onOpenSettings = openSettings,
                        onAddAccount = { navController.navigate(AccountEditorRoute()) },
                        onOpenAccount = { id -> navController.navigate(AccountEditorRoute(accountId = id)) },
                    )
                }
                composable<RecordEditorRoute> {
                    RecordEditorScreen(
                        onClose = { navController.popBackStack() },
                        onDeleted = { record ->
                            scope.launch {
                                val result = snackbar.showSnackbar("Record deleted", actionLabel = "Undo", withDismissAction = true)
                                if (result == SnackbarResult.ActionPerformed) appViewModel.restore(record)
                            }
                        },
                        onAddAccount = { navController.navigate(AccountEditorRoute()) },
                        onOpenRecurring = { id -> navController.navigate(RecurringEditorRoute(id)) },
                    )
                }
                composable<AccountEditorRoute> {
                    AccountEditorScreen(onClose = { navController.popBackStack() }, onMessage = showMessage)
                }
                composable<BudgetsRoute> { BudgetsScreen(onOpenSettings = openSettings) }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDesignSystem = { navController.navigate(DesignSystemRoute) },
                        onOpenCategories = { navController.navigate(CategoriesRoute) },
                        onOpenRecurring = { navController.navigate(RecurringRoute) },
                        onOpenNotifications = { navController.navigate(NotificationSettingsRoute) },
                    )
                }
                composable<CategoriesRoute> {
                    CategoriesScreen(
                        onBack = { navController.popBackStack() },
                        onAdd = { kind -> navController.navigate(CategoryEditorRoute(kind = kind.name)) },
                        onOpen = { id -> navController.navigate(CategoryEditorRoute(categoryId = id)) },
                        onMessage = showMessage,
                    )
                }
                composable<CategoryEditorRoute> {
                    CategoryEditorScreen(onClose = { navController.popBackStack() }, onMessage = showMessage)
                }
                composable<RecurringRoute> {
                    RecurringScreen(onBack = { navController.popBackStack() }, onOpen = { id -> navController.navigate(RecurringEditorRoute(id)) })
                }
                composable<RecurringEditorRoute> { RecurringEditorScreen(onClose = { navController.popBackStack() }) }
                composable<NotificationSettingsRoute> { NotificationSettingsScreen(onBack = { navController.popBackStack() }) }
                composable<DesignSystemRoute> {
                    DesignSystemScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        calculator?.takeIf { it.enabled }?.let { prefs ->
            QuickCalculatorOverlay(prefs = prefs, scrollSignal = scrollSignal, onMoved = appViewModel::moveCalculator)
        }
    }
}
