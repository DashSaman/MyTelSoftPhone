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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the server phonebook JSON parser (owner requirement
 * 2026-11-08, leader corrections 2026-11-08): structured result
 * (Success/Empty/Invalid), strict string typing (numeric/boolean fields
 * rejected), leading zeros preserved, size limits enforced.
 */
class MyTelDirectoryParserTest {

    private fun assertSuccess(body: String, expected: List<Pair<String, String>>) {
        val result = MyTelDirectoryParser.parse(body, maxBodyBytes = 64 * 1024)
        assertTrue("expected Success for [$body]", result is MyTelDirectoryParser.Result.Success)
        val entries = (result as MyTelDirectoryParser.Result.Success).entries
        assertEquals(expected.size, entries.size)
        expected.forEachIndexed { i, (name, number) ->
            assertEquals(name, entries[i].name)
            assertEquals(number, entries[i].number)
        }
    }

    private fun assertEmptyValid(body: String) {
        val result = MyTelDirectoryParser.parse(body, maxBodyBytes = 64 * 1024)
        assertTrue("expected Empty for [$body]", result is MyTelDirectoryParser.Result.Empty)
    }

    private fun assertInvalid(body: String) {
        val result = MyTelDirectoryParser.parse(body, maxBodyBytes = 64 * 1024)
        assertTrue("expected Invalid for [$body]", result is MyTelDirectoryParser.Result.Invalid)
    }

    @Test
    fun parses_valid_array_of_entries() {
        assertSuccess(
            """[{"name":"Reception","number":"101"},
            {"name":"Warehouse","number":"302"}]""",
            listOf("Reception" to "101", "Warehouse" to "302")
        )
    }

    @Test
    fun preserves_leading_zeros_in_numbers() {
        assertSuccess("""[{"name":"Front desk","number":"0100"}]""", listOf("Front desk" to "0100"))
    }

    @Test
    fun tolerates_extra_fields() {
        assertSuccess(
            """[{"name":"Ops","number":"6002","department":"IT","vip":true}]""",
            listOf("Ops" to "6002")
        )
    }

    @Test
    fun skips_invalid_entries_but_keeps_valid_ones() {
        assertSuccess(
            """[{"number":"101"},{"name":"No number"},{"name":"Ok","number":"302"},[]]""",
            listOf("Ok" to "302")
        )
    }

    @Test
    fun rejects_invalid_json() {
        assertInvalid("not json")
    }

    @Test
    fun rejects_non_array_root() {
        assertInvalid("""{"name":"Reception","number":"101"}""")
    }

    @Test
    fun rejects_oversized_body() {
        val big = " ".repeat(200 * 1024)
        assertInvalid(big)
    }

    @Test
    fun skips_overlong_name() {
        val longName = "A".repeat(200)
        assertInvalid("""[{"name":"$longName","number":"101"}]""")
    }

    @Test
    fun skips_overlong_number() {
        val longNumber = "9".repeat(64)
        assertInvalid("""[{"name":"Ops","number":"$longNumber"}]""")
    }

    @Test
    fun empty_array_is_valid_empty() {
        assertEmptyValid("[]")
    }

    @Test
    fun multiline_empty_array_is_valid_empty() {
        assertEmptyValid("[\n  ]")
        assertEmptyValid("[ ]")
    }

    @Test
    fun numeric_name_is_rejected() {
        assertInvalid("""[{"name":101,"number":"101"}]""")
    }

    @Test
    fun numeric_number_is_rejected() {
        assertInvalid("""[{"name":"Ops","number":101}]""")
    }

    @Test
    fun boolean_number_is_rejected() {
        assertInvalid("""[{"name":"Ops","number":true}]""")
    }

    @Test
    fun array_number_is_rejected() {
        assertInvalid("""[{"name":"Ops","number":["101"]}]""")
    }

    @Test
    fun oversize_body_just_over_limit_is_invalid() {
        val entries = StringBuilder("[")
        // build a body that is just over 64 KiB of valid-looking JSON
        var i = 0
        while (StringBuilder(entries.toString()).length < 64 * 1024 + 1) {
            if (i > 0) entries.append(",")
            entries.append("""{"name":"User$i","number":"1$i"}""")
            i++
        }
        entries.append("]")
        assertInvalid(entries.toString())
    }
}
