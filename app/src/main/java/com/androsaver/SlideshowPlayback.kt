package com.androsaver

/** Shared by source delivery, image callbacks and timers; no Android dependencies. */
internal class SlideshowPlayback {
    enum class State { IDLE, LOADING, WAITING, STALLED }
    var state = State.IDLE
        private set
    private var failures = 0
    private var fallbackAttempted = false
    private var fallbackInFlight = false
    val isStalled: Boolean get() = state == State.STALLED

    fun reset() {
        state = State.IDLE
        failures = 0
        fallbackAttempted = false
        fallbackInFlight = false
    }

    fun empty() { state = State.IDLE; failures = 0 }

    fun loading() { state = State.LOADING }
    fun ready() { failures = 0; state = State.WAITING }

    /** True when another image remains to try in this round. */
    fun failed(itemCount: Int): Boolean {
        failures++
        state = if (failures < itemCount) State.WAITING else State.STALLED
        return state == State.WAITING
    }

    fun resumeWithNewItems(): Boolean {
        if (!isStalled) return false
        failures = 0
        state = State.LOADING
        return true
    }

    /** A broken cache must not recursively restart itself. */
    fun requestFallback(): Boolean {
        if (!isStalled || fallbackAttempted || fallbackInFlight) return false
        fallbackInFlight = true
        return true
    }

    fun fallbackFinished(used: Boolean) {
        fallbackInFlight = false
        if (used) fallbackAttempted = true
    }
}
