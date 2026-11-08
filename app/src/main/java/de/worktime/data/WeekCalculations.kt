package de.worktime.data

import de.worktime.domain.WorkTimeCalculator
import java.time.DayOfWeek

fun WorkSessionStore.WeekEntry.netMinutes(
    breakConfig: WorkTimeCalculator.BreakConfig
): Int? {
    if (isAbsent) return null
    val start = startMinutes ?: return null
    val end = endMinutes ?: return null
    return WorkTimeCalculator.calculateFromStartEnd(start, end, breakConfig)
}

/**
 * Wochenziel abzüglich eines Tagesziels je abwesendem konfiguriertem Arbeitstag.
 * Tage außerhalb von [workDaysPerWeek] zählen nicht auf das Soll. [excluding] lässt
 * einen Tag aus, der gerade gearbeitet wird und dessen Abwesenheit damit hinfällig ist.
 */
fun Map<DayOfWeek, WorkSessionStore.WeekEntry>.adjustedWeeklyTargetMinutes(
    weeklyTargetMinutes: Int,
    dailyTargetMinutes: Int,
    workDaysPerWeek: Int = WorkSessionStore.DEFAULT_WORK_DAYS_PER_WEEK,
    excluding: DayOfWeek? = null
): Int {
    val absentWeekdays = count { (day, entry) ->
        day != excluding &&
            entry.isAbsent &&
            WorkSessionStore.isConfiguredWorkDay(day, workDaysPerWeek)
    }
    return (weeklyTargetMinutes - absentWeekdays * dailyTargetMinutes).coerceAtLeast(0)
}

fun Map<DayOfWeek, WorkSessionStore.WeekEntry>.totalNetMinutes(
    breakConfig: WorkTimeCalculator.BreakConfig,
    excluding: DayOfWeek? = null
): Int = entries.sumOf { (day, entry) ->
    if (day == excluding) 0 else entry.netMinutes(breakConfig) ?: 0
}
