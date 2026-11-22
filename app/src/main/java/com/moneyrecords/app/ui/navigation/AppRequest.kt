package com.moneyrecords.app.ui.navigation

/** Things outside the UI (notifications) ask the app to do when it opens. */
sealed interface AppRequest {
    data object OpenBudgets : AppRequest
    data object AddRecord : AppRequest
    /** Open the record editor pre-filled from a due recurring item. */
    data class EditDue(val ruleId: Long) : AppRequest
}
