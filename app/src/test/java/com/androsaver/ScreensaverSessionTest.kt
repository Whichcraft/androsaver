package com.androsaver

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ScreensaverSessionTest {
    @Test fun stopCancelsSessionWorkWithoutCancellingTheHost() = runBlocking {
        val session = ScreensaverSession(this)
        session.start("slideshow")
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val loading = session.scope.launch {
            try { started.complete(Unit); awaitCancellation() }
            finally { finished.complete(Unit) }
        }
        started.await()
        val previousScope = session.scope
        val generation = session.generation
        session.stop()
        loading.join()
        assertTrue(finished.isCompleted)
        assertFalse(previousScope.isActive)
        assertNull(session.mode)
        assertTrue(session.generation > generation)
        assertTrue(isActive)
        session.start("static")
        assertEquals("static", session.mode)
        assertTrue(session.scope.isActive)
        assertFalse(previousScope.isActive)
        session.stop()
    }
}
