package com.juying.app.source

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryPresentationTest {
    @Test
    fun historyPeriodsUseRequestedBoundaries() {
        val now = 200L * 24L * 60L * 60L * 1000L
        assertEquals("近一周", historyPeriodLabel(now - 2L * 24L * 60L * 60L * 1000L, now))
        assertEquals("近一月", historyPeriodLabel(now - 10L * 24L * 60L * 60L * 1000L, now))
        assertEquals("近半年", historyPeriodLabel(now - 60L * 24L * 60L * 60L * 1000L, now))
        assertEquals("更早", historyPeriodLabel(now - 190L * 24L * 60L * 60L * 1000L, now))
    }

    @Test
    fun historyProgressIsClamped() {
        assertEquals(25, historyProgressPercent(15_000L, 60_000L))
        assertEquals(100, historyProgressPercent(90_000L, 60_000L))
        assertEquals(0, historyProgressPercent(1_000L, 0L))
    }
}
