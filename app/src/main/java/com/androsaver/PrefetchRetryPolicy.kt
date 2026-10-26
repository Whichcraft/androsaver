package com.androsaver

import com.androsaver.source.ImageSourceResult.FailureKind

internal object PrefetchRetryPolicy {
    fun http(code: Int): Boolean = code == 408 || code == 425 || code == 429 || code in 500..599
    fun source(kind: FailureKind): Boolean = kind == FailureKind.NETWORK || kind == FailureKind.TIMEOUT
    fun source(result: com.androsaver.source.ImageSourceResult): Boolean = when (result) {
        is com.androsaver.source.ImageSourceResult.Failure -> source(result.kind)
        is com.androsaver.source.ImageSourceResult.Success -> result.retryableFailures
        else -> false
    }
    fun retry(attempt: Int, transientFailure: Boolean): Boolean = transientFailure && attempt < 3
}
