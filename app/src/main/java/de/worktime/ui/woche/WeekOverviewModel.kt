package de.worktime.ui.woche

import de.worktime.data.WorkSessionStore
import de.worktime.data.netMinutes
import de.worktime.domain.WorkTimeCalculator
import java.time.DayOfWeek

data class RunningDayDraft(
    val day: DayOfWeek,
    val startMinutes: Int,
    val endMinutes: Int,
    val netMinutes: Int
)

data class WeekOverviewDay(
    val day: DayOfWeek,
    val startMinutes: Int?,
    val endMinutes: Int?,
    val netMinutes: Int?,
    val isRunningDraft: Boolean
)

data class WeekOverviewModel(
    val days: List<WeekOverviewDay>,
    val totalMinutes: Int,
    val balanceMinutes: Int,
    val filledDays: Int,
    val isProvisional: Boolean
)

fun buildWeekOverviewModel(
    entries: Map<DayOfWeek, WorkSessionStore.WeekEntry>,
    runningDraft: RunningDayDraft?,
    breakConfig: WorkTimeCalculator.BreakConfig,
    showWeekends: Boolean,
    weeklyTargetMinutes: Int
): WeekOverviewModel {
    val workDays = WorkSessionStore.WORK_DAYS.take(5)
    val weekendDays = WorkSessionStore.WORK_DAYS.drop(5)
    val showWeekendRows = showWeekends || weekendDays.any { day ->
        entries[day]?.hasValue == true || runningDraft?.day == day
    }
    val visibleDays = if (showWeekendRows) workDays + weekendDays else workDays

    val days = visibleDays.map { day ->
        if (runningDraft?.day == day) {
            WeekOverviewDay(
                day = day,
                startMinutes = runningDraft.startMinutes,
                endMinutes = runningDraft.endMinutes,
                netMinutes = runningDraft.netMinutes,
                isRunningDraft = true
            )
        } else {
            val entry = entries[day] ?: WorkSessionStore.WeekEntry()
            WeekOverviewDay(
                day = day,
                startMinutes = entry.startMinutes,
                endMinutes = entry.endMinutes,
                netMinutes = entry.netMinutes(breakConfig),
                isRunningDraft = false
            )
        }
    }
    val filledDays = days.count { day -> day.netMinutes != null }
    val totalMinutes = days.sumOf { day -> day.netMinutes ?: 0 }

    return WeekOverviewModel(
        days = days,
        totalMinutes = totalMinutes,
        balanceMinutes = totalMinutes - weeklyTargetMinutes,
        filledDays = filledDays,
        isProvisional = runningDraft != null
    )
}
