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
package org.linphone.utils

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Strict parser for the server phonebook JSON consumed by the server
 * directory feature (owner requirement 2026-11-08, leader corrections
 * 2026-11-08):
 * - body must be a JSON array of objects with STRING "name" and "number"
 *   (numeric/boolean/array values are rejected — leading zeros preserved)
 * - response size, name and number lengths are validated
 * - structured result distinguishes valid-populated / valid-empty / invalid
 *   so callers can clear managed entries only on a VALID empty directory
 * - invalid entries are skipped; an invalid body yields Invalid
 *
 * Deliberately dependency-free (no Android/linphone logging) so it runs in
 * JVM unit tests; production wires [logSink] to the linphone logger.
 */
object MyTelDirectoryParser {

    const val MAX_NAME_LENGTH = 128
    const val MAX_NUMBER_LENGTH = 32
    const val DEFAULT_MAX_BODY_BYTES = 512 * 1024

    var logSink: ((String) -> Unit)? = null

    data class Entry(val name: String, val number: String)

    /** Structured outcome: leader correction #2-c. */
    sealed class Result {
        data class Success(val entries: List<Entry>) : Result()

        object Empty : Result()

        object Invalid : Result()
    }

    private fun logError(message: String) {
        logSink?.invoke(message)
    }

    private fun isString(value: Any?): Boolean = value is String

    fun parse(body: String, maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES): Result {
        if (body.toByteArray(Charsets.UTF_8).size > maxBodyBytes) {
            logError("Response body exceeds [$maxBodyBytes] bytes, discarding it")
            return Result.Invalid
        }
        val entries = mutableListOf<Entry>()
        try {
            val array = JSONArray(body)
            for (i in 0 until array.length()) {
                val item: JSONObject = array.optJSONObject(i) ?: continue
                val nameValue = item.opt("name") ?: continue
                val numberValue = item.opt("number") ?: continue
                // Schema enforcement: name/number must be JSON strings with
                // bounded lengths — a violation invalidates the whole body
                // (leader correction #2-d: optString coerces numbers/booleans)
                if (!isString(nameValue) || !isString(numberValue)) {
                    logError("Entry at [$i] violates the schema: name/number must be strings")
                    return Result.Invalid
                }
                val name = nameValue as String
                val number = numberValue as String
                if (name.isEmpty() || number.isEmpty()) continue
                if (name.length > MAX_NAME_LENGTH || number.length > MAX_NUMBER_LENGTH) {
                    logError("Entry at [$i] violates the schema: name or number too long")
                    return Result.Invalid
                }
                entries.add(Entry(name, number))
            }
        } catch (e: JSONException) {
            logError("Failed to parse directory body: ${e.message}")
            return Result.Invalid
        }
        return if (entries.isEmpty()) Result.Empty else Result.Success(entries)
    }
}
