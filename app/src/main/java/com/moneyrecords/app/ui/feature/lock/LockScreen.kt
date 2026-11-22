package com.moneyrecords.app.ui.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.FragmentActivity
import com.moneyrecords.app.security.AppLockManager
import com.moneyrecords.app.ui.components.DotGlyph
import com.moneyrecords.app.ui.components.Glyphs
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.dotGrid
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

/**
 * Covers the whole app while locked (content stays underneath, so you return to where you were).
 * Opens the unlock prompt straight away; "Unlock" reopens it if it was dismissed.
 */
@Composable
fun LockScreen(lock: AppLockManager) {
    val activity = LocalContext.current as FragmentActivity
    LaunchedEffect(Unit) { lock.promptUnlock(activity) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .dotGrid(MrTheme.colors.dotGrid)
            // Swallow every touch so nothing underneath can be used.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.md), modifier = Modifier.padding(Space.xxl)) {
            DotGlyph(Glyphs.Chart)
            Spacer(Modifier.height(Space.md))
            Text("MoneyRecords is locked", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Use your fingerprint, face or phone screen lock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.md))
            PillButton("Unlock", onClick = { lock.promptUnlock(activity, force = true) })
        }
    }
}
