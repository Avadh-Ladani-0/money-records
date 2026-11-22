package com.moneyrecords.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.moneyrecords.app.notify.BudgetPromptNotifier
import com.moneyrecords.app.ui.navigation.MoneyRecordsRoot
import com.moneyrecords.app.ui.theme.MoneyRecordsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var budgetPrompt: BudgetPromptNotifier

    /** Requests from notifications to open a tab (e.g. "✗ Edit" → Budgets). */
    private val openBudgets = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            MoneyRecordsTheme {
                MoneyRecordsRoot(openBudgetsRequests = openBudgets.receiveAsFlow())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == BudgetPromptNotifier.ACTION_OPEN_BUDGETS) {
            budgetPrompt.dismiss()
            openBudgets.trySend(Unit)
        }
    }
}
