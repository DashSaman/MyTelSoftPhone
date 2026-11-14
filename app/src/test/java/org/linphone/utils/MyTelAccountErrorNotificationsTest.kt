package org.linphone.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class MyTelAccountErrorNotificationsTest {
    private fun account(
        identity: String,
        failed: Boolean = true,
        enabled: Boolean = true,
        push: Boolean = false
    ) = MyTelAccountErrorNotifications.Account(identity, enabled, failed, push)

    private val existing = "sip:301@192.168.8.171"
    private val removed = "sip:87651@192.0.2.1"

    @Test
    fun removedAccountLateFailureCannotRetainNotification() {
        val keep = MyTelAccountErrorNotifications.failedIdentities(listOf(account(existing, failed = false)), true)
        assertEquals(emptySet<String>(), keep)
        assertEquals(setOf(removed.hashCode()), MyTelAccountErrorNotifications.staleIds(setOf(removed.hashCode()), keep))
    }

    @Test
    fun validConfiguredFailureSurvivesAnotherAccountRemoval() {
        val keep = MyTelAccountErrorNotifications.failedIdentities(listOf(account(existing)), true)
        assertEquals(setOf(existing), keep)
        assertEquals(setOf(removed.hashCode()), MyTelAccountErrorNotifications.staleIds(setOf(existing.hashCode(), removed.hashCode()), keep))
    }

    @Test
    fun restoredNoticeForRemovedIdentityIsOrphanEvenWithoutInMemoryMap() {
        val keep = MyTelAccountErrorNotifications.failedIdentities(emptyList(), true)
        assertEquals(setOf(removed.hashCode()), MyTelAccountErrorNotifications.staleIds(setOf(removed.hashCode()), keep))
    }

    @Test
    fun restoredNoticeForRecoveredAccountIsRemoved() {
        val keep = MyTelAccountErrorNotifications.failedIdentities(listOf(account(existing, failed = false)), true)
        assertEquals(setOf(existing.hashCode()), MyTelAccountErrorNotifications.staleIds(setOf(existing.hashCode()), keep))
    }

    @Test
    fun disabledAndPushAccountsDoNotKeepErrorNotices() {
        val keep = MyTelAccountErrorNotifications.failedIdentities(listOf(account(existing, enabled = false), account(removed, push = true)), true)
        assertEquals(emptySet<String>(), keep)
    }

    @Test
    fun backgroundModeOffDoesNotKeepRegistrationErrorNotices() {
        assertEquals(emptySet<String>(), MyTelAccountErrorNotifications.failedIdentities(listOf(account(existing)), false))
    }

    @Test
    fun emptyIdentityNeverGeneratesTheSharedZeroId() {
        assertEquals(emptySet<String>(), MyTelAccountErrorNotifications.failedIdentities(listOf(account("")), true))
    }

    @Test
    fun configuredFailedDuplicateIdentityStillKeepsItsNotice() {
        val keep = MyTelAccountErrorNotifications.failedIdentities(listOf(account(existing), account(existing, failed = false)), true)
        assertEquals(setOf(existing), keep)
        assertEquals(emptySet<Int>(), MyTelAccountErrorNotifications.staleIds(setOf(existing.hashCode()), keep))
    }
}
