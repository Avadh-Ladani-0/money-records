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

/** recordId > 0 edits; duplicateOf > 0 pre-fills a new record from an existing one. */
@Serializable data class RecordEditorRoute(val recordId: Long = 0, val duplicateOf: Long = 0)

@Serializable data class AccountEditorRoute(val accountId: Long = 0)

/** kind is a CategoryKind name. */
@Serializable data class CategoryEditorRoute(val categoryId: Long = 0, val kind: String = "EXPENSE")

object RouteArgs {
    const val RECORD_ID = "recordId"
    const val DUPLICATE_OF = "duplicateOf"
    const val ACCOUNT_ID = "accountId"
    const val CATEGORY_ID = "categoryId"
    const val KIND = "kind"
}
