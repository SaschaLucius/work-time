package de.worktime.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WorkSessionStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `settings expose defaults and persist updates`() = runTest {
        val store = createStore()

        assertEquals(WorkSessionStore.AppSettings(), store.settings.first())

        store.updateBreakMinutes(20, 50)
        store.updateDailyTarget(7 * 60)
        store.updateNotificationsEnabled(true)
        store.updateNotificationOffset(15)
        store.markNotificationShown(LocalDate.of(2026, 9, 1))

        assertEquals(
            WorkSessionStore.AppSettings(
                firstBreakMinutes = 20,
                secondBreakMinutes = 50,
                dailyTargetMinutes = 7 * 60,
                notificationsEnabled = true,
                notificationOffsetMinutes = 15,
                lastNotificationDate = "2026-09-01"
            ),
            store.settings.first()
        )
    }

    @Test
    fun `lowering target clamps notification offset`() = runTest {
        val store = createStore()
        store.updateNotificationOffset(300)

        store.updateDailyTarget(240)

        assertEquals(240, store.settings.first().notificationOffsetMinutes)
    }

    @Test
    fun `invalid settings are rejected`() = runTest {
        val store = createStore()

        assertIllegalArgument { store.updateBreakMinutes(45, 30) }
        assertIllegalArgument { store.updateDailyTarget(0) }
        assertIllegalArgument { store.updateNotificationOffset(-1) }
    }

    @Test
    fun `weekday entries persist partial edits and reset together`() = runTest {
        val store = createStore()
        store.updateWeekStart(DayOfWeek.MONDAY, 8 * 60)
        store.updateWeekEnd(DayOfWeek.MONDAY, 16 * 60 + 30)
        store.updateWeekStart(DayOfWeek.TUESDAY, 9 * 60)

        val entries = store.weekEntries.first()
        assertEquals(8 * 60, entries.getValue(DayOfWeek.MONDAY).startMinutes)
        assertEquals(16 * 60 + 30, entries.getValue(DayOfWeek.MONDAY).endMinutes)
        assertEquals(9 * 60, entries.getValue(DayOfWeek.TUESDAY).startMinutes)
        assertNull(entries.getValue(DayOfWeek.TUESDAY).endMinutes)

        store.resetWeek()

        assertTrue(store.weekEntries.first().values.none { entry -> entry.hasValue })
    }

    @Test
    fun `resetting one weekday keeps the other entries`() = runTest {
        val store = createStore()
        store.updateWeekStart(DayOfWeek.MONDAY, 8 * 60)
        store.updateWeekEnd(DayOfWeek.MONDAY, 17 * 60)
        store.updateWeekStart(DayOfWeek.TUESDAY, 9 * 60)

        store.resetWeekDay(DayOfWeek.MONDAY)

        val entries = store.weekEntries.first()
        assertFalse(entries.getValue(DayOfWeek.MONDAY).hasValue)
        assertEquals(9 * 60, entries.getValue(DayOfWeek.TUESDAY).startMinutes)
    }

    @Test
    fun `saving a day and resetting the active session is atomic`() = runTest {
        val store = createStore()
        store.startSession(123_000L)

        store.saveDayAndResetSession(DayOfWeek.WEDNESDAY, 8 * 60, 17 * 60)

        val session = store.session.first()
        val entry = store.weekEntries.first().getValue(DayOfWeek.WEDNESDAY)
        assertFalse(session.isRunning)
        assertEquals(-1L, session.startTimeMillis)
        assertEquals(8 * 60, entry.startMinutes)
        assertEquals(17 * 60, entry.endMinutes)
    }

    @Test
    fun `weekend entries are rejected without clearing the session`() = runTest {
        val store = createStore()
        store.startSession(123_000L)

        assertIllegalArgument {
            store.saveDayAndResetSession(DayOfWeek.SATURDAY, 8 * 60, 17 * 60)
        }

        assertTrue(store.session.first().isRunning)
    }

    private fun kotlinx.coroutines.test.TestScope.createStore(): WorkSessionStore {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.newFile("preferences.preferences_pb") }
        )
        return WorkSessionStore(dataStore)
    }

    private suspend fun assertIllegalArgument(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }
}