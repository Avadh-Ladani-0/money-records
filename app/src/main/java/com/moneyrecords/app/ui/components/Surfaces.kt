package com.moneyrecords.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.moneyrecords.app.ui.icons.CategoryBadge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

/** Uppercase, widely tracked label used above sections and figures. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text = text.uppercase(), modifier = modifier.semantics { heading() }, style = MaterialTheme.typography.labelMedium, color = color)
}

/**
 * The one screen header: title on the same baseline on every screen, an optional [subtitle] under
 * it (date, scope), actions on the right.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = Space.lg, bottom = Space.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = Space.sm)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            actions()
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = if (onBack != null) 56.dp else 0.dp, top = Space.xs),
            )
        }
    }
}

/** The standard card: flat container with a hairline border, no elevation. */
@Composable
fun MrCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Space.xl),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MrTheme.colors.hairline),
    ) {
        Column(
            Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * A list row: round icon badge, title + subtitle, trailing slot.
 * [iconKey] draws a category badge (coloured in Graphite); [icon] a neutral one.
 * [spokenText] replaces what a screen reader says for the whole row (it's read as one item).
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconKey: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    /** Replaces [icon] inside the round badge, e.g. a custom glyph. */
    leading: (@Composable () -> Unit)? = null,
    subtitleLines: Int = 1,
    spokenText: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .then(
                when {
                    onLongClick != null -> Modifier.combinedClickable(
                        onClick = onClick ?: {},
                        onLongClick = onLongClick,
                        onLongClickLabel = onLongClickLabel,
                    )
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                },
            )
            .then(if (spokenText != null) Modifier.semantics(mergeDescendants = true) { contentDescription = spokenText } else Modifier)
            .padding(vertical = Space.md, horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            iconKey != null && leading == null && icon == null -> {
                CategoryBadge(iconKey)
                Spacer(Modifier.width(Space.lg))
            }
            icon != null || leading != null -> {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(MrTheme.colors.badge, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (leading != null) leading() else Icon(icon!!, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(Space.lg))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.md))
            trailing()
        }
    }
}

/** The app's switch: white (Nothing) or green (Graphite) track when on. */
@Composable
fun MrSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MrTheme.colors.switchOn,
            checkedThumbColor = MrTheme.colors.onSwitchOn,
            checkedBorderColor = MrTheme.colors.switchOn,
        ),
    )
}

/**
 * A settings row with a switch. The whole row is one control (one stop for screen readers, one big
 * touch target) and gives toggle haptics.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    ListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        subtitleLines = 2,
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = { haptics.toggle(it); onCheckedChange(it) },
        ),
        trailing = { MrSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** A record (transaction) row: income signed "+"; colours come from the theme's money tokens. */
@Composable
fun RecordRow(
    title: String,
    subtitle: String,
    paise: Long,
    isIncome: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = modifier,
        onClick = onClick,
        trailing = {
            AmountText(
                paise = if (isIncome) paise else -paise,
                sign = Money.Sign.ALWAYS,
                color = if (isIncome) MrTheme.colors.positive else MrTheme.colors.expense,
            )
        },
    )
}

/** Small outlined tag, e.g. "SOON" or "OVER". */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs / 2),
        )
    }
}

/**
 * Primary pill button — solid ink. [accent] makes it the screen's one main action in the accent
 * colour (the record editor's Save), matching the add button.
 */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, accent: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) MrTheme.colors.accent else MaterialTheme.colorScheme.primary,
            contentColor = if (accent) MrTheme.colors.onAccent else MaterialTheme.colorScheme.onPrimary,
            // Clearly "not yet": readable, but obviously inactive.
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = Space.xl),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Secondary pill button — hairline outline. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = Space.xl),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground) }
}

/** The accent "add" button. */
@Composable
fun AccentFab(onClick: () -> Unit, icon: ImageVector, contentDescription: String, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(64.dp),
        shape = CircleShape,
        containerColor = MrTheme.colors.accent,
        contentColor = MrTheme.colors.onAccent,
    ) { Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(28.dp)) }
}

/** Centered empty state with a dot-matrix glyph. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    glyph: List<String> = Glyphs.Empty,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxl, horizontal = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        DotGlyph(glyph)
        Spacer(Modifier.height(Space.sm))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Space.sm))
            action()
        }
    }
}
