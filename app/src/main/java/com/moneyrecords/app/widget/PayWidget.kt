package com.moneyrecords.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.moneyrecords.app.detect.PaymentApps
import kotlinx.coroutines.flow.first

/** Per-widget settings, stored by Glance for each placed widget. */
object PayWidgetKeys {
    val app = stringPreferencesKey("pay_app")
    val account = longPreferencesKey("pay_account")
}

/**
 * One payment app's "Scan & pay": opens the app straight on its scanner (where the app allows it),
 * then brings you back to record the payment. Placed once per app.
 */
class PayWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val theme = WidgetSnapshot.themeOnly(context)
        provideContent { PayContent(theme) }
    }
}

class PayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = PayWidget()
}

@Composable
private fun PayContent(theme: com.moneyrecords.app.ui.theme.AppTheme) {
    val context = LocalContext.current
    val prefs = currentState<Preferences>()
    val pkg = prefs[PayWidgetKeys.app]
    val c = WidgetPalette.of(theme)
    val name = pkg?.let { PaymentApps.nameOf(it) ?: appLabel(context, it) }
    val launch = Intent(context, PayLaunchActivity::class.java).putExtra(PayLaunchActivity.EXTRA_APP, pkg)
    val side = minOf(androidx.glance.LocalSize.current.width, androidx.glance.LocalSize.current.height)
    // Just the app's mark, monochrome like the phone's themed (Nothing) icons — no label.
    androidx.glance.layout.Box(
        GlanceModifier
            .fillMaxSize()
            .semantics { contentDescription = if (name != null) "Scan and pay with $name" else "Set up pay widget" }
            .clickable(actionStartActivity(launch)),
        contentAlignment = Alignment.Center,
    ) {
        if (pkg == null) {
            WText("Set up", c.ink, 11.sp, bold = true)
        } else {
            val px = (side.value * context.resources.displayMetrics.density).toInt().coerceIn(48, 512)
            Image(
                ImageProvider(monoIcon(context, pkg, name ?: "?", px, NOTHING_GLYPH, NOTHING_DISC)),
                contentDescription = null,
                modifier = GlanceModifier.size(side),
            )
        }
    }
}

private fun appLabel(context: Context, pkg: String): String =
    runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

/** The Nothing launcher's themed-icon colours (measured on the phone): white mark on a dark grey disc. */
private const val NOTHING_GLYPH = 0xFFF2F2F2.toInt()
private const val NOTHING_DISC = 0xFF1A1B20.toInt()
/** How much of the disc the mark spans (its larger side) — measured from the launcher's own icons. */
private const val GLYPH_SPAN = 0.4f

/**
 * The app's icon in one colour on a dark circle: its monochrome layer (the one Android uses for
 * themed icons) when it has one, otherwise the first letter of its name.
 */
private fun monoIcon(context: Context, pkg: String, name: String, size: Int, ink: Int, disc: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = disc }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    val mono = if (android.os.Build.VERSION.SDK_INT >= 33) runCatching {
        (context.packageManager.getApplicationIcon(pkg) as? android.graphics.drawable.AdaptiveIconDrawable)?.monochrome
    }.getOrNull() else null
    // The monochrome layer when the app has one; otherwise its own logo turned into white/grey
    // shades (BHIM's two-colour arrow becomes a white and a grey triangle, like the launcher's).
    val mark = mono?.let { glyphBitmap(it, size * 2, ink) } ?: greyLogo(context, pkg, size * 2)
    if (mark != null) {
        // Apps pad their monochrome layer differently: measure the actual mark and scale it to a
        // consistent share of the disc, centred — so every widget matches the launcher's icons.
        val (bounds, bitmap) = mark
        val scale = size * GLYPH_SPAN / maxOf(bounds.width(), bounds.height())
        val w = bounds.width() * scale
        val h = bounds.height() * scale
        val dst = android.graphics.RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f)
        canvas.drawBitmap(bitmap, bounds, dst, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG))
    } else {
        val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = size * 0.46f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val y = size / 2f - (text.descent() + text.ascent()) / 2
        canvas.drawText(name.take(1).uppercase(), size / 2f, y, text)
    }
    drawBadge(context, canvas, size, disc)
    return bmp
}

