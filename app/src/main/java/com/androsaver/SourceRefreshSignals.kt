package com.androsaver

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal object SourceRefreshSignals {
    private val mutable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events = mutable.asSharedFlow()
    fun request() { mutable.tryEmit(Unit) }
}
