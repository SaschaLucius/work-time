package de.worktime.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun detectsNewerPatch() {
        assertTrue(isNewerVersion("v1.0.1", "1.0.0"))
    }

    @Test
    fun detectsNewerMinor() {
        assertTrue(isNewerVersion("1.1.0", "1.0.9"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(isNewerVersion("v1.2.3", "1.2.3"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(isNewerVersion("v1.0.0", "1.1.0"))
    }

    @Test
    fun handlesDifferentSegmentCounts() {
        assertTrue(isNewerVersion("v2", "1.9.9"))
        assertFalse(isNewerVersion("v1.0", "1.0.1"))
    }
}
