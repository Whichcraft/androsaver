package com.androsaver

import java.util.Calendar

object ScheduleWindow {
    fun isActive(hour: Int, start: Int, end: Int): Boolean {
        if (start == end) return true
        return if (start < end) hour in start until end else hour >= start || hour < end
    }

    fun millisUntilEnd(now: Calendar, start: Int, end: Int): Long? {
        if (start == end) return null
        val active = isActive(now.get(Calendar.HOUR_OF_DAY), start, end)
        if (!active) return null
        val boundary = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, end)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        return (boundary.timeInMillis - now.timeInMillis).coerceAtLeast(1L)
    }
}
