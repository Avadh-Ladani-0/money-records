package com.moneyrecords.app.detect

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * How to open each payment app straight on its QR scanner — the same entry its own "Scan any QR"
 * launcher shortcut uses (checked on the user's phone, 23 Nov 2026):
 * - Google Pay ✓ and PhonePe ✓ open on the scanner;
 * - BHIM Baroda Pay uses the same mechanism as Google Pay (unverified: it refuses to run with
 *   developer options on);
 * - BHIM doesn't let other apps open its scanner, so it opens normally.
 * Anything that can't be opened this way falls back to the app's normal start screen.
 */
object ScanRoutes {
    private const val FLUTTER_ACTION_KEY = "some unique action key"

    private fun flutterShortcut(pkg: String, cls: String, key: String) =
        Intent("android.intent.action.RUN")
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(pkg, cls))
            .putExtra(FLUTTER_ACTION_KEY, key)

    private val scanners: Map<String, () -> Intent> = mapOf(
        "com.google.android.apps.nbu.paisa.user" to {
            flutterShortcut("com.google.android.apps.nbu.paisa.user", "com.google.nbu.paisa.flutter.gpay.app.LauncherActivity", "QR_SCANNER")
        },
        "com.bankofbaroda.upi" to { flutterShortcut("com.bankofbaroda.upi", "com.bankofbaroda.upi.MainActivity", "scan_qr") },
        "com.phonepe.app" to { Intent(Intent.ACTION_VIEW, Uri.parse("phonepe://native?id=scanQR")).setPackage("com.phonepe.app") },
    )

    /** True when the widget for [pkg] opens straight on the scanner. */
    fun opensScanner(pkg: String) = pkg in scanners

    fun intentFor(context: Context, pkg: String): Intent? {
        scanners[pkg]?.invoke()?.let { intent ->
            val resolved = context.packageManager.resolveActivity(intent, 0)
            if (resolved?.activityInfo?.exported == true) return intent
        }
        return context.packageManager.getLaunchIntentForPackage(pkg)
    }
}
