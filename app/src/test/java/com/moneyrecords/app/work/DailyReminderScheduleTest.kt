package com.moneyrecords.app.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class DailyReminderScheduleTest {
    @Test fun `next reminder is later today or tomorrow`() {
        val now = LocalDateTime.of(2026, 11, 22, 18, 30)
        assertEquals(Duration.ofMinutes(150), DailyReminderWorker.delayUntil(now, 21, 0))         // 9 PM today
        assertEquals(Duration.ofHours(23), DailyReminderWorker.delayUntil(now, 17, 30))          // already passed → tomorrow
        assertEquals(Duration.ofDays(1), DailyReminderWorker.delayUntil(now, 18, 30))           // exactly now → tomorrow
    }
}
