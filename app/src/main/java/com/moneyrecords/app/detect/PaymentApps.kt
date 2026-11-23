package com.moneyrecords.app.detect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** A UPI / payment app MoneyRecords knows how to read and open. */
data class PaymentApp(val pkg: String, val name: String)

object PaymentApps {
    /** Known payment and bank apps whose notifications are read (nothing else is). */
    val known = listOf(
        PaymentApp("com.google.android.apps.nbu.paisa.user", "Google Pay"),
        PaymentApp("com.phonepe.app", "PhonePe"),
        PaymentApp("net.one97.paytm", "Paytm"),
        PaymentApp("in.org.npci.upiapp", "BHIM"),
        PaymentApp("in.amazon.mShop.android.shopping", "Amazon Pay"),
        PaymentApp("com.dreamplug.androidapp", "CRED"),
        PaymentApp("com.mobikwik_new", "MobiKwik"),
        PaymentApp("com.sbi.lotusintouch", "YONO SBI"),
        PaymentApp("com.sbi.upi", "BHIM SBI Pay"),
        PaymentApp("com.bankofbaroda.mconnect", "bob World"),
        PaymentApp("com.bankofbaroda.upi", "BHIM Baroda Pay"),
        PaymentApp("com.whatsapp", "WhatsApp Pay"),
    )
    private val byPackage = known.associateBy { it.pkg }

    fun isPaymentApp(pkg: String) = pkg in byPackage
    fun nameOf(pkg: String) = byPackage[pkg]?.name

    /** Payment apps on this phone: anything that can handle a UPI link, plus known ones installed. */
    fun installed(context: Context): List<PaymentApp> {
        val pm = context.packageManager
        val upi = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay")), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
        val knownInstalled = known.map { it.pkg }.filter { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        }
        return (upi + knownInstalled).distinct().map { pkg ->
            byPackage[pkg] ?: PaymentApp(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg))
        }.sortedBy { it.name.lowercase() }
    }
}
