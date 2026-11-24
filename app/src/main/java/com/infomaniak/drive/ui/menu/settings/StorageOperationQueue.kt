/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.ui.menu.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class StorageOperationQueue(private val scope: CoroutineScope) {
    private var last: Job? = null
    private var pending = 0

    fun submit(skipIfBusy: Boolean, action: suspend (hasPendingAfter: () -> Boolean) -> Unit): Boolean {
        if (skipIfBusy && pending > 0) return false
        val previous = last
        pending++
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                previous?.join()
                action { pending > 1 }
            } finally {
                pending--
            }
        }
        last = job
        job.start()
        return true
    }
}
