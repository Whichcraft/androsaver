package com.androsaver.auth

import android.content.SharedPreferences
import com.androsaver.Prefs

/** Tickets never leave memory or appear in logs. Configuration is checked too. */
internal object OAuthAccounts {
    private val transactions = AccountTransactions()
    private fun values(prefs: SharedPreferences, provider: String): List<String?> {
        val keys = when (provider) {
            "google" -> listOf(Prefs.GOOGLE_CLIENT_ID, Prefs.GOOGLE_CLIENT_SECRET, Prefs.GOOGLE_ACCESS_TOKEN, Prefs.GOOGLE_REFRESH_TOKEN)
            "onedrive" -> listOf(Prefs.ONEDRIVE_CLIENT_ID, Prefs.ONEDRIVE_ACCESS_TOKEN, Prefs.ONEDRIVE_REFRESH_TOKEN)
            "dropbox" -> listOf(Prefs.DROPBOX_APP_KEY, Prefs.DROPBOX_APP_SECRET, Prefs.DROPBOX_ACCESS_TOKEN, Prefs.DROPBOX_REFRESH_TOKEN)
            else -> error("Unknown OAuth provider")
        }
        return keys.map { prefs.getString(it, null) }
    }
    fun capture(prefs: SharedPreferences, provider: String) =
        transactions.capture(provider) { values(prefs, provider) }
    fun publish(prefs: SharedPreferences, ticket: AccountTransactions.Ticket, write: () -> Unit) =
        transactions.publish(ticket, { values(prefs, ticket.provider) }, write)
    fun <T> invalidate(provider: String, clear: () -> T) = transactions.invalidate(provider, clear)
}
