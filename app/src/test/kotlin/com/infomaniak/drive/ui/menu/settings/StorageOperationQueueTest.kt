/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.ui.menu.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageOperationQueueTest {
    @Test
    fun `cleanup is queued after refresh while redundant refresh is skipped`() = runBlocking {
        val queue = StorageOperationQueue(this)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        queue.submit(skipIfBusy = true) { hasPendingAfter ->
            started.complete(Unit)
            release.await()
            assertTrue(hasPendingAfter())
            calls += "refresh"
        }
        started.await()
        assertFalse(queue.submit(skipIfBusy = true) { calls += "duplicate" })
        assertTrue(queue.submit(skipIfBusy = false) { hasPendingAfter ->
            assertFalse(hasPendingAfter())
            calls += "cleanup"
            completed.complete(Unit)
        })
        yield()
        assertTrue(calls.isEmpty())
        release.complete(Unit)
        completed.await()
        assertEquals(listOf("refresh", "cleanup"), calls)
    }
}
