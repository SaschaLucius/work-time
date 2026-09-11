package de.worktime.data

import de.worktime.domain.WorkTimeCalculator
import java.time.DayOfWeek

fun WorkSessionStore.WeekEntry.netMinutes(
    breakConfig: WorkTimeCalculator.BreakConfig
): Int? {
    val start = startMinutes ?: return null
    val end = endMinutes ?: return null
    return WorkTimeCalculator.calculateFromStartEnd(start, end, breakConfig)
}

fun Map<DayOfWeek, WorkSessionStore.WeekEntry>.totalNetMinutes(
    breakConfig: WorkTimeCalculator.BreakConfig,
    excluding: DayOfWeek? = null
): Int = entries.sumOf { (day, entry) ->
    if (day == excluding) 0 else entry.netMinutes(breakConfig) ?: 0
}