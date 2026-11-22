package com.moneyrecords.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.moneyrecords.app.MainActivity
import com.moneyrecords.app.R
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.notify.Notifications

/** A Glance colour from a Compose one (widgets follow the app theme, not the system's). */
fun Color.glance() = ColorProvider(day = this, night = this)

/** "₹1,502" or, while App lock is on, "₹ ••••". */
fun amount(paise: Long, masked: Boolean, sign: Money.Sign = Money.Sign.NEGATIVE_ONLY): String =
    if (masked) "₹ ••••" else Money.format(paise, sign = sign)

@Composable
fun WText(text: String, color: Color, size: TextUnit = 13.sp, bold: Boolean = false, modifier: GlanceModifier = GlanceModifier, maxLines: Int = 1) {
    Text(
        text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(color = color.glance(), fontSize = size, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal),
    )
}

/** A thin budget bar; the theme's warning / over colours flag a budget near or past its limit. */
@Composable
fun WBar(fraction: Float, color: Color, track: Color, modifier: GlanceModifier = GlanceModifier) {
    LinearProgressIndicator(
        progress = fraction.coerceIn(0f, 1f),
        modifier = modifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
        color = color.glance(),
        backgroundColor = track.glance(),
    )
}

/**
 * The dot-matrix figure. Glance can't use custom fonts, so the text is drawn with the app's Doto
 * face into a bitmap; the ₹ (which Doto lacks) is drawn in the system face.
 */
@Composable
fun DotAmount(context: Context, text: String, color: Color, height: Dp = 36.dp, description: String) {
    val bitmap = renderDotText(context, text, color, height)
    Image(provider = ImageProvider(bitmap), contentDescription = description)
}

private fun renderDotText(context: Context, text: String, color: Color, height: Dp): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = height.value * density
    val doto = ResourcesCompat.getFont(context, R.font.doto) ?: Typeface.DEFAULT_BOLD
    val heavy = if (Build.VERSION.SDK_INT >= 28) Typeface.create(doto, 900, false) else doto
    val digits = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = heavy; textSize = sizePx; this.color = color.toArgb() }
    val symbol = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT; textSize = sizePx * 0.55f; this.color = color.toArgb() }
    // Split the leading sign/₹ (system face) from the digits (Doto).
    val lead = text.takeWhile { !it.isDigit() && it != '•' }
    val rest = text.drop(lead.length)
    val leadWidth = symbol.measureText(lead)
    val width = (leadWidth + digits.measureText(rest)).toInt().coerceAtLeast(1) + 2
    val bmp = Bitmap.createBitmap(width, (sizePx * 1.15f).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val baseline = sizePx
    canvas.drawText(lead, 0f, baseline - sizePx * 0.32f, symbol)
    canvas.drawText(rest, leadWidth, baseline, digits)
    return bmp
}

/** Opens the app with one of its actions (handled by MainActivity; waits for unlock if locked). */
fun appIntent(context: Context, action: String, kind: String? = null): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(action)
        .apply { if (kind != null) putExtra(Notifications.EXTRA_KIND, kind) }
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

@Composable
fun Spacer(h: Dp) = Box(GlanceModifier.height(h)) {}

@Composable
fun Card(background: Color, modifier: GlanceModifier, content: @Composable () -> Unit) {
    Box(modifier.background(background.glance()).cornerRadius(24.dp)) { content() }
}
