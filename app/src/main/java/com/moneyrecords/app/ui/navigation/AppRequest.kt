package com.moneyrecords.app.ui.navigation

/** Things outside the UI (notifications, widgets) ask the app to do when it opens. */
sealed interface AppRequest {
    data object OpenBudgets : AppRequest
    data object OpenHome : AppRequest
    data object OpenBackup : AppRequest
    /** [kind] is a RecordKind name, or null for the usual default. */
    data class AddRecord(val kind: String? = null) : AppRequest
    /** Open the record editor pre-filled from a due recurring item. */
    data class EditDue(val ruleId: Long) : AppRequest
}
