package com.moneyrecords.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.navigation.NavGraphBuilder
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.mutableIntStateOf
import com.moneyrecords.app.ui.feature.settings.AppearanceScreen
import com.moneyrecords.app.ui.feature.settings.BackupScreen
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
import com.moneyrecords.app.ui.feature.lock.LockScreen
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.moneyrecords.app.ui.components.QuickCalculatorOverlay
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.fillMaxSize
import com.moneyrecords.app.ui.feature.record.RecordEditorScreen
import com.moneyrecords.app.ui.feature.records.RecordsScreen
import com.moneyrecords.app.ui.feature.settings.SettingsScreen
import com.moneyrecords.app.ui.feature.settings.NotificationSettingsScreen
import com.moneyrecords.app.ui.feature.settings.AppLockSettingsScreen
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

/**
 * A destination drawn on a solid background, so a screen sliding over another during a
 * transition (or a swipe-back) is never see-through.
 */
private inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable () -> Unit) {
    composable<T> {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/** How a screen arrives: tabs crossfade, editors rise from the bottom, settings pages slide in. */
private enum class Motion { TAB, EDITOR, PAGE }

private val editorRoutes = listOf(
    RecordEditorRoute::class, AccountEditorRoute::class, CategoryEditorRoute::class, RecurringEditorRoute::class,
)

private fun NavDestination.motion(): Motion = when {
    Tab.entries.any { isOn(it.routeClass) } -> Motion.TAB
    editorRoutes.any { isOn(it) } -> Motion.EDITOR
    else -> Motion.PAGE
}

private const val ENTER_MS = 220
private const val EXIT_MS = 200
/** How long a leaving screen stays visible: short, so it never lingers under the new one. */
private const val OUT_MS = 90
private const val POP_MS = 250

@Composable
fun MoneyRecordsRoot(
    requests: Flow<AppRequest> = emptyFlow(),
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selectedTab = Tab.entries.indexOfFirst { destination.isOn(it.routeClass) }
    // Keeps the bar showing the right tab while it slides away under an editor.
    val lastTab = remember { mutableIntStateOf(0) }
    if (selectedTab >= 0) lastTab.intValue = selectedTab
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val calculator by appViewModel.calculator.collectAsStateWithLifecycle()
    val locked by appViewModel.appLock.locked.collectAsStateWithLifecycle()
    // Notification "✗ Edit" → Budgets tab (for the current month).
    LaunchedEffect(requests) {
        requests.collect { request ->
            when (request) {
                AppRequest.OpenBudgets -> navController.navigate(BudgetsRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
                is AppRequest.AddRecord -> navController.navigate(RecordEditorRoute(kind = request.kind.orEmpty()))
                AppRequest.OpenBackup -> navController.navigate(BackupRoute) { launchSingleTop = true }
                AppRequest.OpenHome -> navController.navigate(HomeRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                    launchSingleTop = true
                }
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
        // While locked, hide the content underneath from accessibility services too.
        Box(if (locked) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                AnimatedVisibility(visible = selectedTab >= 0, enter = EnterTransition.None, exit = ExitTransition.None) {
                    MrBottomBar(
                        items = Tab.entries.map { it.item },
                        selectedIndex = lastTab.intValue,
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
                AnimatedVisibility(
                    visible = destination.isOn(HomeRoute::class) || destination.isOn(RecordsRoute::class),
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                ) {
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
                // Instant navigation both ways, as the user asked: no slide, fade or scale.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                val openSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } }
                val openRecord: (Long) -> Unit = { id -> navController.navigate(RecordEditorRoute(recordId = id)) }
                val duplicateRecord: (Long) -> Unit = { id -> navController.navigate(RecordEditorRoute(duplicateOf = id)) }
                screen<HomeRoute> {
                    HomeScreen(
                        onOpenSettings = openSettings,
                        onOpenRecurring = { navController.navigate(RecurringRoute) },
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
                screen<RecordsRoute> {
                    RecordsScreen(onOpenSettings = openSettings, onOpenRecord = openRecord, onDuplicateRecord = duplicateRecord)
                }
                val showMessage: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
                screen<AccountsRoute> {
                    AccountsScreen(
                        onOpenSettings = openSettings,
                        onAddAccount = { navController.navigate(AccountEditorRoute()) },
                        onOpenAccount = { id -> navController.navigate(AccountEditorRoute(accountId = id)) },
                    )
                }
                screen<RecordEditorRoute> {
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
                screen<AccountEditorRoute> {
                    AccountEditorScreen(onClose = { navController.popBackStack() }, onMessage = showMessage)
                }
                screen<BudgetsRoute> { BudgetsScreen(onOpenSettings = openSettings) }
                screen<SettingsRoute> {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDesignSystem = { navController.navigate(DesignSystemRoute) },
                        onOpenCategories = { navController.navigate(CategoriesRoute) },
                        onOpenRecurring = { navController.navigate(RecurringRoute) },
                        onOpenNotifications = { navController.navigate(NotificationSettingsRoute) },
                        onOpenAppLock = { navController.navigate(AppLockSettingsRoute) },
                        onOpenAppearance = { navController.navigate(AppearanceRoute) },
                        onOpenBackup = { navController.navigate(BackupRoute) },
                    )
                }
                screen<AppearanceRoute> { AppearanceScreen(onBack = { navController.popBackStack() }) }
                screen<BackupRoute> { BackupScreen(onBack = { navController.popBackStack() }, onMessage = showMessage) }
                screen<CategoriesRoute> {
                    CategoriesScreen(
                        onBack = { navController.popBackStack() },
                        onAdd = { kind -> navController.navigate(CategoryEditorRoute(kind = kind.name)) },
                        onOpen = { id -> navController.navigate(CategoryEditorRoute(categoryId = id)) },
                        onMessage = showMessage,
                    )
                }
                screen<CategoryEditorRoute> {
                    CategoryEditorScreen(onClose = { navController.popBackStack() }, onMessage = showMessage)
                }
                screen<RecurringRoute> {
                    RecurringScreen(onBack = { navController.popBackStack() }, onOpen = { id -> navController.navigate(RecurringEditorRoute(id)) })
                }
                screen<RecurringEditorRoute> { RecurringEditorScreen(onClose = { navController.popBackStack() }) }
                screen<AppLockSettingsRoute> { AppLockSettingsScreen(onBack = { navController.popBackStack() }) }
                screen<NotificationSettingsRoute> { NotificationSettingsScreen(onBack = { navController.popBackStack() }) }
                screen<DesignSystemRoute> {
                    DesignSystemScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        }
        // On every screen, the record editor included (it slides away while the keyboard is open).
        calculator?.takeIf { it.enabled && !locked }?.let { prefs ->
            QuickCalculatorOverlay(prefs = prefs, scrollSignal = scrollSignal, onMoved = appViewModel::moveCalculator)
        }
        if (locked) LockScreen(appViewModel.appLock)
    }
}
