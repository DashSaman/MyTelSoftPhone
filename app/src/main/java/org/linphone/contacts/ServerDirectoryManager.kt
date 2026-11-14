/*
 * Copyright (c) 2026 Belledonne Communications SARL.
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.contacts

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.linphone.core.CoreContext
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.MyTelDirectoryParser

/**
 * Server phonebook (owner requirement 2026-11-08, leader corrections
 * 2026-11-08): fetches a JSON directory over an explicitly configured HTTPS
 * URL, injects it as an ApplicationCache friends list.
 *
 * Leader corrections implemented here:
 * - Cache is keyed by account identity AND URL hash: changing the URL never
 *   shows the previous source's snapshot.
 * - Stale responses rejected by generation + account + URL.
 * - Structured parser result: valid-empty clears managed entries; invalid
 *   keeps the previous snapshot.
 * - Empty-URL / no-account / non-HTTPS branches clear the directory and
 *   notify observers.
 * - Streaming 512 KiB byte cap while reading (works without Content-Length);
 *   streams closed and connection released on every failure path.
 * - Directory friends are read-only (ApplicationCache list, not editable by
 *   the user through the contacts UI).
 */
class ServerDirectoryManager(private val coreContext: CoreContext) {

    companion object {
        private const val TAG = "[Server Directory]"
        const val FRIEND_LIST_NAME = "MyTel server directory"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 10_000

        fun directoryUrlKey(identity: String) = "${identity}_directory_url"

        fun directoryCacheKey(identity: String, url: String) =
            "${identity}_directory_cache_${Integer.toHexString(url.hashCode())}"
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var refreshJob: Job? = null

    private val coreListener = object : org.linphone.core.CoreListenerStub() {
        override fun onDefaultAccountChanged(
            core: org.linphone.core.Core,
            account: org.linphone.core.Account?
        ) {
            Log.i("$TAG Default account changed, refreshing server directory")
            refresh()
        }
    }

    fun init() {
        coreContext.core.addListener(coreListener)
    }

    fun onCoreStarted() {
        refresh()
    }

    private var refreshGeneration = 0L

    fun refresh() {
        val core = coreContext.core
        val account = LinphoneUtils.getDefaultAccount()
        val generation = ++refreshGeneration

        if (account == null) {
            clearDirectory(core)
            notifyChanged()
            return
        }
        val identity = account.params?.identityAddress?.asStringUriOnly()
        if (identity == null) {
            clearDirectory(core)
            notifyChanged()
            return
        }
        val domain = account.params?.domain.orEmpty()

        // Show the last valid snapshot for THIS account+URL first; if this
        // account has no cache, clear any previous account's entries.
        val url = core.config.getString("app", directoryUrlKey(identity), "").orEmpty().trim()
        val cached = if (url.isNotEmpty()) {
            core.config.getString("app", directoryCacheKey(identity, url), "").orEmpty()
        } else {
            ""
        }
        if (cached.isNotEmpty()) {
            when (val parsedCache = MyTelDirectoryParser.parse(cached)) {
                is MyTelDirectoryParser.Result.Success ->
                    fillList(core, identity, domain, parsedCache.entries)
                // An empty cached snapshot means the directory was empty last
                // time; the list is already cleared below if needed.
                else -> clearDirectory(core)
            }
        } else {
            clearDirectory(core)
            notifyChanged()
        }

        if (url.isEmpty()) {
            Log.i("$TAG No directory URL configured for account [$identity], directory cleared")
            return
        }
        if (!url.startsWith("https://")) {
            Log.e("$TAG Directory URL must be HTTPS, directory cleared")
            clearDirectory(core)
            notifyChanged()
            return
        }

        refreshJob?.cancel()
        refreshJob = ioScope.launch {
            val fetched = fetch(url)
            coreContext.postOnCoreThread {
                // Discard stale responses: generation, account and URL must match
                if (generation != refreshGeneration) {
                    Log.i("$TAG Directory response discarded (stale generation)")
                    return@postOnCoreThread
                }
                val current = LinphoneUtils.getDefaultAccount()
                val currentIdentity = current?.params?.identityAddress?.asStringUriOnly().orEmpty()
                val currentUrl = core.config.getString(
                    "app", directoryUrlKey(currentIdentity), ""
                ).orEmpty().trim()
                if (currentIdentity != identity || currentUrl != url) {
                    Log.i("$TAG Account/URL changed during fetch, discarding directory response")
                    return@postOnCoreThread
                }
                when (val result = fetched) {
                    null -> Log.e("$TAG Directory fetch failed, keeping previous snapshot")
                    is FetchResult.Invalid -> {
                        Log.e("$TAG Directory body invalid or oversized, keeping previous snapshot")
                    }
                    is FetchResult.Body -> {
                        when (val parsed = MyTelDirectoryParser.parse(result.text)) {
                            is MyTelDirectoryParser.Result.Invalid ->
                                Log.e("$TAG Directory body invalid, keeping previous snapshot")
                            is MyTelDirectoryParser.Result.Empty -> {
                                // Valid empty directory: clear managed entries
                                core.config.setString("app", directoryCacheKey(identity, url), result.text)
                                clearDirectory(core)
                                notifyChanged()
                                Log.i("$TAG Server directory emptied by server response")
                            }
                            is MyTelDirectoryParser.Result.Success -> {
                                core.config.setString("app", directoryCacheKey(identity, url), result.text)
                                fillList(core, identity, domain, parsed.entries)
                                notifyChanged()
                                Log.i(
                                    "$TAG Server directory refreshed with " +
                                        "[${parsed.entries.size}] entries for [$identity]"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun notifyChanged() {
        coreContext.contactsManager.notifyContactsListChanged()
    }

    private fun clearDirectory(core: org.linphone.core.Core) {
        val list = core.getFriendListByName(FRIEND_LIST_NAME) ?: return
        for (old in list.friends.toList()) {
            list.removeFriend(old)
        }
        Log.i("$TAG Server directory cleared")
    }

    /**
     * Returns directory friends whose name OR extension matches the filter
     * (case-insensitive substring match). Used by the contact/chat picker
     * because MagicSearch does not index ApplicationCache friends in this
     * SDK version. Runs on the core thread.
     */
    fun searchDirectoryFriends(filter: String): List<Friend> {
        val core = coreContext.core
        val list = core.getFriendListByName(FRIEND_LIST_NAME) ?: return emptyList()
        if (filter.isEmpty()) return list.friends.toList()
        val lower = filter.lowercase()
        return list.friends.filter { friend ->
            val name = friend.name.orEmpty().lowercase()
            val numbersAndAddresses = friend.phoneNumbersWithLabel.map { labeledNumber ->
                labeledNumber.phoneNumber
            } + friend.addresses.map { address ->
                address.asStringUriOnly()
            }
            name.contains(lower) ||
                numbersAndAddresses.any { it.contains(filter) } ||
                friend.address?.username.orEmpty().contains(filter)
        }
    }

    /**
     * The account domain used for directory addresses (for building SIP URIs
     * from directory entries).
     */
    fun accountDomain(): String {
        return LinphoneUtils.getDefaultAccount()?.params?.domain.orEmpty().ifEmpty {
            "localhost"
        }
    }

    private fun fillList(
        core: org.linphone.core.Core,
        identity: String,
        domain: String,
        entries: List<MyTelDirectoryParser.Entry>
    ) {
        val list: FriendList = core.getFriendListByName(FRIEND_LIST_NAME)
            ?: core.createFriendList().also {
                it.isDatabaseStorageEnabled = true
                it.displayName = FRIEND_LIST_NAME
                it.type = FriendList.Type.ApplicationCache
                core.addFriendList(it)
            }

        // Clear previous directory friends, then add fresh ones
        for (old in list.friends.toList()) {
            list.removeFriend(old)
        }

        var added = 0
        for (entry in entries) {
            val address = core.createAddress("sip:${entry.number}@$domain") ?: continue
            val friend = core.createFriend()
            friend.edit()
            friend.name = entry.name
            friend.addAddress(address)
            friend.done()
            if (list.addFriend(friend) == FriendList.Status.OK) {
                added++
            } else {
                Log.e("$TAG Failed to add directory friend [${entry.name}] to list")
            }
        }
        Log.i("$TAG Directory list filled with [$added/${entries.size}] entries for account [$identity]")
    }

    /** Fetch outcome: null = transport/HTTP failure; Body = text (already capped). */
    private sealed class FetchResult {
        data class Body(val text: String) : FetchResult()

        object Invalid : FetchResult()
    }

    private fun fetch(url: String): FetchResult? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(
                "User-Agent",
                "MytellPhone/${org.linphone.BuildConfig.VERSION_NAME}"
            )
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                Log.e("$TAG Directory fetch HTTP code [$code]")
                connection.disconnect()
                return null
            }

            // Streaming byte cap (leader correction #1): works without
            // Content-Length; oversized responses are rejected mid-read.
            val maxBytes = MyTelDirectoryParser.DEFAULT_MAX_BODY_BYTES
            val output = java.io.ByteArrayOutputStream()
            var total = 0
            val buffer = ByteArray(8 * 1024)
            connection.inputStream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        Log.e("$TAG Directory body exceeds [$maxBytes] bytes while streaming")
                        connection.disconnect()
                        return FetchResult.Invalid
                    }
                    output.write(buffer, 0, read)
                }
            }
            connection.disconnect()
            FetchResult.Body(output.toString("UTF-8"))
        } catch (e: Exception) {
            Log.e("$TAG Directory fetch error (${e.javaClass.simpleName})")
            connection?.disconnect()
            null
        }
    }
}
