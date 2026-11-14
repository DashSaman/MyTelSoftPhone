package org.linphone.utils

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SipLoginAttemptTest {
    @Test
    fun repeatedTapCannotCreateAnotherPendingAccount() {
        val attempts = SipLoginAttempt()
        assertNotNull(attempts.begin())
        assertNull(attempts.begin())
    }

    @Test
    fun cancellationAllowsRetryAndRejectsTheOldRegistrationCallback() {
        val attempts = SipLoginAttempt()
        val first = attempts.begin()!!
        assertEquals(first, attempts.cancel())
        val second = attempts.begin()!!
        assertNotEquals(first, second)
        assertFalse(attempts.finish(first))
        assertTrue(attempts.isCurrent(second))
        assertTrue(attempts.finish(second))
    }

    @Test
    fun timeoutFromPreviousAttemptCannotCancelTheRetry() {
        val attempts = SipLoginAttempt()
        val first = attempts.begin()!!
        assertTrue(attempts.finish(first))
        val retry = attempts.begin()!!
        assertFalse(attempts.finish(first))
        assertTrue(attempts.isCurrent(retry))
    }

    @Test
    fun completionIsAcceptedOnce() {
        val attempts = SipLoginAttempt()
        val attempt = attempts.begin()!!
        assertTrue(attempts.finish(attempt))
        assertFalse(attempts.finish(attempt))
        assertFalse(attempts.isCurrent(attempt))
        assertNull(attempts.cancel())
    }

    @Test
    fun competingUiAndCoreThreadsShareOneAttempt() {
        val attempts = SipLoginAttempt()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = (1..12).map {
                executor.submit<Long?> {
                    start.await()
                    attempts.begin()
                }
            }
            start.countDown()
            assertEquals(1, results.count { it.get() != null })
        } finally {
            executor.shutdownNow()
        }
    }
}
