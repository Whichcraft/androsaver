package com.androsaver.auth

/** Serialises account replacement with publication of in-flight network responses. */
internal class AccountTransactions {
    class Ticket internal constructor(
        internal val provider: String, internal val epoch: Long,
        internal val values: List<String?>
    )
    private val epochs = mutableMapOf<String, Long>()

    @Synchronized fun capture(provider: String, read: () -> List<String?>): Ticket =
        Ticket(provider, epochs[provider] ?: 0L, read())

    @Synchronized fun publish(ticket: Ticket, read: () -> List<String?>, write: () -> Unit): Boolean {
        if ((epochs[ticket.provider] ?: 0L) != ticket.epoch || read() != ticket.values) return false
        write()
        epochs[ticket.provider] = ticket.epoch + 1L
        return true
    }

    @Synchronized fun <T> invalidate(provider: String, clear: () -> T): T {
        epochs[provider] = (epochs[provider] ?: 0L) + 1L
        return clear()
    }
}
