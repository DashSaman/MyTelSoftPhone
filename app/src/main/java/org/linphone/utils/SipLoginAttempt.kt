package org.linphone.utils

/** Serializes login attempts across the UI and SDK threads without holding SDK objects. */
class SipLoginAttempt {
    private var generation = 0L
    private var active: Long? = null

    @Synchronized
    fun begin(): Long? {
        if (active != null) return null
        return (++generation).also { active = it }
    }

    @Synchronized
    fun isCurrent(attempt: Long): Boolean = active == attempt

    @Synchronized
    fun finish(attempt: Long): Boolean {
        if (active != attempt) return false
        active = null
        return true
    }

    @Synchronized
    fun cancel(): Long? = active.also { active = null }
}
