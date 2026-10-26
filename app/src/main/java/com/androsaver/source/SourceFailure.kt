package com.androsaver.source

import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException
import com.androsaver.source.ImageSourceResult.FailureKind

/** Only typed metadata crosses the diagnostic boundary. Never retain messages, URLs or credentials. */
internal fun sourceFailure(error: Exception): ImageSourceResult.Failure {
    val causes = mutableListOf<Throwable>()
    var current: Throwable? = error
    while (current != null && causes.size < 12 && current !in causes) {
        causes += current
        current = current.cause
    }
    val kind = when {
        error is ImageSourceAuthenticationException -> FailureKind.AUTHENTICATION
        error is ImageSourcePermissionException -> FailureKind.PERMISSION
        error is ImageSourceHttpException -> when {
            error.code == 401 || error.code == 403 -> FailureKind.AUTHENTICATION
            com.androsaver.PrefetchRetryPolicy.http(error.code) -> FailureKind.NETWORK
            else -> FailureKind.UNKNOWN
        }
        causes.any { it is CertificateExpiredException } -> FailureKind.TLS_EXPIRED
        causes.any { it is CertificateNotYetValidException } -> FailureKind.TLS_NOT_YET_VALID
        causes.any { it is CertPathValidatorException } -> FailureKind.TLS_UNTRUSTED
        error is SSLPeerUnverifiedException -> FailureKind.TLS_IDENTITY
        causes.any { it is SSLException } -> FailureKind.TLS
        error is java.io.IOException -> FailureKind.NETWORK
        error is com.google.gson.JsonParseException -> FailureKind.PARSE
        else -> FailureKind.UNKNOWN
    }
    return ImageSourceResult.Failure(kind, causes.map { it.javaClass.name }, (error as? ImageSourceHttpException)?.code)
}
