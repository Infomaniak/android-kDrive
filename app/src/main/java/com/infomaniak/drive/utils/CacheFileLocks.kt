/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

internal object CacheFileLocks {
    private val locks = Array(64) { Mutex() }

    private fun lock(file: File): Mutex = locks[(file.canonicalFile.path.hashCode() and Int.MAX_VALUE) % locks.size]

    suspend fun <T> withLock(file: File, action: suspend () -> T): T = lock(file).withLock { action() }

    fun tryWithLock(file: File, action: () -> Boolean): Boolean {
        val mutex = lock(file)
        if (!mutex.tryLock()) return false
        return try {
            action()
        } finally {
            mutex.unlock()
        }
    }
}
