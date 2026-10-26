package com.androsaver.visualizer

/** Reports signal separately from permission and backend availability; never records audio. */
internal class AudioCaptureMonitor(private val now: () -> Long = System::nanoTime) {
    enum class Status { STOPPED, STARTING, ACTIVE, NO_SIGNAL, PERMISSION_REQUIRED, UNAVAILABLE }
    private var state = Status.STOPPED
    private var lastSignal: Long? = null
    @Synchronized fun starting() { state = Status.STARTING; lastSignal = null }
    @Synchronized fun attached() { state = Status.NO_SIGNAL }
    @Synchronized fun permissionRequired() { state = Status.PERMISSION_REQUIRED }
    @Synchronized fun unavailable() { state = Status.UNAVAILABLE }
    @Synchronized fun stopped() { state = Status.STOPPED; lastSignal = null }
    @Synchronized fun sample(hasSignal: Boolean) {
        if (state != Status.NO_SIGNAL && state != Status.ACTIVE) return
        if (hasSignal) { lastSignal = now(); state = Status.ACTIVE }
    }
    @Synchronized fun status(): Status {
        if (state == Status.ACTIVE && lastSignal?.let { now() - it > 3_000_000_000L } != false) state = Status.NO_SIGNAL
        return state
    }
}
