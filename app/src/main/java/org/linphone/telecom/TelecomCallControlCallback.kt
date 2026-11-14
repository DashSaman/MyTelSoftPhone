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

import android.telecom.DisconnectCause
import androidx.annotation.WorkerThread
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.Reason
import org.linphone.core.tools.Log
import org.linphone.utils.AudioUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.MyTelTelecomSessions
import java.util.concurrent.atomic.AtomicBoolean

class TelecomCallControlCallback(
    private val call: Call,
    private val callControl: CallControlScope
) {
    companion object {
        private const val TAG = "[Telecom Call Control Callback]"
    }

    private val closed = AtomicBoolean(false)
    private val connectionSynchronized = AtomicBoolean(false)
    private var listenerAttached = false
    private var mutedByTelecomManager = false

    private val callListener = object : CallListenerStub() {
        @WorkerThread
        override fun onStateChanged(call: Call, state: Call.State?, message: String) {
            if (closed.get()) return
            when (state) {
                Call.State.Connected, Call.State.StreamsRunning -> synchronizeConnectedCall()
                Call.State.End, Call.State.Error, Call.State.Released -> onCallFinished()
                Call.State.Pausing -> callControl.launch {
                    if (!closed.get()) logResult(callControl.setInactive(), "set inactive")
                }
                Call.State.Resuming -> callControl.launch {
                    if (!closed.get()) logResult(callControl.setActive(), "set active")
                }
                else -> {}
            }
        }
    }

    @WorkerThread
    fun onCallControlCallbackSet(forceDisconnect: Boolean = false) {
        if (forceDisconnect) {
            disconnectPlatform(DisconnectCause.LOCAL)
            return
        }
        call.addListener(callListener)
        listenerAttached = true
        val state = call.state
        val ended = state == Call.State.End || state == Call.State.Error || state == Call.State.Released
        val action = MyTelTelecomSessions.actionFor(
            incoming = call.dir == Call.Dir.Incoming,
            connected = state == Call.State.Connected || state == Call.State.StreamsRunning,
            ended = ended
        )
        when (action) {
            MyTelTelecomSessions.Action.Disconnect -> onCallFinished()
            MyTelTelecomSessions.Action.Answer, MyTelTelecomSessions.Action.SetActive -> synchronizeConnectedCall()
            MyTelTelecomSessions.Action.None -> {}
        }
        if (closed.get()) return

        // These collectors belong to this platform call, not the manager's
        // lifetime. AndroidX cancels them when the call session disconnects.
        callControl.availableEndpoints.onEach {
            Log.i("$TAG Audio endpoints updated")
        }.launchIn(callControl)
        callControl.currentCallEndpoint.onEach {
            Log.i("$TAG Platform audio endpoint updated")
        }.launchIn(callControl)
        callControl.isMuted.onEach { muted ->
            coreContext.postOnCoreThread {
                if (closed.get()) return@postOnCoreThread
                val stateNow = call.state
                if (muted || mutedByTelecomManager ||
                    (!LinphoneUtils.isCallOutgoing(stateNow, false) && coreContext.isConnectedToAndroidAuto)
                ) {
                    mutedByTelecomManager = muted
                    call.microphoneMuted = muted
                    coreContext.refreshMicrophoneMuteStateEvent.postValue(Event(true))
                }
            }
        }.launchIn(callControl)
    }

    @WorkerThread
    private fun synchronizeConnectedCall() {
        if (closed.get() || !connectionSynchronized.compareAndSet(false, true)) return
        val isVideo = LinphoneUtils.isVideoEnabled(call)
        val type = if (isVideo) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL
        val action = MyTelTelecomSessions.actionFor(
            incoming = call.dir == Call.Dir.Incoming,
            connected = true,
            ended = false
        )
        callControl.launch {
            if (closed.get()) return@launch
            // answer() is valid for incoming calls only. Late attachment to an
            // already connected outgoing SIP call must transition DIALING->ACTIVE.
            val result = if (action == MyTelTelecomSessions.Action.Answer) {
                callControl.answer(type)
            } else {
                callControl.setActive()
            }
            if (result is CallControlResult.Error) connectionSynchronized.set(false)
            logResult(result, "synchronize connected call")
        }
        if (isVideo && corePreferences.routeAudioToSpeakerWhenVideoIsEnabled) {
            AudioUtils.routeAudioToSpeaker(call)
        }
    }

    @WorkerThread
    fun onCallFinished() {
        val reason = call.reason
        val cause = when (reason) {
            Reason.NotAnswered -> DisconnectCause.REMOTE
            Reason.Declined -> DisconnectCause.REJECTED
            Reason.Busy -> if (call.dir == Call.Dir.Incoming) DisconnectCause.MISSED else DisconnectCause.REJECTED
            else -> if (call.state == Call.State.Error) DisconnectCause.REJECTED else DisconnectCause.LOCAL
        }
        disconnectPlatform(cause)
    }

    @WorkerThread
    fun onCoreStopped() {
        disconnectPlatform(DisconnectCause.LOCAL)
    }

    @WorkerThread
    private fun disconnectPlatform(cause: Int) {
        if (!closed.compareAndSet(false, true)) return
        detachListener()
        callControl.launch {
            try {
                logResult(callControl.disconnect(DisconnectCause(cause)), "disconnect")
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e("$TAG Unable to disconnect platform call: $e")
            }
        }
    }

    @WorkerThread
    fun dispose() {
        closed.set(true)
        detachListener()
    }

    @WorkerThread
    private fun detachListener() {
        if (listenerAttached) {
            call.removeListener(callListener)
            listenerAttached = false
        }
    }

    private fun logResult(result: CallControlResult, operation: String) {
        if (result is CallControlResult.Error) Log.e("$TAG Failed to $operation: $result")
    }
}
