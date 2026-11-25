/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.ui.fileList.preview

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class PreviewListStoreTest {
    @Test
    fun `large lists retain their order and composite identities`() {
        val keys = List(150_000) { (it % 1000) to (it / 1000) }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { PreviewListStore.writeKeys(it, keys) }

        assertEquals(keys, PreviewListStore.readKeys(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))))
    }

    @Test
    fun `truncated lists fail instead of returning partial results`() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.writeInt(1) }

        assertThrows(IllegalArgumentException::class.java) {
            PreviewListStore.readKeys(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
        }
    }
}
