package com.androsaver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Own asynchronous work for exactly one engine start/stop interval. */
internal class ScreensaverSession(private val parent: CoroutineScope) {
    @Volatile var generation: Long = 0
        private set
    @Volatile var mode: String? = null
        private set
    var scope = CoroutineScope(parent.coroutineContext + Job().apply { cancel() })
        private set

    fun start(mode: String) {
        stop()
        this.mode = mode
        scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    }

    fun stop() {
        generation++
        mode = null
        scope.cancel()
    }
}
