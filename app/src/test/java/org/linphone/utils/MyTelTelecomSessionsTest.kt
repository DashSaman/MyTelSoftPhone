package org.linphone.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyTelTelecomSessionsTest {
    @Test
    fun repeatedOutgoingProgressReservesOnePlatformCall() {
        val sessions = MyTelTelecomSessions<Any>()
        val sipCall = Any()
        assertNotNull(sessions.begin(sipCall))
        assertNull(sessions.begin(sipCall))
    }

    @Test
    fun sipRetryDoesNotCreateSecondPlatformCallWhenCallIdChanges() {
        val sessions = MyTelTelecomSessions<Any>()
        val sipCall = Any()
        val first = sessions.begin(sipCall)!!
        // The SDK keeps this call object while retrying with a new SIP Call-ID.
        assertNull(sessions.begin(sipCall))
        assertTrue(sessions.isLive(first))
    }

    @Test
    fun errorBeforeAsynchronousPlatformAttachmentIsAlreadyTerminal() {
        val sessions = MyTelTelecomSessions<Any>()
        val sipCall = Any()
        val pending = sessions.begin(sipCall)!!
        sessions.end(sipCall)
        assertFalse(sessions.isLive(pending))
        assertNull(sessions.begin(sipCall))
    }

    @Test
    fun finishedCallCannotBlockNextDistinctCall() {
        val sessions = MyTelTelecomSessions<Any>()
        val firstCall = Any()
        val pending = sessions.begin(firstCall)!!
        sessions.end(firstCall)
        assertNotNull(sessions.begin(Any()))
        assertTrue(sessions.release(pending))
    }

    @Test
    fun lateOldCleanupCannotRemoveReplacementSession() {
        val sessions = MyTelTelecomSessions<String>()
        val first = sessions.begin("call")!!
        sessions.end("call")
        assertTrue(sessions.release(first))
        val replacement = sessions.begin("call")!!
        assertFalse(sessions.release(first))
        assertTrue(sessions.isLive(replacement))
    }

    @Test
    fun coreShutdownInvalidatesAllPendingSessions() {
        val sessions = MyTelTelecomSessions<Int>()
        val first = sessions.begin(1)!!
        val second = sessions.begin(2)!!
        sessions.endAll()
        assertFalse(sessions.isLive(first))
        assertFalse(sessions.isLive(second))
    }

    @Test
    fun outgoingCallAttachedAfterConnectionMustActivateNotAnswer() {
        assertEquals(MyTelTelecomSessions.Action.SetActive,
            MyTelTelecomSessions.actionFor(incoming = false, connected = true, ended = false))
    }

    @Test
    fun incomingConnectedCallMustAnswerPlatformCall() {
        assertEquals(MyTelTelecomSessions.Action.Answer,
            MyTelTelecomSessions.actionFor(incoming = true, connected = true, ended = false))
    }

    @Test
    fun terminalStateAlwaysDisconnectsEvenIfPreviouslyConnected() {
        assertEquals(MyTelTelecomSessions.Action.Disconnect,
            MyTelTelecomSessions.actionFor(incoming = false, connected = true, ended = true))
        assertEquals(MyTelTelecomSessions.Action.None,
            MyTelTelecomSessions.actionFor(incoming = false, connected = false, ended = false))
    }
}
