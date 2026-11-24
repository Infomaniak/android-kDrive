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
    internal data class Entry(val mutex: Mutex = Mutex(), var references: Int = 0)

    private val locks = mutableMapOf<String, Entry>()

    internal fun acquire(path: String): Entry = synchronized(locks) {
        locks.getOrPut(path) { Entry() }.also { it.references++ }
    }

    internal fun release(path: String, entry: Entry) = synchronized(locks) {
        if (--entry.references == 0) locks.remove(path)
    }

    suspend inline fun <T> withLock(file: File, action: () -> T): T {
        val path = file.canonicalPath
        val entry = acquire(path)
        try {
            return entry.mutex.withLock { action() }
        } finally {
            release(path, entry)
        }
    }

    inline fun tryWithLock(file: File, action: () -> Boolean): Boolean {
        val path = file.canonicalPath
        val entry = acquire(path)
        return try {
            if (!entry.mutex.tryLock()) return false
            try {
                action()
            } finally {
                entry.mutex.unlock()
            }
        } finally {
            release(path, entry)
        }
    }
}
