/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.ui.fileList.preview

import androidx.lifecycle.SavedStateHandle
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreviewSliderViewModelTest {

    @Test
    fun `restores the selected page and parent folder`() {
        val state = SavedStateHandle(mapOf("fileId" to 12, "parentFolderId" to 4))
        val first = PreviewSliderViewModel(state)

        assertEquals(12, first.currentFileId)
        first.currentFileId = 15

        val restored = PreviewSliderViewModel(state.copy())
        assertEquals(15, restored.currentFileId)
        assertEquals(4, restored.parentFolderId)
    }

    @Test
    fun `restores changed ordered file ids`() {
        val state = SavedStateHandle(mapOf("fileId" to 12, "fileIds" to intArrayOf(12, 15, 20)))
        val first = PreviewSliderViewModel(state)
        first.fileIds = first.fileIds!!.filterNot { it == 15 }.toIntArray() + 21

        val restored = PreviewSliderViewModel(state.copy())
        assertArrayEquals(intArrayOf(12, 20, 21), restored.fileIds)
    }

    @Test
    fun `restores additions and removals from a folder preview`() {
        val state = SavedStateHandle(mapOf("fileId" to 12, "parentFolderId" to 4))
        val first = PreviewSliderViewModel(state)
        first.addedFileIds += 21
        first.removedFileIds += 15

        val restored = PreviewSliderViewModel(state.copy())
        assertArrayEquals(intArrayOf(21), restored.addedFileIds)
        assertArrayEquals(intArrayOf(15), restored.removedFileIds)
    }

    private fun SavedStateHandle.copy() = SavedStateHandle(keys().associateWith { get<Any?>(it) })
}
