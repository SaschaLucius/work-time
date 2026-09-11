package de.worktime.ui.woche

import de.worktime.data.WorkSessionStore
import de.worktime.domain.WorkTimeCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekShareTextTest {

    @Test
    fun `summary contains dated entries total target and balance`() {
        val entries = mapOf(
            DayOfWeek.MONDAY to WorkSessionStore.WeekEntry(8 * 60, 16 * 60 + 30),
            DayOfWeek.TUESDAY to WorkSessionStore.WeekEntry(9 * 60, 17 * 60)
        )

        assertEquals(
            """Wochenübersicht
07.09.2026 - 13.09.2026

Montag, 07.09.2026: 08:00 - 16:30 (08:00)
Dienstag, 08.09.2026: 09:00 - 17:00 (07:30)

Gesamt: 15:30
Wochenziel: 39:00
Saldo: -23:30""",
            buildWeekShareText(
                entries = entries,
                breakConfig = WorkTimeCalculator.BreakConfig(),
                weeklyTargetMinutes = 39 * 60,
                today = LocalDate.of(2026, 9, 11)
            )
        )
    }

    @Test
    fun `subject uses the current ISO week dates`() {
        assertEquals(
            "Wochenübersicht 07.09.2026 - 13.09.2026",
            buildWeekShareSubject(LocalDate.of(2026, 9, 11))
        )
    }
}