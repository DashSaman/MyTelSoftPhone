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

/**
 * Normalizes Iranian phone numbers when dialing from contacts (owner
 * requirement 2026-11-08, rules leader-approved 2026-11-08):
 * - Persian/Arabic digits are converted to ASCII, ordinary visual
 *   separators (spaces, dashes, parentheses, dots, slashes) are removed.
 * - Country prefixes are recognized longest-first: `0098`, `+98`, `098`, `98`.
 * - The number is rewritten with exactly one leading trunk `0` ONLY when the
 *   remaining national portion has exactly 10 digits; one redundant leading
 *   trunk zero after the prefix is accepted (11 digits starting with `0`).
 * - Short internal extensions, already-domestic numbers, other-country
 *   numbers, feature codes and SIP addresses are preserved unchanged.
 */
object MyTelNumberNormalizer {

    private val prefixes = listOf("0098", "+98", "098", "98")

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return raw
        if (trimmed.startsWith("sip:", ignoreCase = true) ||
            trimmed.startsWith("sips:", ignoreCase = true) ||
            trimmed.contains("@")
        ) {
            return raw
        }

        val ascii = StringBuilder()
        val telephone = if (trimmed.startsWith("tel:", ignoreCase = true)) trimmed.substring(4) else trimmed
        for (ch in telephone) {
            ascii.append(
                when (ch) {
                    in '۰'..'۹' -> ('0' + (ch - '۰'))
                    in '٠'..'٩' -> ('0' + (ch - '٠'))
                    else -> ch
                }
            )
        }

        val cleaned = ascii.toString().filter {
            !it.isWhitespace() && it != '\u00A0' && it !in "-()./\u200E\u200F\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069"
        }
        if (cleaned.isEmpty()) return raw
        if (cleaned.startsWith("*") || cleaned.startsWith("#")) return cleaned

        val prefix = prefixes.firstOrNull { cleaned.startsWith(it) } ?: return cleaned
        val remainder = cleaned.substring(prefix.length)
        if (!remainder.all { it in '0'..'9' }) return cleaned

        return when {
            remainder.length == 10 -> "0$remainder"
            remainder.length == 11 && remainder.startsWith("0") -> remainder
            else -> cleaned
        }
    }
}
