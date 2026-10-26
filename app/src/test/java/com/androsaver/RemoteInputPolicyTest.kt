package com.androsaver

import org.junit.Assert.*
import org.junit.Test

class RemoteInputPolicyTest {
    @Test fun pairedMediaEventsArePassedThroughInBlankModeRegardlessOfAction() {
        for (key in listOf(85, 87, 88, 126, 127)) {
            val downAndUp = listOf(0, 1).map { RemoteInputPolicy.passThrough(true, key) }
            assertEquals(listOf(true, true), downAndUp)
            assertFalse(RemoteInputPolicy.passThrough(false, key))
        }
        assertTrue(RemoteInputPolicy.passThrough(false, 24))
        assertFalse(RemoteInputPolicy.passThrough(true, 4))
    }
}
