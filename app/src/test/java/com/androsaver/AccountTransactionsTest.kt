package com.androsaver

import com.androsaver.auth.AccountTransactions
import org.junit.Assert.*
import org.junit.Test

class AccountTransactionsTest {
    @Test fun disconnectRejectsResponseEvenIfIdenticalSettingsAreRestored() {
        val state = AccountTransactions()
        var values = listOf<String?>("placeholder-account")
        val ticket = state.capture("provider") { values }
        state.invalidate("provider") { values = emptyList() }
        values = listOf("placeholder-account")
        var written = false
        assertFalse(state.publish(ticket, { values }) { written = true })
        assertFalse(written)
    }
    @Test fun configurationChangesAndNewerSignInRejectOldRefresh() {
        val state = AccountTransactions()
        var values = listOf<String?>("placeholder-client")
        val old = state.capture("provider") { values }
        values = listOf("different-client")
        assertFalse(state.publish(old, { values }) { fail("stale write") })
        val refresh = state.capture("provider") { values }
        val login = state.capture("provider") { values }
        assertTrue(state.publish(login, { values }) { values = listOf("new-account") })
        assertFalse(state.publish(refresh, { values }) { fail("stale write") })
    }
}
