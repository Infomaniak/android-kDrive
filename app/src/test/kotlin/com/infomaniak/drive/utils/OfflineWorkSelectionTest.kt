/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.utils

import androidx.work.WorkInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class OfflineWorkSelectionTest {
    private fun work(state: WorkInfo.State, tag: String): WorkInfo = mockk {
        every { this@mockk.state } returns state
        every { tags } returns setOf(tag)
    }

    @Test
    fun `selects the running work for the requested file despite older and unrelated work`() {
        val running = work(WorkInfo.State.RUNNING, "file")
        val works = listOf(
            work(WorkInfo.State.SUCCEEDED, "file"),
            work(WorkInfo.State.RUNNING, "other"),
            work(WorkInfo.State.ENQUEUED, "file"),
            running,
        )

        assertSame(running, works.activeOfflineWorkForTag("file"))
    }

    @Test
    fun `selects queued work and ignores finished work`() {
        val queued = work(WorkInfo.State.BLOCKED, "file")
        assertSame(queued, listOf(work(WorkInfo.State.FAILED, "file"), queued).activeOfflineWorkForTag("file"))
        assertNull(listOf(work(WorkInfo.State.CANCELLED, "file")).activeOfflineWorkForTag("file"))
    }
}
