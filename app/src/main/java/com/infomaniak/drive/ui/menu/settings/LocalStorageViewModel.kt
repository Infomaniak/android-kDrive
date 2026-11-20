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
import com.infomaniak.drive.data.cache.LocalStorageRepository
import com.infomaniak.drive.data.cache.LocalStorageUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
internal class LocalStorageViewModel @Inject constructor(private val repository: LocalStorageRepository) : ViewModel() {

    data class State(val usage: LocalStorageUsage? = null, val busy: Boolean = true, val cleanupFailed: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private var operation: Job? = null

    fun refresh() = update()

    fun clearFiles(files: List<CachedStorageFile>) = update { repository.clearFiles(files) }

    fun clearImages() = update { repository.clearImageCache() }

    private fun update(cleanup: (suspend () -> Unit)? = null) {
        if (operation?.isActive == true) return

        operation = viewModelScope.launch {
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
            _state.value = State(usage, busy = false, cleanupFailed)
        }
    }

    private companion object {
        const val TAG = "LocalStorageViewModel"
    }
}
