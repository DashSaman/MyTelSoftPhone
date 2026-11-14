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
package org.linphone.ui.assistant.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.AuthInfo
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.SipLoginAttempt

open class AccountLoginViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Account Login ViewModel]"
        private const val REGISTRATION_TIMEOUT_MS = 20_000L
    }

    val showBackButton = MutableLiveData<Boolean>()

    val hideCreateAccount = MutableLiveData<Boolean>()

    val hideScanQrCode = MutableLiveData<Boolean>()

    val hideThirdPartyAccount = MutableLiveData<Boolean>()

    val sipIdentity = MutableLiveData<String>()

    val password = MutableLiveData<String>()

    val serverAddress = MutableLiveData<String>()

    val serverPort = MutableLiveData<String>()

    // 0 = TLS, 1 = TCP, 2 = UDP (indexes of the transport RadioGroup)
    val transportType = MutableLiveData<Int>()

    val internationalPrefix = MutableLiveData<String>()

    val internationalPrefixIsoCountryCode = MutableLiveData<String>()

    val showPassword = MutableLiveData<Boolean>()

    val loginEnabled = MediatorLiveData<Boolean>()

    val registrationInProgress = MutableLiveData<Boolean>()

    val accountLoggedInEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val accountLoginErrorEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData()
    }

    val skipLandingToThirdPartySipAccountEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    var conditionsAndPrivacyPolicyAccepted = false

    init {
        // MyTel: pre-fill the connection details so the standard user only
        // types extension + password, while still being able to override them.
        serverAddress.value = corePreferences.defaultDomain
        serverPort.value = "57306"
        transportType.value = 0
    }

    private val attempts = SipLoginAttempt()
    private var pendingAttempt: Long? = null
    private var newlyCreatedAuthInfo: AuthInfo? = null
    private var newlyCreatedAccount: Account? = null

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            val attempt = pendingAttempt ?: return
            if (account == newlyCreatedAccount && attempts.isCurrent(attempt)) {
                Log.i("$TAG Newly created account registration state is [$state] ($message)")

                if (state == RegistrationState.Ok) {
                    if (!attempts.finish(attempt)) return
                    registrationInProgress.postValue(false)
                    core.defaultAccount = account
                    releasePendingAccount(core, attempt, removeAccount = false)
                    accountLoggedInEvent.postValue(Event(core.accountList.size == 1))
                } else if (state == RegistrationState.Failed) {
                    val error = when (account.error) {
                        Reason.Forbidden -> {
                            AppUtils.getString(R.string.assistant_account_login_forbidden_error)
                        }
                        else -> {
                            AppUtils.getFormattedString(
                                R.string.assistant_account_login_error,
                                account.error.toString()
                            )
                        }
                    }
                    failLogin(core, attempt, error)
                }
            }
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            // Prevent user from leaving assistant if no account was configured yet
            showBackButton.postValue(core.accountList.isNotEmpty())
            hideCreateAccount.postValue(corePreferences.hideAssistantCreateAccount)
            hideScanQrCode.postValue(corePreferences.hideAssistantScanQrCode)
            hideThirdPartyAccount.postValue(corePreferences.hideAssistantThirdPartySipAccount)
            conditionsAndPrivacyPolicyAccepted = corePreferences.conditionsAndPrivacyPolicyAccepted

            if (corePreferences.assistantDirectlyGoToThirdPartySipAccountLogin) {
                skipLandingToThirdPartySipAccountEvent.postValue(Event(true))
            }
        }

        showPassword.value = false
        registrationInProgress.value = false

        loginEnabled.addSource(sipIdentity) {
            loginEnabled.value = isLoginButtonEnabled()
        }
        loginEnabled.addSource(password) {
            loginEnabled.value = isLoginButtonEnabled()
        }
    }

    @UiThread
    fun setTransport(type: Int) {
        transportType.value = type
    }

    @UiThread
    fun login() {
        if (!coreContext.isReady()) return
        val attempt = attempts.begin() ?: return
        registrationInProgress.value = true
        val userInput = sipIdentity.value.orEmpty().trim()
        val passwordValue = password.value.orEmpty()
        val serverValue = serverAddress.value.orEmpty().trim().ifEmpty { corePreferences.defaultDomain }
        val portValue = serverPort.value.orEmpty().trim()
        val selectedTransport = when (transportType.value) {
            1 -> TransportType.Tcp
            2 -> TransportType.Udp
            else -> TransportType.Tls
        }
        val prefix = internationalPrefix.value.orEmpty().trim().removePrefix("+")
        val isoCountryCode = internationalPrefixIsoCountryCode.value.orEmpty()
        coreContext.postOnCoreThread { core ->
            if (!attempts.isCurrent(attempt)) return@postOnCoreThread
            try {
                core.loadConfigFromXml(corePreferences.linphoneDefaultValuesPath)
                val serverUri = if (serverValue.startsWith("sip:", true) || serverValue.startsWith("sips:", true)) serverValue else "sip:$serverValue"
                val serverAddr = Factory.instance().createAddress(serverUri)
                val port = portValue.toIntOrNull()
                if (serverAddr == null || serverAddr.domain.isNullOrBlank() ||
                    (portValue.isNotEmpty() && (port == null || port !in 1..65535))
                ) {
                    failLogin(core, attempt, AppUtils.getString(R.string.assistant_login_cant_parse_address_toast))
                    return@postOnCoreThread
                }
                if (port != null) serverAddr.port = port
                serverAddr.transport = selectedTransport
                val inputWithoutScheme = userInput.replaceFirst(Regex("(?i)^sips?:"), "")
                val identity = if (inputWithoutScheme.contains("@")) "sip:$inputWithoutScheme" else "sip:$inputWithoutScheme@${serverAddr.domain}"
                val identityAddress = Factory.instance().createAddress(identity)
                val user = identityAddress?.username
                if (identityAddress == null || user.isNullOrBlank()) {
                    failLogin(core, attempt, AppUtils.getString(R.string.assistant_login_cant_parse_address_toast))
                    return@postOnCoreThread
                }
                if (core.accountList.any { it.params.identityAddress?.weakEqual(identityAddress) == true }) {
                    failLogin(core, attempt, AppUtils.getString(R.string.assistant_account_login_already_connected_error))
                    return@postOnCoreThread
                }
                val accountParams = core.createAccountParams()
                accountParams.identityAddress = identityAddress
                accountParams.serverAddress = serverAddr
                if (prefix.isNotEmpty()) {
                    accountParams.internationalPrefix = prefix
                    accountParams.internationalPrefixIsoCountryCode = isoCountryCode
                }
                // Keep credentials and settings belonging to existing accounts.
                // Failed/cancelled attempts remove only the objects created here.
                newlyCreatedAuthInfo = Factory.instance().createAuthInfo(
                    user, null, passwordValue, null, null, identityAddress.domain
                )
                newlyCreatedAccount = core.createAccount(accountParams)
                pendingAttempt = attempt
                core.addListener(coreListener)
                newlyCreatedAuthInfo?.let { core.addAuthInfo(it) }
                newlyCreatedAccount?.let { core.addAccount(it) }
                coreContext.postOnCoreThreadDelayed({ currentCore ->
                    failLogin(currentCore, attempt, AppUtils.getString(R.string.mytel_login_timeout))
                }, REGISTRATION_TIMEOUT_MS)
            } catch (error: Exception) {
                Log.e("$TAG Login setup failed (${error.javaClass.simpleName})")
                failLogin(core, attempt, AppUtils.getString(R.string.mytel_login_setup_failed))
            }
        }
    }

    @WorkerThread
    private fun failLogin(core: Core, attempt: Long, message: String) {
        if (!attempts.finish(attempt)) return
        releasePendingAccount(core, attempt, removeAccount = true)
        registrationInProgress.postValue(false)
        accountLoginErrorEvent.postValue(Event(message))
    }

    @WorkerThread
    private fun releasePendingAccount(core: Core, attempt: Long, removeAccount: Boolean) {
        if (pendingAttempt != attempt) return
        core.removeListener(coreListener)
        if (removeAccount) {
            newlyCreatedAccount?.let { core.removeAccount(it) }
            newlyCreatedAuthInfo?.let { core.removeAuthInfo(it) }
        }
        newlyCreatedAccount = null
        newlyCreatedAuthInfo = null
        pendingAttempt = null
    }

    @UiThread
    fun cancelLogin() {
        val attempt = attempts.cancel() ?: return
        registrationInProgress.value = false
        coreContext.postOnCoreThread { core ->
            releasePendingAccount(core, attempt, removeAccount = true)
        }
    }

    override fun onCleared() {
        val attempt = attempts.cancel()
        if (attempt != null && coreContext.isReady()) {
            coreContext.postOnCoreThread { core ->
                releasePendingAccount(core, attempt, removeAccount = true)
            }
        }
        super.onCleared()
    }

    @UiThread
    fun toggleShowPassword() {
        showPassword.value = showPassword.value == false
    }

    @UiThread
    private fun isLoginButtonEnabled(): Boolean {
        return sipIdentity.value.orEmpty().trim().isNotEmpty() && password.value.orEmpty().isNotEmpty()
    }
}
