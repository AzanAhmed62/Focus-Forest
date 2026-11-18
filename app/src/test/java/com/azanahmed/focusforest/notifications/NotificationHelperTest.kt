package com.azanahmed.focusforest.notifications

import com.azanahmed.focusforest.domain.model.NotificationPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class NotificationHelperTest {

    @Test
    fun `quiet hours handles overnight ranges accurately`() {
        val prefs = NotificationPreferences(
            notificationsEnabled = true,
            quietHoursEnabled = true,
            quietHoursStartHour = 22,
            quietHoursEndHour = 7
        )
        assertTrue(NotificationHelper.isInQuietHours(prefs, LocalTime.of(23, 30)))
        assertTrue(NotificationHelper.isInQuietHours(prefs, LocalTime.of(3, 0)))
        assertFalse(NotificationHelper.isInQuietHours(prefs, LocalTime.of(7, 0)))
        assertFalse(NotificationHelper.isInQuietHours(prefs, LocalTime.of(14, 0)))

        val disabled = prefs.copy(quietHoursEnabled = false)
        assertFalse(NotificationHelper.isInQuietHours(disabled, LocalTime.of(23, 30)))
    }
}
