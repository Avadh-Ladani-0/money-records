package com.moneyrecords.app.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The single place mapping stored icon keys to icons. Keys are persisted in the database,
 * so never rename a key — add a new one instead. Unknown keys fall back to a neutral icon.
 */
object AppIcons {
    private val byKey: Map<String, ImageVector> = mapOf(
        // categories
        "food" to Icons.Outlined.Restaurant,
        "groceries" to Icons.Outlined.ShoppingCart,
        "transport" to Icons.Outlined.DirectionsBus,
        "fuel" to Icons.Outlined.LocalGasStation,
        "shopping" to Icons.Outlined.ShoppingBag,
        "bills" to Icons.AutoMirrored.Outlined.ReceiptLong,
        "rent" to Icons.Outlined.Home,
        "health" to Icons.Outlined.LocalHospital,
        "education" to Icons.Outlined.School,
        "entertainment" to Icons.Outlined.Movie,
        "travel" to Icons.Outlined.Flight,
        "personal" to Icons.Outlined.Spa,
        "gift" to Icons.Outlined.CardGiftcard,
        "other" to Icons.Outlined.Category,
        "salary" to Icons.Outlined.Work,
        "business" to Icons.Outlined.BusinessCenter,
        "interest" to Icons.Outlined.Savings,
        "refund" to Icons.Outlined.Replay,
        // accounts
        "cash" to Icons.Outlined.Payments,
        "bank" to Icons.Outlined.AccountBalance,
        "card" to Icons.Outlined.CreditCard,
        "wallet" to Icons.Outlined.AccountBalanceWallet,
    )

    val accountKeys: List<String> = listOf("cash", "bank", "card", "wallet")
    val categoryKeys: List<String> = byKey.keys.filterNot { it in accountKeys }

    fun forKey(key: String?): ImageVector = key?.let(byKey::get) ?: Icons.Outlined.Category
}
