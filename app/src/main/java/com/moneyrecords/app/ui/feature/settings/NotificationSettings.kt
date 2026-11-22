package com.moneyrecords.app.ui.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.repository.NotificationPrefs
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.rememberNotificationPermissionRequest
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space
import com.moneyrecords.app.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val prefs: UserPrefsRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val state: StateFlow<NotificationPrefs> = prefs.observeNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationPrefs())

    fun update(change: (NotificationPrefs) -> NotificationPrefs) {
        viewModelScope.launch {
            prefs.updateNotifications(change)
            scheduler.apply(prefs.notifications())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val n by viewModel.state.collectAsStateWithLifecycle()
    val askPermission = rememberNotificationPermissionRequest()
    var pickTime by remember { mutableStateOf(false) }
    fun set(change: (NotificationPrefs) -> NotificationPrefs, turningOn: Boolean) {
        if (turningOn) askPermission()
        viewModel.update(change)
    }
    val time = LocalTime.of(n.reminderHour, n.reminderMinute).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg)) {
        item { ScreenHeader(title = "Notifications", onBack = onBack) }
        item { SectionLabel("Recurring") }
        item {
            Toggle("Recurring records due", "Add, Skip or Edit when an item is due", Icons.Outlined.EventRepeat, n.recurringDue) { on ->
                set({ it.copy(recurringDue = on) }, on)
            }
        }
        item {
            Toggle("Upcoming bills", "The day before a recurring expense is due", Icons.Outlined.CalendarMonth, n.upcomingBills) { on ->
                set({ it.copy(upcomingBills = on) }, on)
            }
        }
        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Reminders") }
        item {
            Toggle("Daily reminder", "Log today's spending — skipped if you already did", Icons.Outlined.EditNote, n.dailyReminder) { on ->
                set({ it.copy(dailyReminder = on) }, on)
            }
        }
        if (n.dailyReminder) item {
            ListRow(title = "Reminder time", subtitle = time, icon = Icons.Outlined.Schedule, onClick = { pickTime = true })
        }
        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Budgets") }
        item {
            Toggle("80% and over-budget alerts", "Once per budget per month", Icons.Outlined.NotificationsActive, n.budgetAlerts) { on ->
                set({ it.copy(budgetAlerts = on) }, on)
            }
        }
        item {
            Toggle("Month-start check", "Keep last month's budgets? on the 1st", Icons.Outlined.PieChart, n.monthStartBudgets) { on ->
                set({ it.copy(monthStartBudgets = on) }, on)
            }
        }
        item { Spacer(Modifier.height(Space.xxl)) }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = n.reminderHour, initialMinute = n.reminderMinute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.update { it.copy(reminderHour = state.hour, reminderMinute = state.minute) }
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(
        title = title, subtitle = subtitle, icon = icon,
        onClick = { onChange(!checked) },
        trailing = {
            Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = MrTheme.colors.accent))
        },
    )
}
