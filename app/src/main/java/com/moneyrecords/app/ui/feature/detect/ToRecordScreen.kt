package com.moneyrecords.app.ui.feature.detect

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.detect.PaymentApps
import com.moneyrecords.app.domain.detect.DetectSource
import com.moneyrecords.app.domain.detect.DetectStatus
import com.moneyrecords.app.domain.detect.Detected
import com.moneyrecords.app.domain.detect.DetectedPrompts
import com.moneyrecords.app.domain.detect.DetectionRepository
import com.moneyrecords.app.domain.detect.DetectionText
import com.moneyrecords.app.domain.detect.Direction
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.ui.components.EmptyState
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.rememberHaptics
import com.moneyrecords.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ToRecordItem(val item: Detected, val title: String)

@HiltViewModel
class ToRecordViewModel @Inject constructor(
    private val detection: DetectionRepository,
    private val prompts: DetectedPrompts,
    accounts: AccountRepository,
) : ViewModel() {
    val items: StateFlow<List<ToRecordItem>?> = combine(detection.observeOpen(), accounts.observeAccounts()) { open, list ->
        val names = list.associate { it.id to it.name }
        open.map { ToRecordItem(it, DetectionText.title(it, names)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun ignore(id: Long) {
        viewModelScope.launch {
            detection.setStatus(id, DetectStatus.IGNORED)
            prompts.cancel(id)
            prompts.refreshSummary()
        }
    }
}

private val whenFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.getDefault())

/** Everything caught but not recorded yet: Add (opens the editor pre-filled) or Ignore. */
@Composable
fun ToRecordScreen(onBack: () -> Unit, onAdd: (Long) -> Unit, viewModel: ToRecordViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg)) {
        item { ScreenHeader(title = "To record", subtitle = "Caught from bank SMS, payment apps and pay widgets", onBack = onBack) }
        val list = items ?: return@LazyColumn
        if (list.isEmpty()) {
            item { EmptyState(title = "All recorded", message = "Payments MoneyRecords catches show up here until you add or ignore them.") }
        }
        items(list, key = { it.item.id }) { (d, title) ->
            val source = when (d.source) {
                DetectSource.SMS -> "Bank SMS"
                DetectSource.NOTIFICATION -> PaymentApps.nameOf(d.origin) ?: "Payment app"
                DetectSource.PAY_WIDGET -> "Pay widget"
            }
            ListRow(
                title = title,
                subtitle = "$source · " + Instant.ofEpochMilli(d.occurredAt).atZone(ZoneId.systemDefault()).format(whenFormat),
                icon = when {
                    d.isTransfer -> Icons.AutoMirrored.Outlined.CompareArrows
                    d.direction == Direction.OUT -> Icons.AutoMirrored.Outlined.CallMade
                    else -> Icons.AutoMirrored.Outlined.CallReceived
                },
                onClick = { onAdd(d.id) },
                modifier = Modifier.animateItem(),
                trailing = {
                    Row {
                        TextButton(onClick = { haptics.tick(); viewModel.ignore(d.id) }) {
                            Text("Ignore", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onAdd(d.id) }) { Text("Add", color = MaterialTheme.colorScheme.onBackground) }
                    }
                },
            )
        }
        item { Spacer(Modifier.height(Space.xxl)) }
    }
}
