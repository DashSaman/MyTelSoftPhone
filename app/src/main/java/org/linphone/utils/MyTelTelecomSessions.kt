package org.linphone.utils

/** A SIP call can retry with a different Call-ID, but owns only one Android call. */
class MyTelTelecomSessions<K> {
    class Session<K> internal constructor(val call: K)

    private data class Entry<K>(val session: Session<K>, var ended: Boolean = false)
    private val entries = HashMap<K, Entry<K>>()

    @Synchronized
    fun begin(call: K): Session<K>? {
        if (entries.containsKey(call)) return null
        return Session(call).also { entries[call] = Entry(it) }
    }

    @Synchronized
    fun isLive(session: Session<K>): Boolean {
        val entry = entries[session.call] ?: return false
        return entry.session === session && !entry.ended
    }

    @Synchronized
    fun end(call: K) {
        entries[call]?.ended = true
    }

    @Synchronized
    fun endAll() {
        entries.values.forEach { it.ended = true }
    }

    @Synchronized
    fun release(session: Session<K>): Boolean {
        if (entries[session.call]?.session !== session) return false
        entries.remove(session.call)
        return true
    }

    enum class Action { None, Answer, SetActive, Disconnect }

    companion object {
        fun actionFor(incoming: Boolean, connected: Boolean, ended: Boolean): Action = when {
            ended -> Action.Disconnect
            !connected -> Action.None
            incoming -> Action.Answer
            else -> Action.SetActive
        }
    }
}
