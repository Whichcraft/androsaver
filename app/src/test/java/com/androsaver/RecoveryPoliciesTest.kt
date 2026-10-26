package com.androsaver

import android.view.KeyEvent
import com.androsaver.source.ImageSourceResult.FailureKind
import com.androsaver.source.ImageSource
import com.androsaver.source.ImageSourceResult
import com.androsaver.source.ImageSourceHttpException
import com.androsaver.source.ImageItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RecoveryPoliciesTest {
    @Test fun sourceEnumerationPreservesPermanentVersusTransientHttpFailures() = runBlocking {
        for (code in listOf(401, 403, 404, 429, 503)) {
            val source = object : ImageSource {
                override val name = "test"
                override fun isConfigured() = true
                override suspend fun getImageUrls(): List<ImageItem> = throw ImageSourceHttpException(code)
            }
            val failure = source.enumerate() as ImageSourceResult.Failure
            assertEquals(code == 429 || code == 503, PrefetchRetryPolicy.source(failure.kind))
        }
    }

    @Test fun permanentFailuresAreNotRetriedAndTransientRetriesAreBounded() {
        listOf(400, 401, 403, 404, 413, 415).forEach { assertFalse(PrefetchRetryPolicy.http(it)) }
        listOf(408, 425, 429, 500, 503).forEach { assertTrue(PrefetchRetryPolicy.http(it)) }
        listOf(FailureKind.AUTHENTICATION, FailureKind.PERMISSION, FailureKind.PARSE).forEach {
            assertFalse(PrefetchRetryPolicy.source(it))
        }
        assertTrue(PrefetchRetryPolicy.source(FailureKind.NETWORK))
        assertTrue(PrefetchRetryPolicy.retry(0, true))
        assertFalse(PrefetchRetryPolicy.retry(3, true))
        assertFalse(PrefetchRetryPolicy.retry(0, false))
    }

    @Test fun diagnosticRemoteActionsScrollDismissAndPreserveVolumeKeys() {
        assertEquals(DiagnosticControls.Action.UP, DiagnosticControls.action(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(DiagnosticControls.Action.DOWN, DiagnosticControls.action(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(DiagnosticControls.Action.PAGE_DOWN, DiagnosticControls.action(KeyEvent.KEYCODE_DPAD_RIGHT))
        listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER).forEach {
            assertEquals(DiagnosticControls.Action.DISMISS, DiagnosticControls.action(it))
        }
        assertEquals(DiagnosticControls.Action.PASS, DiagnosticControls.action(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(100, DiagnosticControls.scrollPosition(0, 300, 1000, DiagnosticControls.Action.DOWN))
        assertEquals(700, DiagnosticControls.scrollPosition(650, 300, 1000, DiagnosticControls.Action.PAGE_DOWN))
        assertEquals(0, DiagnosticControls.scrollPosition(0, 300, 100, DiagnosticControls.Action.UP))
    }
}
