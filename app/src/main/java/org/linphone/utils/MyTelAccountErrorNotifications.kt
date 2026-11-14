package org.linphone.utils

/** Only current, enabled account failures own persistent registration notices. */
object MyTelAccountErrorNotifications {
    data class Account(
        val identity: String,
        val registrationEnabled: Boolean,
        val registrationFailed: Boolean,
        val pushAvailable: Boolean
    )

    fun failedIdentities(accounts: List<Account>, keepAlive: Boolean): Set<String> {
        if (!keepAlive) return emptySet()
        return accounts.asSequence()
            .filter { it.identity.isNotEmpty() && it.registrationEnabled && it.registrationFailed && !it.pushAvailable }
            .map { it.identity }
            .toSet()
    }

    fun staleIds(notificationIds: Set<Int>, retainedIdentities: Set<String>): Set<Int> {
        val retainedIds = retainedIdentities.map { it.hashCode() }.toSet()
        return notificationIds - retainedIds
    }
}
