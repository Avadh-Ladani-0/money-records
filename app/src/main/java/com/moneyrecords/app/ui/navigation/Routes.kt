package com.moneyrecords.app.ui.navigation

import kotlinx.serialization.Serializable

// Arguments are non-null primitives (0 = none) so ViewModels can read them straight from
// SavedStateHandle by property name, which also keeps them testable on the JVM.

@Serializable data object HomeRoute
@Serializable data object RecordsRoute
@Serializable data object AccountsRoute
@Serializable data object BudgetsRoute
@Serializable data object SettingsRoute
@Serializable data object DesignSystemRoute
@Serializable data object CategoriesRoute

/**
 * recordId > 0 edits; duplicateOf > 0 pre-fills a new record from an existing one;
 * fromRuleId > 0 pre-fills from a due recurring item and confirms it on save.
 * kind (a RecordKind name, "" = remembered default) starts a new record as that type.
 */
@Serializable data class RecordEditorRoute(
    val recordId: Long = 0,
    val duplicateOf: Long = 0,
    val fromRuleId: Long = 0,
    val kind: String = "",
    /** > 0: a caught payment (bank SMS / payment app / pay widget) to record. */
    val detectedId: Long = 0,
)

@Serializable data object RecurringRoute
@Serializable data class RecurringEditorRoute(val ruleId: Long)
@Serializable data object NotificationSettingsRoute
@Serializable data object AppLockSettingsRoute
@Serializable data object AppearanceRoute
@Serializable data object BackupRoute
@Serializable data object ToCollectRoute
@Serializable data object ToRecordRoute
@Serializable data object AutoDetectRoute

@Serializable data class AccountEditorRoute(val accountId: Long = 0)

/** kind is a CategoryKind name. */
@Serializable data class CategoryEditorRoute(val categoryId: Long = 0, val kind: String = "EXPENSE")

object RouteArgs {
    const val RECORD_ID = "recordId"
    const val DUPLICATE_OF = "duplicateOf"
    const val FROM_RULE_ID = "fromRuleId"
    const val RULE_ID = "ruleId"
    const val ACCOUNT_ID = "accountId"
    const val CATEGORY_ID = "categoryId"
    const val KIND = "kind"
    const val DETECTED_ID = "detectedId"
}
