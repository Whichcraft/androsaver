package com.androsaver.auth

import android.content.SharedPreferences
import com.androsaver.Prefs

/** Tickets never leave memory or appear in logs. Configuration is checked too. */
internal object OAuthAccounts {
    private val transactions = AccountTransactions()
    private fun values(prefs: SharedPreferences, provider: String, replacement: Boolean): List<String?> {
        val keys = when (provider) {
            "google" -> listOf(Prefs.GOOGLE_CLIENT_ID, Prefs.GOOGLE_CLIENT_SECRET, com.androsaver.source.SourceIdentity.key(provider))
            "onedrive" -> listOf(Prefs.ONEDRIVE_CLIENT_ID, com.androsaver.source.SourceIdentity.key(provider))
            "dropbox" -> listOf(Prefs.DROPBOX_APP_KEY, Prefs.DROPBOX_APP_SECRET, com.androsaver.source.SourceIdentity.key(provider))
            else -> error("Unknown OAuth provider")
        }
        val refreshKey = when (provider) {
            "google" -> Prefs.GOOGLE_REFRESH_TOKEN
            "onedrive" -> Prefs.ONEDRIVE_REFRESH_TOKEN
            else -> Prefs.DROPBOX_REFRESH_TOKEN
        }
        return (if (replacement) keys else keys + refreshKey).map { prefs.getString(it, null) }
    }
    fun capture(prefs: SharedPreferences, provider: String, replacement: Boolean = true): AccountTransactions.Ticket {
        com.androsaver.source.SourceIdentity.scope(prefs, provider)
        return transactions.capture(provider, replacement) { values(prefs, provider, replacement) }
    }
    fun publish(prefs: SharedPreferences, ticket: AccountTransactions.Ticket, write: () -> Unit) =
        transactions.publish(ticket, { values(prefs, ticket.provider, ticket.replacement) }, write)
    fun <T> invalidate(provider: String, clear: () -> T) = transactions.invalidate(provider, clear)
}
