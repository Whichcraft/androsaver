package com.androsaver

import com.androsaver.source.ImageSourceResult.FailureKind

internal object PrefetchRetryPolicy {
    fun http(code: Int): Boolean = code == 408 || code == 425 || code == 429 || code in 500..599
    fun source(kind: FailureKind): Boolean = kind == FailureKind.NETWORK || kind == FailureKind.TIMEOUT
    fun retry(attempt: Int, transientFailure: Boolean): Boolean = transientFailure && attempt < 3
}
