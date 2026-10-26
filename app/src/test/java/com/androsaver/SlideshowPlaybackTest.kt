package com.androsaver

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SlideshowPlaybackTest {
    @Test fun laterHealthySourceRestartsPlaybackAfterTheFirstSourceExhaustsItsImages() = runBlocking {
        withTimeout(2000) {
            val playback = SlideshowPlayback()
            val releaseHealthy = CompletableDeferred<Unit>()
            val stalled = CompletableDeferred<Unit>()
            var restarted = false
            val loading = launch {
                ProgressiveSources.load(listOf("broken", "healthy"), fetch = { source ->
                    if (source == "healthy") releaseHealthy.await()
                    listOf(source)
                }, onItems = { source, _ ->
                    if (source == "broken") {
                        playback.loading()
                        assertFalse(playback.failed(1))
                        assertTrue(playback.requestFallback())
                        stalled.complete(Unit)
                    } else {
                        restarted = playback.resumeWithNewItems()
                        playback.ready()
                    }
                })
            }
            stalled.await()
            assertTrue(playback.isStalled)
            releaseHealthy.complete(Unit)
            loading.join()
            assertTrue(restarted)
            assertEquals(SlideshowPlayback.State.WAITING, playback.state)
        }
    }

    @Test fun brokenOfflineCacheCannotRestartItselfForever() {
        val playback = SlideshowPlayback()
        playback.loading()
        assertTrue(playback.failed(2))
        assertFalse(playback.failed(2))
        assertTrue(playback.requestFallback())
        playback.fallbackFinished(used = true)
        playback.loading()
        assertFalse(playback.failed(1))
        assertFalse(playback.requestFallback())
        assertTrue(playback.resumeWithNewItems())
    }

    @Test fun fallbackSupersededByNewImagesDoesNotConsumeTheOnlyAttempt() {
        val playback = SlideshowPlayback()
        playback.loading()
        assertFalse(playback.failed(1))
        assertTrue(playback.requestFallback())
        assertTrue(playback.resumeWithNewItems())
        playback.fallbackFinished(used = false)
        assertFalse(playback.failed(1))
        assertTrue(playback.requestFallback())
    }

    @Test fun laterImagesDoNotRestartAnAlreadyScheduledSlide() {
        val playback = SlideshowPlayback()
        playback.ready()
        assertFalse(playback.resumeWithNewItems())
        assertEquals(SlideshowPlayback.State.WAITING, playback.state)
        playback.reset()
        assertEquals(SlideshowPlayback.State.IDLE, playback.state)
    }
}
