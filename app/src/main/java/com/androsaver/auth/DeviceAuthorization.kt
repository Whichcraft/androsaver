package com.androsaver.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One in-memory session survives collector/Activity recreation; device codes are never saved to disk. */
internal class DeviceAuthorization(
    private val scope: CoroutineScope,
    private val request: suspend () -> DeviceCodeResponse?,
    private val poll: suspend (String) -> AuthResult,
    private val now: () -> Long,
    private val sleep: suspend (Long) -> Unit = { delay(it) }
) {
    enum class Stage { REQUESTING, WAITING, SUCCESS, EXPIRED, ERROR }
    data class State(val stage: Stage = Stage.REQUESTING, val device: DeviceCodeResponse? = null, val error: String? = null)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    @Synchronized fun start() {
        if (job != null) return
        job = scope.launch {
            try {
                val code = request()
                if (code == null) { mutable.value = State(Stage.ERROR); return@launch }
                if (code.expiresIn <= 0) { mutable.value = State(Stage.EXPIRED); return@launch }
                val deadline = now() + code.expiresIn.toLong() * 1000
                var interval = code.interval.toLong().coerceAtLeast(5) * 1000
                mutable.value = State(Stage.WAITING, code)
                while (now() < deadline) {
                    sleep(minOf(interval, deadline - now()))
                    val remaining = deadline - now()
                    if (remaining <= 0) break
                    val result = withTimeoutOrNull(remaining) { poll(code.deviceCode) } ?: break
                    when (result) {
                        is AuthResult.Success -> { mutable.value = State(Stage.SUCCESS); return@launch }
                        AuthResult.Pending -> Unit
                        AuthResult.SlowDown -> interval += 5000
                        AuthResult.Expired -> break
                        is AuthResult.Error -> { mutable.value = State(Stage.ERROR, error = result.message); return@launch }
                    }
                }
                mutable.value = State(Stage.EXPIRED)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.value = State(Stage.ERROR) }
        }
    }
}
