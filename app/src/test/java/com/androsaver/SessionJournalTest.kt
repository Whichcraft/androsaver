package com.androsaver

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionJournalTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cleanRestartDoesNotReplayAnOlderInterruptionInTheStartupOverlay() {
        val file = File(temporary.root, "report.txt")
        SessionJournal().begin(file, "static")
        val journal = SessionJournal()
        val interrupted = journal.begin(file, "slideshow")
        assertNotNull(ScreensaverPostmortem.readReports(file, includeSourceFailures = false,
            includeInterruptedSession = interrupted.previousInterruption != null))
        journal.end(interrupted.id)

        repeat(2) {
            val clean = journal.begin(file, "visualizer")
            assertNull(clean.previousInterruption)
            assertNull(ScreensaverPostmortem.readReports(file, includeSourceFailures = false,
                includeInterruptedSession = clean.previousInterruption != null))
            // The diagnostic history remains available on explicit inspection.
            assertTrue(ScreensaverPostmortem.readReports(file)!!.contains("CLEAN SHUTDOWN"))
            journal.end(clean.id)
        }

        ScreensaverPostmortem.write(file, "saved fatal")
        ScreensaverPostmortem.writeRecoverable(file, "saved recoverable")
        val failures = ScreensaverPostmortem.readReports(file, includeInterruptedSession = false)!!
        assertTrue(failures.contains("saved fatal"))
        assertTrue(failures.contains("saved recoverable"))
        assertFalse(failures.contains("CLEAN SHUTDOWN"))
    }
    @Test fun failedCleanupCannotBeHiddenByAnotherSessionStoppingCleanly() {
        val file = File(temporary.root, "report.txt")
        val journal = SessionJournal()
        val bad = journal.begin(file, "static")
        val good = journal.begin(file, "visualizer")
        journal.end(bad.id, clean = false)
        journal.end(good.id)
        assertTrue(File(file.parentFile, "report.txt.active").isFile)
        assertTrue(ScreensaverPostmortem.readReports(file)!!.contains("CLEANUP DID NOT COMPLETE"))
        val restarted = journal.begin(file, "slideshow")
        assertTrue(restarted.previousInterruption!!.contains("static"))
        journal.end(restarted.id)
        assertFalse(File(file.parentFile, "report.txt.active").exists())
    }

    @Test fun processRestartReportsAnUncleanSessionWithoutInventingACrashCause() {
        val file = File(temporary.root, "report.txt")
        SessionJournal().begin(file, "static")
        val restarted = SessionJournal()
        val next = restarted.begin(file, "slideshow")
        assertTrue(next.previousInterruption!!.contains("static"))
        assertTrue(next.previousInterruption!!.contains("Cause unavailable"))
        assertNull(ScreensaverPostmortem.read(file))
        assertTrue(ScreensaverPostmortem.readReports(file)!!.contains("CLEAN SHUTDOWN"))
        restarted.end(next.id)
        assertNull(SessionJournal().begin(file, "visualizer").previousInterruption)
    }
    @Test fun overlappingSessionsAndCleanRestartsAreNotReportedAsCrashes() {
        val journal = SessionJournal()
        val file = File(temporary.root, "report.txt")
        val first = journal.begin(file, "static")
        val second = journal.begin(file, "visualizer")
        assertNull(second.previousInterruption)
        journal.end(first.id)
        assertTrue(File(file.parentFile, "report.txt.active").isFile)
        journal.end(second.id)
        assertFalse(File(file.parentFile, "report.txt.active").exists())
        assertNull(SessionJournal().begin(file, "blank").previousInterruption)
    }
}
