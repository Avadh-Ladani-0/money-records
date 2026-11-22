package com.moneyrecords.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.ui.theme.InterFamily
import com.moneyrecords.app.ui.theme.TabularNums

/** Rolls a changed figure: a higher value comes in from below, a lower one from above. */
private fun rollSpec(up: Boolean): ContentTransform {
    val dir = if (up) 1 else -1
    return (slideInVertically(tween(280)) { h -> dir * h / 2 } + fadeIn(tween(280)))
        .togetherWith(slideOutVertically(tween(200)) { h -> -dir * h / 2 } + fadeOut(tween(200)))
}

/** An amount in running text or a list row. [animate] rolls it when the value changes. */
@Composable
fun AmountText(
    paise: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    sign: Money.Sign = Money.Sign.NEGATIVE_ONLY,
    color: Color = Color.Unspecified,
    animate: Boolean = false,
) {
    val spoken = Modifier.semantics { contentDescription = Money.spoken(paise) }
    if (!animate) {
        Text(
            text = Money.format(paise, sign = sign),
            modifier = modifier.then(spoken),
            style = style.copy(fontFeatureSettings = TabularNums),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    AnimatedContent(
        targetState = paise,
        transitionSpec = { rollSpec(targetState > initialState) },
        modifier = modifier.then(spoken),
        label = "amountRoll",
    ) { value ->
        Text(
            text = Money.format(value, sign = sign),
            style = style.copy(fontFeatureSettings = TabularNums),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The large dot-matrix amount used for balances. The rupee symbol and paise are set smaller
 * than the rupees, and the whole figure shrinks to fit its width.
 */
@Composable
fun HeroAmount(
    paise: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    color: Color = LocalContentColor.current,
    /** Roll to the new figure when it changes (balances on Home and Accounts). */
    animate: Boolean = false,
) {
    if (animate) {
        AnimatedContent(
            targetState = paise,
            transitionSpec = { rollSpec(targetState > initialState) },
            modifier = modifier,
            label = "heroRoll",
        ) { value -> HeroAmount(value, style = style, color = color, animate = false) }
        return
    }
    val parts = Money.parts(paise, alwaysShowPaise = true)
    val text = buildAnnotatedString {
        // ₹ comes from Inter (Doto has no rupee glyph); raise it to sit with the dot digits.
        withStyle(SpanStyle(fontFamily = InterFamily, fontSize = 0.55.em, baselineShift = BaselineShift(0.35f))) {
            append(parts.sign)
            append(parts.symbol)
        }
        append(parts.rupees)
        // Doto's "." at full weight is a 5-dot cross, so the decimal point comes from Inter.
        withStyle(SpanStyle(fontFamily = InterFamily, fontSize = 0.6.em)) { append(".") }
        withStyle(SpanStyle(fontSize = 0.6.em)) { append(parts.paise) }
    }
    BasicText(
        text = text,
        modifier = modifier.semantics { contentDescription = Money.spoken(paise) },
        style = style.copy(color = color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 28.sp,
            maxFontSize = style.fontSize,
            stepSize = 2.sp,
        ),
    )
}
