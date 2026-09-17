package de.worktime.ui.woche

import de.worktime.data.WorkSessionStore
import de.worktime.domain.WorkTimeCalculator
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekOverviewModelTest {

    private val breakConfig = WorkTimeCalculator.BreakConfig()

    @Test
    fun `saved entries keep finalized totals`() {
        val model = buildWeekOverviewModel(
            entries = mapOf(
                DayOfWeek.MONDAY to WorkSessionStore.WeekEntry(8 * 60, 16 * 60 + 30),
                DayOfWeek.TUESDAY to WorkSessionStore.WeekEntry(9 * 60, 17 * 60)
            ),
            runningDraft = null,
            breakConfig = breakConfig,
            showWeekends = false,
            weeklyTargetMinutes = 39 * 60
        )

        assertEquals(15 * 60 + 30, model.totalMinutes)
        assertEquals(2, model.filledDays)
        assertEquals(5, model.days.size)
        assertFalse(model.isProvisional)
    }

    @Test
    fun `running draft contributes to provisional total and balance`() {
        val model = buildWeekOverviewModel(
            entries = mapOf(
                DayOfWeek.MONDAY to WorkSessionStore.WeekEntry(8 * 60, 16 * 60 + 30)
            ),
            runningDraft = RunningDayDraft(
                day = DayOfWeek.TUESDAY,
                startMinutes = 9 * 60,
                endMinutes = 12 * 60,
                netMinutes = 3 * 60
            ),
            breakConfig = breakConfig,
            showWeekends = false,
            weeklyTargetMinutes = 10 * 60
        )

        assertEquals(11 * 60, model.totalMinutes)
        assertEquals(60, model.balanceMinutes)
        assertEquals(2, model.filledDays)
        assertTrue(model.isProvisional)
    }

    @Test
    fun `running draft replaces saved entry for today`() {
        val model = buildWeekOverviewModel(
            entries = mapOf(
                DayOfWeek.MONDAY to WorkSessionStore.WeekEntry(8 * 60, 16 * 60 + 30),
                DayOfWeek.TUESDAY to WorkSessionStore.WeekEntry(9 * 60, 17 * 60)
            ),
            runningDraft = RunningDayDraft(
                day = DayOfWeek.TUESDAY,
                startMinutes = 10 * 60,
                endMinutes = 12 * 60,
                netMinutes = 2 * 60
            ),
            breakConfig = breakConfig,
            showWeekends = false,
            weeklyTargetMinutes = 39 * 60
        )

        assertEquals(10 * 60, model.totalMinutes)
        assertEquals(2, model.filledDays)
        assertTrue(model.days.single { it.day == DayOfWeek.TUESDAY }.isRunningDraft)
    }

    @Test
    fun `running weekend is visible while weekends are hidden`() {
        val model = buildWeekOverviewModel(
            entries = emptyMap(),
            runningDraft = RunningDayDraft(
                day = DayOfWeek.SATURDAY,
                startMinutes = 8 * 60,
                endMinutes = 9 * 60,
                netMinutes = 60
            ),
            breakConfig = breakConfig,
            showWeekends = false,
            weeklyTargetMinutes = 39 * 60
        )

        assertEquals(7, model.days.size)
        assertTrue(model.days.single { it.day == DayOfWeek.SATURDAY }.isRunningDraft)
    }
}
