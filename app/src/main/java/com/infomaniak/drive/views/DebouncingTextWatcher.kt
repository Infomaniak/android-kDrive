/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.drive.views

import android.text.Editable
import android.text.TextWatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DebouncingTextWatcher(
    lifecycle: Lifecycle,
    private val onDebouncingQueryTextChange: (String?) -> Unit,
) : TextWatcher {

    private val coroutineScope = lifecycle.coroutineScope
    private var searchJob: Job? = null

    override fun afterTextChanged(s: Editable?) = Unit

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        search(s.toString())
    }

    private fun search(query: String?): Boolean {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            query?.let {
                delay(DEBOUNCE_DELAY)
                onDebouncingQueryTextChange(query)
            }
        }
        return false
    }

    companion object {
        const val DEBOUNCE_DELAY: Long = 300
    }
}
