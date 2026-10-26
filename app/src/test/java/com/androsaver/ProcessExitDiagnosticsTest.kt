package com.androsaver

import org.junit.Assert.*
import org.junit.Test

class ProcessExitDiagnosticsTest {
    @Test fun androidExitReasonsDistinguishNativeCrashesAnrsAndMemoryPressure() {
        assertEquals("NATIVE_CRASH", ProcessExitDiagnostics.reason(5))
        assertEquals("ANR", ProcessExitDiagnostics.reason(6))
        assertEquals("LOW_MEMORY", ProcessExitDiagnostics.reason(3))
    }
    @Test fun traceExtractionKeepsFramesWithoutArbitraryMessages() {
        val trace = "thread private-user-data\njava.lang.Error: nonfunctional-secret-placeholder\n  at com.androsaver.Example.render(Example.kt:1)\n"
        assertEquals("at com.androsaver.Example.render(Example.kt:1)", ProcessExitDiagnostics.stackFrames(trace))
    }
}
