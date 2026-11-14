package org.linphone.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyTelRegistrationAlertsTest {
    private val onlineDefault = MyTelRegistrationAlerts.Account(isDefault = true, enabled = true, failed = false, cleared = false)
    private val failedOther = MyTelRegistrationAlerts.Account(isDefault = false, enabled = true, failed = true, cleared = false)

    @Test
    fun removingCancelledAttemptClearsItsFailureWithoutChangingTheOnlineDefault() {
        assertTrue(MyTelRegistrationAlerts.resolve(true, listOf(onlineDefault, failedOther)).otherFailed)
        assertEquals(MyTelRegistrationAlerts.Alerts(false, false, false), MyTelRegistrationAlerts.resolve(true, listOf(onlineDefault)))
    }

    @Test
    fun anotherConfiguredAccountFailureSurvivesAttemptRemoval() {
        val another = failedOther.copy()
        assertTrue(MyTelRegistrationAlerts.resolve(true, listOf(onlineDefault, another)).otherFailed)
    }

    @Test
    fun genuineDefaultFailureSurvivesRemovingAnotherAccount() {
        val failedDefault = onlineDefault.copy(failed = true)
        assertTrue(MyTelRegistrationAlerts.resolve(true, listOf(failedDefault)).defaultFailed)
    }

    @Test
    fun intentionallyDisabledAccountDoesNotProduceASecondaryConnectionError() {
        assertFalse(MyTelRegistrationAlerts.resolve(true, listOf(onlineDefault, failedOther.copy(enabled = false))).otherFailed)
    }

    @Test
    fun disabledDefaultRemainsVisibleAcrossNetworkChanges() {
        val disabled = onlineDefault.copy(enabled = false, cleared = true)
        assertTrue(MyTelRegistrationAlerts.resolve(false, listOf(disabled)).defaultDisabled)
        assertFalse(MyTelRegistrationAlerts.resolve(false, listOf(disabled, failedOther)).otherFailed)
    }

    @Test
    fun noConfiguredAccountsCannotKeepARegistrationAlert() {
        assertEquals(MyTelRegistrationAlerts.Alerts(false, false, false), MyTelRegistrationAlerts.resolve(true, emptyList()))
    }
}
