/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.ui.fileList.preview

import androidx.lifecycle.SavedStateHandle
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
    fun `restores the selected drive after swiping to a file with the same id`() {
        val state = SavedStateHandle(mapOf("fileId" to 12, "driveId" to 1))
        val first = PreviewSliderViewModel(state)

        assertEquals(1, first.currentDriveId)
        first.currentFileId = 12
        first.currentDriveId = 2

        val restored = PreviewSliderViewModel(state.copy())
        assertEquals(12, restored.currentFileId)
        assertEquals(2, restored.currentDriveId)
    }

    @Test
    fun `removing a file preserves the same id from another drive and the list order`() {
        val state = SavedStateHandle(
            mapOf("fileId" to 12, "fileIds" to intArrayOf(12, 12, 20), "fileDriveIds" to intArrayOf(1, 2, 1))
        )
        val first = PreviewSliderViewModel(state)
        first.removeFile(fileId = 12, driveId = 1)

        val restored = PreviewSliderViewModel(state.copy())
        assertArrayEquals(intArrayOf(12, 20), restored.fileIds)
        assertArrayEquals(intArrayOf(2, 1), restored.fileDriveIds)
        assertEquals(listOf(12 to 2, 20 to 1), restored.fileKeys(12, 1))
    }

    @Test
    fun `restores older file lists with the initial drive`() {
        val state = SavedStateHandle(mapOf("fileId" to 12, "fileIds" to intArrayOf(12, 20), "driveId" to 3))

        assertEquals(listOf(12 to 3, 20 to 3), PreviewSliderViewModel(state).fileKeys(12, 3))
    }

    @Test
    fun `rejects misaligned file and drive lists`() {
        val state = SavedStateHandle(mapOf("fileIds" to intArrayOf(12, 20), "fileDriveIds" to intArrayOf(1)))
        assertThrows(IllegalArgumentException::class.java) { PreviewSliderViewModel(state).fileKeys(12, 1) }
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
