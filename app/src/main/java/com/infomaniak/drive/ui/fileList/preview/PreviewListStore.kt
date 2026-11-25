/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.ui.fileList.preview

import android.content.Context
import android.util.AtomicFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

class PreviewListStore(context: Context) {
    private val directory = File(context.filesDir, "preview-lists")

    fun create(keys: List<Pair<Int, Int>>): String {
        val token = UUID.randomUUID().toString()
        write(token, keys)
        return token
    }

    fun read(token: String): List<Pair<Int, Int>> {
        return DataInputStream(BufferedInputStream(file(token).openRead())).use { input ->
            readKeys(input)
        }
    }

    fun write(token: String, keys: List<Pair<Int, Int>>) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create preview list directory")
        }
        val atomicFile = file(token)
        val output = atomicFile.startWrite()
        try {
            DataOutputStream(BufferedOutputStream(output)).apply { writeKeys(this, keys); flush() }
            atomicFile.finishWrite(output)
        } catch (exception: Exception) {
            atomicFile.failWrite(output)
            throw exception
        }
    }

    fun delete(token: String) = file(token).delete()

    private fun file(token: String): AtomicFile {
        require(UUID.fromString(token).toString() == token) { "Invalid preview list token" }
        return AtomicFile(File(directory, token))
    }

    companion object {
        internal fun readKeys(input: DataInputStream): List<Pair<Int, Int>> {
            val count = input.readInt()
            require(count >= 0 && count <= input.available() / 8) { "Invalid preview list size" }
            return List(count) { input.readInt() to input.readInt() }
        }

        internal fun writeKeys(output: DataOutputStream, keys: List<Pair<Int, Int>>) {
            output.writeInt(keys.size)
            keys.forEach { (id, driveId) ->
                output.writeInt(id)
                output.writeInt(driveId)
            }
        }
    }
}
