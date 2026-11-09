package de.worktime.ui.woche

import de.worktime.data.WorkSessionStore
import de.worktime.data.absentWorkDays
import de.worktime.data.adjustedWeeklyTargetMinutes
import de.worktime.data.netMinutes
import de.worktime.data.totalNetMinutes
import de.worktime.domain.WorkTimeCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)
private val dayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)

internal fun buildWeekShareSubject(today: LocalDate = LocalDate.now()): String {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sunday = monday.plusDays(6)
    return "Wochenübersicht ${monday.format(dateFormatter)} - ${sunday.format(dateFormatter)}"
}

internal fun buildWeekShareText(
    entries: Map<DayOfWeek, WorkSessionStore.WeekEntry>,
    breakConfig: WorkTimeCalculator.BreakConfig,
    weeklyTargetMinutes: Int,
    workDaysPerWeek: Int,
    today: LocalDate = LocalDate.now()
): String {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sunday = monday.plusDays(6)
    val totalMinutes = entries.totalNetMinutes(breakConfig)
    val targetMinutes = entries.adjustedWeeklyTargetMinutes(weeklyTargetMinutes, workDaysPerWeek)
    val balanceMinutes = totalMinutes - targetMinutes
    val absentWeekdays = entries.absentWorkDays(workDaysPerWeek)
    val absentDays = entries.count { (_, entry) -> entry.isAbsent }
    val dayLines = WorkSessionStore.WORK_DAYS.mapNotNull { day ->
        val entry = entries[day] ?: return@mapNotNull null
        if (!entry.hasValue) return@mapNotNull null
        val date = monday.plusDays((day.value - DayOfWeek.MONDAY.value).toLong())
        val heading = "${date.format(dayFormatter).replaceFirstChar(Char::titlecase)}, " +
            date.format(dateFormatter)
        if (entry.isAbsent) {
            "$heading: Abwesend"
        } else {
            val start = entry.startMinutes?.let(::formatTime) ?: "--:--"
            val end = entry.endMinutes?.let(::formatTime) ?: "--:--"
            val net = entry.netMinutes(breakConfig)?.let(WorkTimeCalculator::formatDuration) ?: "--:--"
            "$heading: $start - $end ($net)"
        }
    }

    return buildString {
        appendLine("Wochenübersicht")
        appendLine("${monday.format(dateFormatter)} - ${sunday.format(dateFormatter)}")
        appendLine()
        if (dayLines.isEmpty()) {
            appendLine("Keine Zeiten erfasst.")
        } else {
            dayLines.forEach(::appendLine)
        }
        appendLine()
        appendLine("Gesamt: ${WorkTimeCalculator.formatDuration(totalMinutes)}")
        appendLine("Wochenziel: ${WorkTimeCalculator.formatDuration(targetMinutes)}")
        absentSummaryLabel(absentWeekdays, absentDays)?.let { label ->
            appendLine("Abwesend: $label")
        }
        append("Saldo: ${formatSignedDuration(balanceMinutes)}")
    }
}

internal fun absentSummaryLabel(absentWeekdays: Int, absentDays: Int): String? = when {
    absentWeekdays > 0 -> {
        val noun = if (absentWeekdays == 1) "Werktag" else "Werktage"
        "$absentWeekdays $noun"
    }
    absentDays > 0 -> if (absentDays == 1) "1 Tag" else "$absentDays Tage"
    else -> null
}

internal fun formatSignedDuration(minutes: Int): String {
    val sign = if (minutes >= 0) "+" else "-"
    return sign + WorkTimeCalculator.formatDuration(kotlin.math.abs(minutes))
}

private fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)