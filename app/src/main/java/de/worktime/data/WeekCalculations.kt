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
 * Abwesende Tage innerhalb der konfigurierten Arbeitstage. [excluding] lässt einen Tag aus,
 * der gerade gearbeitet wird und dessen Abwesenheit damit hinfällig ist.
 */
fun Map<DayOfWeek, WorkSessionStore.WeekEntry>.absentWorkDays(
    workDaysPerWeek: Int,
    excluding: DayOfWeek? = null
): Int = count { (day, entry) ->
    day != excluding &&
        entry.isAbsent &&
        WorkSessionStore.isConfiguredWorkDay(day, workDaysPerWeek)
}

/**
 * Wochenziel anteilig für die anwesenden Arbeitstage, auf die nächste volle Minute
 * aufgerundet. Eine komplett abwesende Woche ergibt damit genau 0.
 */
fun Map<DayOfWeek, WorkSessionStore.WeekEntry>.adjustedWeeklyTargetMinutes(
    weeklyTargetMinutes: Int,
    workDaysPerWeek: Int,
    excluding: DayOfWeek? = null
): Int {
    val presentDays = (workDaysPerWeek - absentWorkDays(workDaysPerWeek, excluding))
        .coerceAtLeast(0)
    // Aufrunden ohne Math.ceilDiv (erst ab Android 14 verfügbar).
    return (weeklyTargetMinutes * presentDays + workDaysPerWeek - 1) / workDaysPerWeek
}

fun Map<DayOfWeek, WorkSessionStore.WeekEntry>.totalNetMinutes(
    breakConfig: WorkTimeCalculator.BreakConfig,
    excluding: DayOfWeek? = null
): Int = entries.sumOf { (day, entry) ->
    if (day == excluding) 0 else entry.netMinutes(breakConfig) ?: 0
}
