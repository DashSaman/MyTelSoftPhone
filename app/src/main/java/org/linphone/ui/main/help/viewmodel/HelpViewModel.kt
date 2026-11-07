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
package org.linphone.ui.main.help.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.launch
import org.linphone.BuildConfig
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.VersionUpdateCheckResult
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class HelpViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Help ViewModel]"

        private const val NUMBER_OF_CLICK_TO_ENABLE_DEVELOPER_MODE = 6

        // Cap shared log text so every share target (chat, mail, ...) accepts it
        private const val MAX_SHARED_LOG_SIZE = 300000

        private val sensitiveLogPatterns = listOf(
            // Authorization / Proxy-Authorization headers, passwords, tokens, api keys
            Regex("(?i)(authorization|proxy-authorization|password|passwd|secret|api[-_]?key|token)[=:]\\s*[^\\s,;)\"']+"),
            // SIP URIs carrying a password as userinfo: sip:user:password@domain
            Regex("(?i)sip:([^:@\\s]+):([^@\\s]+)@")
        )
    }

    val version = MutableLiveData<String>()

    val appVersion = MutableLiveData<String>()

    val sdkVersion = MutableLiveData<String>()

    val firebaseProjectId = MutableLiveData<String>()

    val checkUpdateAvailable = MutableLiveData<Boolean>()

    val uploadLogsAvailable = MutableLiveData<Boolean>()

    val logsUploadInProgress = MutableLiveData<Boolean>()

    val printLogsInLogcat = MutableLiveData<Boolean>()

    val developerSettingsEnabled = MutableLiveData<Boolean>()

    val canConfigFileBeViewed = MutableLiveData<Boolean>()

    val newVersionAvailableEvent: MutableLiveData<Event<Pair<String, String>>> by lazy {
        MutableLiveData()
    }

    val versionUpToDateEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val errorEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val debugLogsCleanedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val uploadDebugLogsFinishedEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData()
    }

    val shareLogsAsTextEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData()
    }

    val uploadDebugLogsErrorEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData()
    }

    val showConfigFileEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData()
    }

    private var versionClickCount: Int = 0

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onVersionUpdateCheckResultReceived(
            core: Core,
            result: VersionUpdateCheckResult,
            version: String?,
            url: String?
        ) {
            when (result) {
                VersionUpdateCheckResult.NewVersionAvailable -> {
                    Log.i("$TAG Update available, version [$version], url [$url]")
                    if (!version.isNullOrEmpty() && !url.isNullOrEmpty()) {
                        newVersionAvailableEvent.postValue(Event(Pair(version, url)))
                    }
                }
                VersionUpdateCheckResult.UpToDate -> {
                    Log.i("$TAG This version is up-to-date")
                    versionUpToDateEvent.postValue(Event(true))
                }
                else -> {
                    Log.e("$TAG Can't check for update, an error happened [$result]")
                    errorEvent.postValue(Event(true))
                }
            }
        }

        @WorkerThread
        override fun onLogCollectionUploadStateChanged(
            core: Core,
            state: Core.LogCollectionUploadState,
            info: String
        ) {
            Log.i("$TAG Logs upload state changed [$state]")
            if (state == Core.LogCollectionUploadState.Delivered) {
                logsUploadInProgress.postValue(false)
                uploadDebugLogsFinishedEvent.postValue(Event(info))
            } else if (state == Core.LogCollectionUploadState.NotDelivered) {
                logsUploadInProgress.postValue(false)
                uploadDebugLogsErrorEvent.postValue(Event(true))
            }
        }
    }

    init {
        val currentVersion = BuildConfig.VERSION_NAME
        version.value = currentVersion

        val versionCode = BuildConfig.VERSION_CODE
        val appGitDescribe = AppUtils.getString(R.string.linphone_app_version)
        val appBranch = AppUtils.getString(R.string.linphone_app_branch)
        appVersion.value = "$versionCode - $appGitDescribe ($appBranch)"

        sdkVersion.value = coreContext.sdkVersion
        logsUploadInProgress.value = false

        try {
            firebaseProjectId.value = FirebaseApp.getInstance().options.projectId
        } catch (e: Exception) {
            Log.e("$TAG Failed to get FirebaseApp instance: $e")
            firebaseProjectId.value = "unknown"
        }

        versionClickCount = if (corePreferences.showDeveloperSettings) {
            Log.i("$TAG Developer settings are already enabled")
            NUMBER_OF_CLICK_TO_ENABLE_DEVELOPER_MODE
        } else {
            0
        }

        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            printLogsInLogcat.postValue(corePreferences.printLogsInLogcat)
            developerSettingsEnabled.postValue(corePreferences.showDeveloperSettings)
            checkUpdateAvailable.postValue(corePreferences.checkForUpdateServerUrl.isNotEmpty())
            // Always allow sharing logs locally as text, no upload server required
            uploadLogsAvailable.postValue(true)
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
    }

    @UiThread
    fun versionClicked() {
        versionClickCount += 1
        Log.i("$TAG Version was clicked [$versionClickCount] times")
        when (versionClickCount) {
            NUMBER_OF_CLICK_TO_ENABLE_DEVELOPER_MODE - 2 -> {
                showGreenToast(R.string.settings_developer_two_more_clicks_required_toast, R.drawable.gear)
            }
            NUMBER_OF_CLICK_TO_ENABLE_DEVELOPER_MODE - 1 -> {
                showGreenToast(R.string.settings_developer_one_more_click_required_toast, R.drawable.gear)
            }
            NUMBER_OF_CLICK_TO_ENABLE_DEVELOPER_MODE -> {
                showGreenToast(R.string.settings_developer_enabled_toast, R.drawable.gear)
                coreContext.postOnCoreThread {
                    Log.w("$TAG Enabling developer settings")
                    corePreferences.showDeveloperSettings = true
                    developerSettingsEnabled.postValue(true)
                }
            }
            NUMBER_OF_CLICK_TO_ENABLE_DEVELOPER_MODE + 1 -> {
                showGreenToast(R.string.settings_developer_already_enabled_toast, R.drawable.gear)
            }
            else -> { }
        }
    }

    @UiThread
    fun cleanLogs() {
        coreContext.postOnCoreThread { core ->
            core.resetLogCollection()
            Log.i("$TAG Debug logs have been cleaned")
            debugLogsCleanedEvent.postValue(Event(true))
        }
    }

    @UiThread
    fun shareLogs() {
        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Collecting debug logs to share them as text")
            val context = coreContext.context
            val builder = StringBuilder()

            builder.appendLine("MytellPhone debug report")
            builder.appendLine("=========================")
            builder.appendLine(
                "Time: " + java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.US
                ).format(java.util.Date())
            )
            builder.appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            builder.appendLine("Core version: ${core.version}")
            builder.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            builder.appendLine(
                "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            )
            val account = core.defaultAccount
            builder.appendLine(
                "Account: ${account?.params?.identityAddress?.username.orEmpty()} @ ${account?.params?.domain.orEmpty()}"
            )
            builder.appendLine("Registration state: ${account?.state}")
            builder.appendLine("=========================")

            val logFiles = context.filesDir.listFiles { file ->
                (file.name.startsWith("linphone") && file.name.contains(".log")) ||
                    file.name.endsWith(".log.1")
            }?.sortedBy { it.lastModified() } ?: emptyList()
            for (file in logFiles) {
                builder.appendLine("--- ${file.name} ---")
                builder.appendLine(redactSensitiveInformation(file))
            }

            var text = builder.toString()
            if (text.length > MAX_SHARED_LOG_SIZE) {
                text = "[... log truncated, oldest lines removed ...]" +
                    text.takeLast(MAX_SHARED_LOG_SIZE)
            }
            shareLogsAsTextEvent.postValue(Event(text))
        }
    }

    private fun redactSensitiveInformation(file: java.io.File): String {
        return try {
            file.readText().lines().joinToString("\n") { line ->
                var sanitized = line
                for (pattern in sensitiveLogPatterns) {
                    sanitized = pattern.replace(sanitized) { matchResult ->
                        if (matchResult.groupValues.size > 2) {
                            "sip:${matchResult.groupValues[1]}:***@"
                        } else {
                            "${matchResult.groupValues[1]}=REDACTED"
                        }
                    }
                }
                sanitized
            }
        } catch (e: Exception) {
            "Could not read log file [${file.name}]: $e"
        }
    }

    @UiThread
    fun checkForUpdate() {
        val currentVersion = version.value.orEmpty()
        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Checking for update using current version [$currentVersion]")
            core.checkForUpdate(currentVersion)
        }
    }

    @UiThread
    fun toggleLogcat() {
        val newValue = printLogsInLogcat.value == false
        coreContext.postOnCoreThread {
            corePreferences.printLogsInLogcat = newValue
            coreContext.updateLogcatEnabledSetting(newValue)
            Factory.instance().enableLogcatLogs(newValue)
            printLogsInLogcat.postValue(newValue)
        }
    }

    @UiThread
    fun showConfigFile() {
        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Dumping & displaying Core's config")
            val config = core.config.dump()
            val file = FileUtils.getFileStorageCacheDir(
                "linphonerc.txt",
                overrideExisting = true
            )
            viewModelScope.launch {
                if (FileUtils.dumpStringToFile(config, file)) {
                    Log.i("$TAG .linphonerc string saved as file in cache folder")
                    showConfigFileEvent.postValue(Event(file.absolutePath))
                } else {
                    Log.e("$TAG Failed to save .linphonerc string as file in cache folder")
                }
            }
        }
    }
}
