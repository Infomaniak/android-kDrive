/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.ui.menu.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.cache.CachedStorageFile
import com.infomaniak.drive.data.cache.ManageStorageRepository
import com.infomaniak.drive.data.cache.ManageStorageUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
internal class ManageStorageViewModel @Inject constructor(private val repository: ManageStorageRepository) : ViewModel() {

    data class State(val usage: ManageStorageUsage? = null, val busy: Boolean = true, val cleanupFailed: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val operations = StorageOperationQueue(viewModelScope)

    fun refresh() = update()

    fun clearFiles(files: List<CachedStorageFile>) = update { repository.clearFiles(files) }

    fun clearImages() = update { repository.clearImageCache() }

    private fun update(cleanup: (suspend () -> Unit)? = null) {
        _state.value = _state.value.copy(busy = true)
        operations.submit(skipIfBusy = cleanup == null) { hasPendingAfter ->
            _state.value = _state.value.copy(busy = true, cleanupFailed = false)
            var cleanupFailed = false
            try {
                cleanup?.invoke()
            } catch (exception: IOException) {
                Log.w(TAG, "Local storage cleanup did not complete", exception)
                cleanupFailed = true
            }

            val usage = try {
                repository.measure()
            } catch (exception: IOException) {
                Log.w(TAG, "Could not measure local storage", exception)
                null
            }
            _state.value = State(usage, busy = hasPendingAfter(), cleanupFailed)
        }
    }

    private companion object {
        const val TAG = "ManageStorageViewModel"
    }
}
