package com.moneyrecords.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.moneyrecords.app.notify.BudgetPromptNotifier
import com.moneyrecords.app.notify.Notifications
import com.moneyrecords.app.ui.navigation.AppRequest
import com.moneyrecords.app.ui.navigation.MoneyRecordsRoot
import com.moneyrecords.app.ui.theme.MoneyRecordsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var budgetPrompt: BudgetPromptNotifier

    /** Requests from notifications (open Budgets, add a record, edit a due recurring item). */
    private val requests = Channel<AppRequest>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            MoneyRecordsTheme {
                MoneyRecordsRoot(requests = requests.receiveAsFlow())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            Notifications.ACTION_OPEN_BUDGETS -> {
                budgetPrompt.dismiss()
                requests.trySend(AppRequest.OpenBudgets)
            }
            Notifications.ACTION_ADD_RECORD -> requests.trySend(AppRequest.AddRecord)
            Notifications.ACTION_EDIT_DUE -> {
                val ruleId = intent.getLongExtra(Notifications.EXTRA_RULE_ID, 0L)
                if (ruleId > 0) {
                    Notifications.cancel(this, Notifications.dueId(ruleId))
                    requests.trySend(AppRequest.EditDue(ruleId))
                }
            }
        }
        // Consume the action so a rotation/recreate doesn't repeat it.
        intent?.action = null
    }
}
