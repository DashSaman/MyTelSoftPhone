package org.linphone.utils

import org.linphone.core.RegistrationState

/** Resolves a snapshot taken on the Linphone core thread. */
enum class MyTelConnectionStatus {
    Online,
    Connecting,
    Offline,
    NoAccount;

    companion object {
        fun resolve(networkReachable: Boolean, registrationState: RegistrationState?): MyTelConnectionStatus = when {
            registrationState == null -> NoAccount
            !networkReachable -> Offline
            registrationState == RegistrationState.Ok -> Online
            registrationState == RegistrationState.Progress -> Connecting
            else -> Offline
        }
    }
}
