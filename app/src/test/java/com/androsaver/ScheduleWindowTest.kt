package com.androsaver

import java.util.Calendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleWindowTest {
    @Test fun disabledScheduleNeverBlocksStartupOrArmsShutdown() {
        for ((start, end) in listOf(8 to 22, 22 to 6, 8 to 8)) {
            for (hour in 0..23) {
                val now = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour) }
                assertTrue(ScheduleWindow.isActive(hour, start, end, enabled = false))
                assertNull(ScheduleWindow.millisUntilEnd(now, start, end, enabled = false))
            }
        }
    }

    @Test fun equalHoursMeanAllDay() {
        assertTrue(ScheduleWindow.isActive(0, 8, 8))
        assertTrue(ScheduleWindow.isActive(23, 8, 8))
    }

    @Test fun daytimeWindowExcludesEndHour() {
        assertTrue(ScheduleWindow.isActive(8, 8, 22))
        assertTrue(ScheduleWindow.isActive(21, 8, 22))
        assertFalse(ScheduleWindow.isActive(22, 8, 22))
    }

    @Test fun overnightWindowWrapsMidnight() {
        assertTrue(ScheduleWindow.isActive(23, 22, 6))
        assertTrue(ScheduleWindow.isActive(2, 22, 6))
        assertFalse(ScheduleWindow.isActive(12, 22, 6))
    }

    @Test fun endDelayTargetsNextBoundary() {
        val now = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 30) }
        val delay = ScheduleWindow.millisUntilEnd(now, 22, 6)
        assertTrue(delay != null && delay > 0)
    }
}
