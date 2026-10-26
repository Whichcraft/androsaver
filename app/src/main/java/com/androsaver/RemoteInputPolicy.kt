package com.androsaver

/** Key pass-through is independent of DOWN/UP so Android receives complete sequences. */
internal object RemoteInputPolicy {
    private val volume = setOf(24, 25, 164)
    private val blankMedia = setOf(19, 20, 21, 22, 85, 86, 87, 88, 89, 90, 126, 127)
    fun passThrough(blank: Boolean, key: Int) = key in volume || (blank && key in blankMedia)
}
