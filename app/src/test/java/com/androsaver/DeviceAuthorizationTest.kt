package com.androsaver

import com.androsaver.auth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class DeviceAuthorizationTest {
    @Test fun reattachingCollectorsDoesNotIssueAnotherCodeOrLoseThePendingSession() = runBlocking {
        var requests = 0
        var now = 0L
        val gate = CompletableDeferred<Unit>()
        val flow = DeviceAuthorization(this, {
            requests++
            DeviceCodeResponse("placeholder-device", "placeholder-user", "https://example.invalid", 60, 5)
        }, { AuthResult.Success("nonfunctional-access", "nonfunctional-refresh") }, { now }, {
            gate.await(); now += it
        })
        flow.start()
        val first = flow.state.first { it.stage == DeviceAuthorization.Stage.WAITING }
        flow.start()
        assertSame(first.device, flow.state.value.device)
        assertEquals(1, requests)
        gate.complete(Unit)
        flow.state.first { it.stage == DeviceAuthorization.Stage.SUCCESS }
        flow.start()
        assertEquals(1, requests)
        assertNull(flow.state.value.device) // tokens and codes do not become completion state
    }
    @Test fun expiryUsesMonotonicTimeAndSlowDownCanExceedSixtySeconds() = runBlocking {
        var now = 0L
        var polls = 0
        val waits = mutableListOf<Long>()
        val flow = DeviceAuthorization(this, {
            DeviceCodeResponse("placeholder", "placeholder", "https://example.invalid", 1000, 5)
        }, {
            if (++polls <= 13) AuthResult.SlowDown else AuthResult.Success("placeholder", "placeholder")
        }, { now }, { waits += it; now += it })
        flow.start()
        flow.state.first { it.stage == DeviceAuthorization.Stage.SUCCESS }
        assertTrue(waits.last() > 60_000)
        var expiredPolls = 0
        val expired = DeviceAuthorization(this, {
            DeviceCodeResponse("placeholder", "placeholder", "https://example.invalid", 1, 5)
        }, { expiredPolls++; AuthResult.Pending }, { now }, { now += it })
        expired.start()
        expired.state.first { it.stage == DeviceAuthorization.Stage.EXPIRED }
        assertEquals(0, expiredPolls)
    }
}
