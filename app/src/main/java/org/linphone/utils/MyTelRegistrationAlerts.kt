package org.linphone.utils

/** Registration alerts are derived from currently configured accounts, never a past callback. */
object MyTelRegistrationAlerts {
    data class Account(val isDefault: Boolean, val enabled: Boolean, val failed: Boolean, val cleared: Boolean)

    data class Alerts(val defaultDisabled: Boolean, val defaultFailed: Boolean, val otherFailed: Boolean)

    fun resolve(networkReachable: Boolean, accounts: List<Account>): Alerts {
        val default = accounts.firstOrNull { it.isDefault }
        return Alerts(
            defaultDisabled = default != null && (!default.enabled || default.cleared),
            defaultFailed = default?.enabled == true && default.failed,
            otherFailed = networkReachable && accounts.any { !it.isDefault && it.enabled && it.failed }
        )
    }
}
