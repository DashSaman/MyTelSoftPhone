package org.linphone.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.linphone.core.RegistrationState

class MyTelConnectionStatusTest {
    @Test
    fun registered_account_on_reachable_network_is_online() {
        assertEquals(MyTelConnectionStatus.Online, MyTelConnectionStatus.resolve(true, RegistrationState.Ok))
    }

    @Test
    fun lost_network_is_offline_even_with_stale_registered_account() {
        assertEquals(MyTelConnectionStatus.Offline, MyTelConnectionStatus.resolve(false, RegistrationState.Ok))
    }

    @Test
    fun registration_in_progress_is_connecting() {
        assertEquals(MyTelConnectionStatus.Connecting, MyTelConnectionStatus.resolve(true, RegistrationState.Progress))
    }

    @Test
    fun missing_account_is_distinct_from_failed_registration() {
        assertEquals(MyTelConnectionStatus.NoAccount, MyTelConnectionStatus.resolve(true, null))
        assertEquals(MyTelConnectionStatus.Offline, MyTelConnectionStatus.resolve(true, RegistrationState.Failed))
    }
}
