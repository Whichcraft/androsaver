package com.androsaver

import com.androsaver.source.*
import com.androsaver.source.ImageSourceResult.FailureKind
import org.junit.Assert.*
import org.junit.Test
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLHandshakeException

class SourceFailureTest {
    @Test fun certificateCauseAndHttpStatusSurviveWithoutMessagesOrCredentials() {
        val error = SSLHandshakeException("https://private.invalid/?token=nonfunctional-placeholder")
        error.initCause(CertificateExpiredException("private certificate subject"))
        val failure = sourceFailure(error)
        assertEquals(FailureKind.TLS_EXPIRED, failure.kind)
        assertEquals(listOf(SSLHandshakeException::class.java.name, CertificateExpiredException::class.java.name), failure.exceptionTypes)
        assertFalse(failure.toString().contains("private"))
        val http = sourceFailure(ImageSourceHttpException(429))
        assertEquals(FailureKind.NETWORK, http.kind)
        assertEquals(429, http.httpStatus)
        assertFalse(PrefetchRetryPolicy.source(failure))
    }
}
