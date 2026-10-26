package com.androsaver

import org.junit.Assert.*
import org.junit.Test

class CleanupActionsTest {
    @Test fun allResourcesAreReleasedAndEveryFailureRetainsItsPhaseAndCause() {
        val released = mutableListOf<String>()
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        val failures = CleanupActions.run(
            "images" to { throw first },
            "audio" to { released += "audio" },
            "weather" to { throw second },
            "lease" to { released += "lease" })
        assertEquals(listOf("audio", "lease"), released)
        assertEquals(listOf("images", "weather"), failures.map { it.phase })
        assertEquals(listOf(first, second), failures.map { it.error })
    }
}
