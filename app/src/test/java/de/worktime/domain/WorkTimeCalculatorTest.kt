package de.worktime.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkTimeCalculatorTest {

    @Test
    fun `default breaks preserve current threshold behavior`() {
        assertEquals(360, WorkTimeCalculator.calculateNetMinutes(360))
        assertEquals(331, WorkTimeCalculator.calculateNetMinutes(361))
        assertEquals(540, WorkTimeCalculator.calculateNetMinutes(570))
        assertEquals(526, WorkTimeCalculator.calculateNetMinutes(571))
    }

    @Test
    fun `custom breaks are deducted after fixed thresholds`() {
        val config = WorkTimeCalculator.BreakConfig(
            firstBreakMinutes = 20,
            secondBreakMinutes = 50
        )

        assertEquals(341, WorkTimeCalculator.calculateNetMinutes(361, config))
        assertEquals(20, WorkTimeCalculator.requiredBreakMinutes(570, config))
        assertEquals(521, WorkTimeCalculator.calculateNetMinutes(571, config))
        assertEquals(50, WorkTimeCalculator.requiredBreakMinutes(571, config))
    }

    @Test
    fun `second break cannot be shorter than first break`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkTimeCalculator.BreakConfig(
                firstBreakMinutes = 45,
                secondBreakMinutes = 30
            )
        }
    }

    @Test
    fun `gross target calculation accounts for break discontinuities`() {
        assertEquals(360, WorkTimeCalculator.grossMinutesToReachNetTarget(360))
        assertEquals(391, WorkTimeCalculator.grossMinutesToReachNetTarget(361))
        assertEquals(570, WorkTimeCalculator.grossMinutesToReachNetTarget(540))
        assertEquals(586, WorkTimeCalculator.grossMinutesToReachNetTarget(541))
        assertEquals(645, WorkTimeCalculator.grossMinutesToReachNetTarget(600))
    }

    @Test
    fun `gross target calculation uses custom breaks`() {
        val config = WorkTimeCalculator.BreakConfig(
            firstBreakMinutes = 40,
            secondBreakMinutes = 60
        )

        assertEquals(401, WorkTimeCalculator.grossMinutesToReachNetTarget(361, config))
        assertEquals(630, WorkTimeCalculator.grossMinutesToReachNetTarget(570, config))
    }

    @Test
    fun `feierabend includes mandatory break for eight hour day`() {
        // 8 Std. Netto → 8:30 Brutto (30 Min. Pause)
        assertEquals(
            1_000L + 510 * 60_000L,
            WorkTimeCalculator.feierabendMillis(
                startTimeMillis = 1_000L,
                dailyTargetMinutes = 8 * 60
            )
        )
    }

    @Test
    fun `wochenende is null when remaining exceeds legal day maximum`() {
        assertEquals(
            null,
            WorkTimeCalculator.wochenendeMillis(
                startTimeMillis = 1_000L,
                weeklyTargetMinutes = 39 * 60,
                completedWeekMinutes = 0
            )
        )
    }

    @Test
    fun `wochenende is null when week goal already reached`() {
        assertEquals(
            null,
            WorkTimeCalculator.wochenendeMillis(
                startTimeMillis = 1_000L,
                weeklyTargetMinutes = 39 * 60,
                completedWeekMinutes = 39 * 60
            )
        )
    }

    @Test
    fun `wochenende returns time when remaining fits today`() {
        // 30 Min. Rest → keine Pause nötig
        assertEquals(
            1_000L + 30 * 60_000L,
            WorkTimeCalculator.wochenendeMillis(
                startTimeMillis = 1_000L,
                weeklyTargetMinutes = 39 * 60,
                completedWeekMinutes = 38 * 60 + 30
            )
        )
    }

    @Test
    fun `manual breaks covering mandatory break are deducted without extra pause`() {
        // 08:30–17:00 = 510 Min. Brutto → 30 Min. Pflichtpause
        val result = WorkTimeCalculator.calculateWithManualBreaks(
            startMinutes = 8 * 60 + 30,
            endMinutes = 17 * 60,
            breaks = listOf(
                WorkTimeCalculator.ManualBreak(
                    startMinutes = 12 * 60,
                    endMinutes = 12 * 60 + 30
                )
            )
        )

        assertEquals(510, result.grossMinutes)
        assertEquals(30, result.manualBreakMinutes)
        assertEquals(30, result.requiredBreakMinutes)
        assertEquals(0, result.addedMandatoryMinutes)
        assertEquals(true, result.mandatoryBreakCovered)
        assertEquals(480, result.netMinutes)
    }

    @Test
    fun `short manual break triggers added mandatory break`() {
        val result = WorkTimeCalculator.calculateWithManualBreaks(
            startMinutes = 8 * 60 + 30,
            endMinutes = 17 * 60,
            breaks = listOf(
                WorkTimeCalculator.ManualBreak(
                    startMinutes = 12 * 60,
                    endMinutes = 12 * 60 + 15
                )
            )
        )

        assertEquals(15, result.manualBreakMinutes)
        assertEquals(30, result.requiredBreakMinutes)
        assertEquals(15, result.addedMandatoryMinutes)
        assertEquals(false, result.mandatoryBreakCovered)
        assertEquals(480, result.netMinutes)
    }

    @Test
    fun `longer manual breaks are fully deducted when mandatory is covered`() {
        val result = WorkTimeCalculator.calculateWithManualBreaks(
            startMinutes = 8 * 60,
            endMinutes = 17 * 60,
            breaks = listOf(
                WorkTimeCalculator.ManualBreak(12 * 60, 12 * 60 + 30),
                WorkTimeCalculator.ManualBreak(15 * 60, 15 * 60 + 15)
            )
        )

        assertEquals(45, result.manualBreakMinutes)
        assertEquals(30, result.requiredBreakMinutes)
        assertEquals(0, result.addedMandatoryMinutes)
        assertEquals(540 - 45, result.netMinutes)
    }

    @Test
    fun `no manual breaks still deducts mandatory break`() {
        val result = WorkTimeCalculator.calculateWithManualBreaks(
            startMinutes = 8 * 60 + 30,
            endMinutes = 17 * 60,
            breaks = emptyList()
        )

        assertEquals(0, result.manualBreakMinutes)
        assertEquals(30, result.addedMandatoryMinutes)
        assertEquals(480, result.netMinutes)
    }
}