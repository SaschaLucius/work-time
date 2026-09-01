package de.worktime

import de.worktime.data.WorkSessionStore
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetNotificationSchedulerTest {

    private val today = LocalDate.of(2026, 9, 1)

    @Test
    fun `disabled notifications have no trigger`() {
        assertNull(
            targetNotificationTriggerMillis(
                startTimeMillis = 1_000L,
                settings = WorkSessionStore.AppSettings(),
                today = today
            )
        )
    }

    @Test
    fun `default target includes the first break`() {
        val settings = WorkSessionStore.AppSettings(notificationsEnabled = true)

        assertEquals(
            1_000L + 510 * 60_000L,
            targetNotificationTriggerMillis(1_000L, settings, today)
        )
    }

    @Test
    fun `early offset lowers the net target`() {
        val settings = WorkSessionStore.AppSettings(
            notificationsEnabled = true,
            notificationOffsetMinutes = 30
        )

        assertEquals(
            1_000L + 480 * 60_000L,
            targetNotificationTriggerMillis(1_000L, settings, today)
        )
    }

    @Test
    fun `notification already shown today has no trigger`() {
        val settings = WorkSessionStore.AppSettings(
            notificationsEnabled = true,
            lastNotificationDate = today.toString()
        )

        assertNull(targetNotificationTriggerMillis(1_000L, settings, today))
    }
}