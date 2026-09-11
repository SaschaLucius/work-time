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
    fun `notifications already shown for day and week have no trigger`() {
        val settings = WorkSessionStore.AppSettings(
            notificationsEnabled = true,
            lastNotificationDate = today.toString(),
            lastWeeklyNotification = WorkSessionStore.weekId(today)
        )

        assertNull(targetNotificationTriggerMillis(1_000L, settings, today))
    }

    @Test
    fun `weekly target triggers when remaining week time is reached`() {
        val settings = WorkSessionStore.AppSettings(notificationsEnabled = true)

        assertEquals(
            1_000L + 30 * 60_000L,
            targetNotificationTriggerMillis(
                startTimeMillis = 1_000L,
                settings = settings,
                today = today,
                completedWeekMinutes = 38 * 60 + 30
            )
        )
    }

    @Test
    fun `daily reminder remains after weekly notification`() {
        val settings = WorkSessionStore.AppSettings(
            notificationsEnabled = true,
            lastWeeklyNotification = WorkSessionStore.weekId(today)
        )

        assertEquals(
            1_000L + 510 * 60_000L,
            targetNotificationTriggerMillis(
                startTimeMillis = 1_000L,
                settings = settings,
                today = today,
                completedWeekMinutes = 38 * 60 + 30
            )
        )
    }
}