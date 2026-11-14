/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.telecom

import android.content.Context
import android.os.Build
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallException
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.compatibility.Compatibility
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.MyTelTelecomSessions

class TelecomManager
    @WorkerThread
    constructor(context: Context) {
    companion object {
        private const val TAG = "[Telecom Manager]"
        private val SAMSUNG_S23s =
            arrayListOf("dm3q", "dm1q", "dm2q", "r11s", "r11q", "SC-52D", "SCG19", "SC-51D", "SCG24", "SCG20")
    }

    private val callsManager = CallsManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessions = MyTelTelecomSessions<Call>()
    // Android permits only one addCall setup until its CallControlScope is ready.
    private val setupMutex = Mutex()
    // Accessed on the SDK core thread, keyed by the SIP call, not its mutable Call-ID.
    private val controls = HashMap<Call, TelecomCallControlCallback>()
    private val hasTelecomFeature = Compatibility.hasTelecomManagerFeature(context)

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallStateChanged(core: Core, call: Call, state: Call.State?, message: String) {
            when (state) {
                Call.State.IncomingReceived, Call.State.OutgoingProgress -> onCallCreated(call)
                Call.State.End, Call.State.Error, Call.State.Released -> {
                    sessions.end(call)
                    controls[call]?.onCallFinished()
                }
                else -> {}
            }
        }
    }

    init {
        Log.i("$TAG Feature is [${if (hasTelecomFeature) "available" else "not available"}]")
        if (hasTelecomFeature) {
            try {
                callsManager.registerAppWithTelecom(CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING)
                Log.i("$TAG App has been registered with Telecom")
            } catch (e: Exception) {
                Log.e("$TAG Can't init TelecomManager: $e")
            }
        }
    }

    @WorkerThread
    fun onCallCreated(call: Call) {
        val session = sessions.begin(call)
        if (session == null) {
            Log.i("$TAG SIP call already owns a pending or active Telecom session")
            return
        }

        // Snapshot every SDK property here; addCall and its callbacks run asynchronously.
        val address = call.callLog.remoteAddress
        val uri = address.asStringUriOnly().toUri()
        val direction = if (call.dir == Call.Dir.Outgoing) {
            CallAttributesCompat.DIRECTION_OUTGOING
        } else {
            CallAttributesCompat.DIRECTION_INCOMING
        }
        val conferenceInfo = LinphoneUtils.getConferenceInfoIfAny(call)
        val displayName = if (call.conference != null || conferenceInfo != null) {
            conferenceInfo?.subject ?: call.conference?.subject ?: LinphoneUtils.getDisplayName(address)
        } else {
            coreContext.contactsManager.findContactByAddress(address)?.name
                ?: LinphoneUtils.getDisplayName(address)
        }
        val type = if (!call.core.isVideoEnabled || Build.DEVICE in SAMSUNG_S23s) {
            CallAttributesCompat.CALL_TYPE_AUDIO_CALL
        } else {
            CallAttributesCompat.CALL_TYPE_VIDEO_CALL
        }
        val attributes = CallAttributesCompat(
            displayName, uri, direction, type,
            CallAttributesCompat.SUPPORTS_SET_INACTIVE or CallAttributesCompat.SUPPORTS_TRANSFER,
            isLogExcluded = true
        )

        scope.launch {
            var callbacks: TelecomCallControlCallback? = null
            try {
                setupMutex.lock(session)
                // An early SIP failure can finish the call while Android setup is queued.
                if (!sessions.isLive(session)) return@launch
                callsManager.addCall(
                    attributes,
                    { // onAnswer
                        coreContext.postOnCoreThread {
                            if (sessions.isLive(session) && LinphoneUtils.isCallIncoming(call.state)) {
                                coreContext.answerCall(call)
                            }
                        }
                    },
                    { // onDisconnect
                        coreContext.postOnCoreThread {
                            if (sessions.isLive(session)) coreContext.terminateCall(call)
                        }
                    },
                    { // onSetActive
                        coreContext.postOnCoreThread {
                            if (sessions.isLive(session) && call.state == Call.State.Paused) call.resume()
                        }
                    },
                    { // onSetInactive
                        coreContext.postOnCoreThread {
                            if (sessions.isLive(session)) call.pause()
                        }
                    }
                ) {
                    // Setup is complete; other calls may now register without waiting
                    // for this entire (possibly long-lived) call to disconnect.
                    setupMutex.unlock(session)
                    val callback = TelecomCallControlCallback(call, this)
                    callbacks = callback
                    coreContext.postOnCoreThread {
                        controls[call] = callback
                        callback.onCallControlCallbackSet(forceDisconnect = !sessions.isLive(session))
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (ce: CallException) {
                Log.e("$TAG Failed to add call to Telecom's CallsManager: $ce")
            } catch (e: Exception) {
                Log.e("$TAG Unable to register Telecom call: $e")
            } finally {
                if (setupMutex.holdsLock(session)) setupMutex.unlock(session)
                val callback = callbacks
                coreContext.postOnCoreThread {
                    callback?.dispose()
                    if (sessions.release(session)) controls.remove(call)
                }
            }
        }
    }

    @WorkerThread
    fun onCoreStarted(core: Core) {
        if (hasTelecomFeature) {
            core.addListener(coreListener)
        } else {
            Log.w("$TAG Telecom unavailable; enabling SDK audio focus requests")
            core.config.setBool("audio", "android_disable_audio_focus_requests", false)
        }
    }

    @WorkerThread
    fun onCoreStopped(core: Core) {
        if (hasTelecomFeature) {
            core.removeListener(coreListener)
            sessions.endAll()
            controls.values.toList().forEach { it.onCoreStopped() }
            controls.clear()
        }
    }
}
