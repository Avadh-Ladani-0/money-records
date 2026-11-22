package com.moneyrecords.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * One vocabulary for touch feedback, so the same kind of action always feels the same.
 * Uses the system's haptic types, so the phone's own haptics setting is respected.
 */
@Stable
class Haptics(private val feedback: HapticFeedback) {
    /** A keypad press or a small step. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
    /** Choosing one option among several (chips, segments, categories, tabs). */
    fun select() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
    /** Something was saved, added or completed. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)
    /** An input was refused (a third decimal, a limit reached). */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
    /** A long-press started a mode (duplicate, combine, drag). */
    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    fun toggle(on: Boolean) =
        feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    /** A drag passed its threshold and started moving something. */
    fun dragStart() = feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    fun dragEnd() = feedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
