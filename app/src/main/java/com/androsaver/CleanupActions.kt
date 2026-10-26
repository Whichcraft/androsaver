package com.androsaver

/** Attempt every release even when an earlier resource cannot be cleaned up. */
internal object CleanupActions {
    data class Failure(val phase: String, val error: Throwable)
    fun run(vararg actions: Pair<String, () -> Unit>): List<Failure> = buildList {
        for ((phase, action) in actions) {
            try { action() } catch (error: Throwable) { add(Failure(phase, error)) }
        }
    }
}
