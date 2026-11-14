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
import org.junit.Test

/**
 * Tests for the Iranian phone-number normalization applied when dialing
 * from contacts (owner requirement 2026-11-08, leader-approved rules):
 * - Persian/Arabic digits converted to ASCII, visual separators removed
 * - Longest applicable country prefix first: 0098, +98, 098, 98
 * - Converts ONLY when the remaining national portion has exactly 10 digits;
 *   one redundant leading trunk zero is accepted; output has exactly one 0
 * - Short extensions, already-domestic numbers, other-country numbers,
 *   feature codes and SIP addresses are preserved unchanged
 */
class MyTelNumberNormalizerTest {

    private fun assertNormalized(input: String, expected: String) {
        assertEquals("input [$input]", expected, MyTelNumberNormalizer.normalize(input))
    }

    @Test
    fun plus98_prefix_becomes_trunk_zero() {
        assertNormalized("+989121234567", "09121234567")
    }

    @Test
    fun double_zero_98_prefix_becomes_trunk_zero() {
        assertNormalized("00989121234567", "09121234567")
    }

    @Test
    fun bare_98_prefix_becomes_trunk_zero() {
        assertNormalized("989121234567", "09121234567")
    }

    @Test
    fun zero98_prefix_becomes_trunk_zero() {
        assertNormalized("0989121234567", "09121234567")
    }

    @Test
    fun landline_with_area_code_is_converted() {
        assertNormalized("+982112345678", "02112345678")
    }

    @Test
    fun separators_and_spaces_are_removed() {
        assertNormalized("+98 912 345-6789", "09123456789")
        assertNormalized("+98 (912) 345.6789", "09123456789")
    }

    @Test
    fun persian_digits_are_converted() {
        assertNormalized("+۹۸۹۱۲۱۲۳۴۵۶۷", "09121234567")
        assertNormalized("۰۹۱۲۱۲۳۴۵۶۷", "09121234567")
    }

    @Test
    fun redundant_trunk_zero_after_prefix_is_normalized_to_one_zero() {
        assertNormalized("009809121234567", "09121234567")
        assertNormalized("+9809121234567", "09121234567")
    }

    @Test
    fun already_domestic_number_is_unchanged() {
        assertNormalized("09121234567", "09121234567")
        assertNormalized("02112345678", "02112345678")
    }

    @Test
    fun short_internal_extensions_are_unchanged() {
        assertNormalized("1", "1")
        assertNormalized("2", "2")
        assertNormalized("301", "301")
        assertNormalized("6002", "6002")
    }

    @Test
    fun other_country_numbers_are_unchanged() {
        assertNormalized("+442071234567", "+442071234567")
        assertNormalized("00442071234567", "00442071234567")
    }

    @Test
    fun wrong_length_remainder_is_unchanged() {
        assertNormalized("+98912", "+98912")
        assertNormalized("+9812345", "+9812345")
    }

    @Test
    fun feature_codes_are_unchanged() {
        assertNormalized("*67", "*67")
        assertNormalized("#123", "#123")
    }

    @Test
    fun sip_addresses_are_unchanged() {
        assertNormalized("sip:2@10.0.2.2", "sip:2@10.0.2.2")
        assertNormalized("2@10.0.2.2", "2@10.0.2.2")
    }

    @Test
    fun empty_input_is_unchanged() {
        assertNormalized("", "")
    }

    @Test
    fun non_numeric_country_prefix_is_not_rewritten() {
        assertNormalized("+98abcdefghij", "+98abcdefghij")
        assertNormalized("0098123456789x", "0098123456789x")
    }

    @Test
    fun persian_feature_codes_use_ascii_digits() {
        assertNormalized("*۲۱*۳۰۱#", "*21*301#")
        assertNormalized("#۱۲۳", "#123")
    }

    @Test
    fun pasted_rtl_phone_number_drops_invisible_direction_marks() {
        assertNormalized("\u200E+98\u200F 912\u202F123\u00A04567", "09121234567")
    }

    @Test
    fun telephone_uri_is_dialable_without_changing_sip_uris() {
        assertNormalized("tel:+989121234567", "09121234567")
        assertNormalized("sips:2@example.org", "sips:2@example.org")
    }
}
