package com.androsaver

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScreensaverPostmortemTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun clearingReportsKeepsActiveSessionEvidenceAndOtherAppFiles() {
        val file = File(temporary.root, "report.txt")
        val active = File(temporary.root, "report.txt.active").apply { writeText("active") }
        val unrelated = File(temporary.root, "settings.txt").apply { writeText("keep") }
        ScreensaverPostmortem.write(file, "fatal")
        ScreensaverPostmortem.writeRecoverable(file, "recoverable")
        assertTrue(ScreensaverPostmortem.clearReports(file))
        assertNull(ScreensaverPostmortem.readReports(file))
        assertTrue(active.exists())
        assertTrue(unrelated.exists())
    }

    @Test fun recoverableStartupErrorsCannotOverwriteTheSavedCrash() {
        val file = File(temporary.root, "report.txt")
        ScreensaverPostmortem.write(file, "original fatal failure")
        val previous = ScreensaverPostmortem.readReports(file)!!
        ScreensaverPostmortem.writeRecoverable(file, "new startup failure")
        assertEquals("original fatal failure", ScreensaverPostmortem.read(file))
        assertTrue(previous.contains("original fatal failure"))
        assertFalse(previous.contains("new startup failure"))
        val restored = ScreensaverPostmortem.readReports(file)!!
        assertTrue(restored.contains("original fatal failure"))
        assertTrue(restored.contains("new startup failure"))
    }

    @Test fun overlappingSessionsUseOneHandlerAndDelegateOnce() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        var delegated = 0
        val delegate = Thread.UncaughtExceptionHandler { _, _ -> delegated++ }
        val file = File(temporary.root, "report.txt")
        var first: Long? = null
        var second: Long? = null
        try {
            Thread.setDefaultUncaughtExceptionHandler(delegate)
            first = ScreensaverPostmortem.register(file, "static")
            val handler = Thread.getDefaultUncaughtExceptionHandler()!!
            second = ScreensaverPostmortem.register(file, "visualizer")
            assertSame(handler, Thread.getDefaultUncaughtExceptionHandler())
            handler.uncaughtException(Thread.currentThread(), IllegalStateException("test crash"))
            assertEquals(1, delegated)
            val report = ScreensaverPostmortem.read(file)!!
            assertTrue(report.contains("static, visualizer"))
            assertTrue(report.contains("test crash"))
            // Stop the older session first: the shared handler must stay alive.
            ScreensaverPostmortem.unregister(requireNotNull(first))
            assertSame(handler, Thread.getDefaultUncaughtExceptionHandler())
            handler.uncaughtException(Thread.currentThread(), IllegalStateException("second crash"))
            assertFalse(ScreensaverPostmortem.read(file)!!.contains("static"))
            assertEquals(2, delegated)
            ScreensaverPostmortem.unregister(requireNotNull(second))
            assertSame(delegate, Thread.getDefaultUncaughtExceptionHandler())
        } finally {
            first?.let { ScreensaverPostmortem.unregister(it) }
            second?.let { ScreensaverPostmortem.unregister(it) }
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    @Test fun reportsAreBoundedRedactedAndCanBeReadRepeatedly() {
        val file = File(temporary.root, "report.txt")
        ScreensaverPostmortem.write(file, "HTTPS://example.invalid/?token=placeholder\n" + "x".repeat(70_000))
        val report = ScreensaverPostmortem.read(file)!!
        assertTrue(report.startsWith("[URL redacted]\n"))
        assertFalse(report.contains("placeholder"))
        assertEquals(64_000, report.length)
        assertEquals(report, ScreensaverPostmortem.read(file))
        assertFalse(File(temporary.root, "report.txt.tmp").exists())
    }

    @Test fun failedWritePreservesPreviousReport() {
        val file = File(temporary.root, "report.txt")
        ScreensaverPostmortem.write(file, "previous")
        assertTrue(File(temporary.root, "report.txt.tmp").mkdir())
        ScreensaverPostmortem.write(file, "replacement")
        assertEquals("previous", ScreensaverPostmortem.read(file))
    }
}
