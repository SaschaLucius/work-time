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
}