/**
 * A small MoneyRecords badge on the lower-right edge (like a notification dot), so this widget
 * isn't mistaken for the payment app's own icon: white disc, MoneyRecords mark in the icon's dark.
 */
private fun drawBadge(context: Context, canvas: Canvas, size: Int, dark: Int) {
    // Kept inside the round icon (launchers clip widget corners): centre offset + radius ≤ half the size.
    val r = size * 0.14f
    val ring = size * 0.025f
    val offset = (size * 0.5f - r - ring) / 1.4142f
    val cx = size * 0.5f + offset
    val cy = size * 0.5f + offset
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    // A ring of the background around the badge keeps it from blending into the disc.
    paint.color = 0xFF000000.toInt()
    canvas.drawCircle(cx, cy, r + ring, paint)
    paint.color = NOTHING_GLYPH
    canvas.drawCircle(cx, cy, r, paint)
    val mark = androidx.core.content.ContextCompat.getDrawable(context, com.moneyrecords.app.R.drawable.ic_notification)?.mutate() ?: return
    val m = (r * 1.25f).toInt()
    mark.setBounds((cx - m / 2f).toInt(), (cy - m / 2f).toInt(), (cx + m / 2f).toInt(), (cy + m / 2f).toInt())
    mark.setTint(dark)
    mark.draw(canvas)
}

/** Renders a monochrome layer in [ink] and finds the bounds of what's actually drawn. */
private fun glyphBitmap(mono: android.graphics.drawable.Drawable, size: Int, ink: Int): Pair<android.graphics.Rect, Bitmap>? {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    mono.setBounds(0, 0, size, size)
    mono.setTint(ink)
    mono.draw(Canvas(bmp))
    return opaqueBounds(bmp)?.let { it to bmp }
}

/** The app's logo (adaptive foreground, else its icon) as white-to-grey shades by brightness. */
private fun greyLogo(context: Context, pkg: String, size: Int): Pair<android.graphics.Rect, Bitmap>? = runCatching {
    val icon = context.packageManager.getApplicationIcon(pkg)
    val logo = (icon as? android.graphics.drawable.AdaptiveIconDrawable)?.foreground ?: icon
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    logo.setBounds(0, 0, size, size)
    logo.draw(Canvas(bmp))
    val px = IntArray(size * size)
    bmp.getPixels(px, 0, size, 0, 0, size, size)
    // Brightest part white, darkest part dark grey (contrast stretched), like the launcher's
    // two-tone marks; transparent stays transparent.
    fun lumOf(c: Int) = (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)) / 255.0
    val opaque = px.filter { (it ushr 24) > 200 }
    val lo = opaque.minOfOrNull(::lumOf) ?: 0.0
    val hi = opaque.maxOfOrNull(::lumOf) ?: 1.0
    val range = (hi - lo).takeIf { it > 0.05 }
    for (i in px.indices) {
        val c = px[i]
        val a = c ushr 24
        if (a == 0) continue
        val t = range?.let { ((lumOf(c) - lo) / it).coerceIn(0.0, 1.0) } ?: 1.0
        val v = (88 + 154 * t).toInt()
        px[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    bmp.setPixels(px, 0, size, 0, 0, size, size)
    // A full-bleed square logo (no transparency) can't be shown as a mark.
    val bounds = opaqueBounds(bmp) ?: return@runCatching null
    if (bounds.width() > size * 0.95 && bounds.height() > size * 0.95) return@runCatching null
    bounds to bmp
}.getOrNull()

private fun opaqueBounds(bmp: Bitmap): android.graphics.Rect? {
    val w = bmp.width; val h = bmp.height
    var left = w; var top = h; var right = -1; var bottom = -1
    val row = IntArray(w)
    for (y in 0 until h) {
        bmp.getPixels(row, 0, w, 0, y, w, 1)
        for (x in 0 until w) if ((row[x] ushr 24) > 24) {
            if (x < left) left = x; if (x > right) right = x; if (y < top) top = y; if (y > bottom) bottom = y
        }
    }
    return if (right < left) null else android.graphics.Rect(left, top, right + 1, bottom + 1)
}
