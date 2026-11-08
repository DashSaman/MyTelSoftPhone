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
package org.linphone.ui.main.model

import androidx.annotation.UiThread
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import org.linphone.utils.Event

/**
 * Pre-dial editor (owner requirement 2026-11-08): the normalized destination
 * is prefilled and editable; Call dials exactly the displayed number and
 * Cancel initiates nothing.
 */
class PreDialEditDialogModel
@UiThread
constructor(
    contactName: String,
    initialNumber: String
) {
    val title = "Call $contactName"

    val number = MutableLiveData<String>()

    val dismissEvent = MutableLiveData<Event<Boolean>>()

    val confirmEvent = MutableLiveData<Event<String>>()

    val emptyNumber = MediatorLiveData<Boolean>()

    init {
        emptyNumber.addSource(number) { value ->
            emptyNumber.value = value.isEmpty()
        }
        number.value = initialNumber
    }

    @UiThread
    fun confirm() {
        confirmEvent.value = Event(number.value.orEmpty())
    }

    @UiThread
    fun dismiss() {
        dismissEvent.value = Event(true)
    }
}
