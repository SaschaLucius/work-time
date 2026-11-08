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
            workDaysPerWeek = 5,
            weeklyTargetMinutes = 39 * 60,
            dailyTargetMinutes = 8 * 60
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
            workDaysPerWeek = 5,
            weeklyTargetMinutes = 10 * 60,
            dailyTargetMinutes = 8 * 60
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
            workDaysPerWeek = 5,
            weeklyTargetMinutes = 39 * 60,
            dailyTargetMinutes = 8 * 60
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
            workDaysPerWeek = 5,
            weeklyTargetMinutes = 39 * 60,
            dailyTargetMinutes = 8 * 60
        )

        assertEquals(6, model.days.size)
        assertTrue(model.days.single { it.day == DayOfWeek.SATURDAY }.isRunningDraft)
    }

    @Test
    fun `six work days show saturday by default`() {
        val model = buildWeekOverviewModel(
            entries = emptyMap(),
            runningDraft = null,
            breakConfig = breakConfig,
            workDaysPerWeek = 6,
            weeklyTargetMinutes = 39 * 60,
            dailyTargetMinutes = 6 * 60 + 30
        )

        assertEquals(6, model.days.size)
        assertTrue(model.days.any { it.day == DayOfWeek.SATURDAY })
        assertFalse(model.days.any { it.day == DayOfWeek.SUNDAY })
    }

    @Test
    fun `absent weekday lowers the target and ignores stored hours`() {
        val model = buildWeekOverviewModel(
            entries = mapOf(
                DayOfWeek.MONDAY to WorkSessionStore.WeekEntry(8 * 60, 16 * 60 + 30),
                DayOfWeek.TUESDAY to WorkSessionStore.WeekEntry(
                    startMinutes = 9 * 60,
                    endMinutes = 17 * 60,
                    isAbsent = true
                )
            ),
            runningDraft = null,
            breakConfig = breakConfig,
            workDaysPerWeek = 5,
            weeklyTargetMinutes = 39 * 60,
            dailyTargetMinutes = 8 * 60
        )

        assertEquals(8 * 60, model.totalMinutes)
        assertEquals(31 * 60, model.targetMinutes)
        assertEquals(-23 * 60, model.balanceMinutes)
        assertEquals(1, model.filledDays)
        assertEquals(1, model.absentWeekdays)
        assertTrue(model.days.single { it.day == DayOfWeek.TUESDAY }.isAbsent)
        assertEquals(null, model.days.single { it.day == DayOfWeek.TUESDAY }.netMinutes)
    }

    @Test
    fun `weekend absence is visible without lowering the weekly target`() {
        val model = buildWeekOverviewModel(
            entries = mapOf(
                DayOfWeek.SATURDAY to WorkSessionStore.WeekEntry(isAbsent = true)
            ),
            runningDraft = null,
            breakConfig = breakConfig,
            workDaysPerWeek = 5,
            weeklyTargetMinutes = 39 * 60,
            dailyTargetMinutes = 8 * 60
        )

        assertEquals(6, model.days.size)
        assertEquals(1, model.absentDays)
        assertEquals(0, model.absentWeekdays)
        assertEquals(39 * 60, model.targetMinutes)
        assertTrue(model.hasContent)
    }

    @Test
    fun `running draft replaces an absence for that day`() {
        val model = buildWeekOverviewModel(
            entries = mapOf(
                DayOfWeek.TUESDAY to WorkSessionStore.WeekEntry(isAbsent = true)
            ),
            runningDraft = RunningDayDraft(
                day = DayOfWeek.TUESDAY,
                startMinutes = 9 * 60,
                endMinutes = 12 * 60,
                netMinutes = 3 * 60
            ),
            breakConfig = breakConfig,
            workDaysPerWeek = 5,
            weeklyTargetMinutes = 39 * 60,
            dailyTargetMinutes = 8 * 60
        )

        val tuesday = model.days.single { it.day == DayOfWeek.TUESDAY }
        assertTrue(tuesday.isRunningDraft)
        assertFalse(tuesday.isAbsent)
        assertEquals(39 * 60, model.targetMinutes)
        assertEquals(3 * 60, model.totalMinutes)
    }
}
