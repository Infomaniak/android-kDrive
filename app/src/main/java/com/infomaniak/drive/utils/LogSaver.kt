/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.drive.utils

import android.content.Context
import android.util.Log
import com.infomaniak.core.cancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream

class LogSaver(private val appContext: Context) {

    init {
        require(appContext == appContext.applicationContext) { "The context must to be an applicationContext" }
    }

    suspend fun saveLogsToFile(): Boolean = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val logFilePath = IOFile(appContext.filesDir, "kDrive_logs.txt").apply {
                if (exists()) delete()
                createNewFile()
            }

            ensureActive()

            // Exec logcat command
            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "logcat -d --pid=$(pidof -s ${appContext.packageName})")
            ) ?: return@runCatching false

            // Save logs
            process.inputStream.use { inputStream ->
                inputStream.saveTo(logFilePath)
            }

            Log.i("LogSaver", "Logs saved to ${logFilePath.absolutePath}")
            true
        }.cancellable().getOrElse { exception ->
            Log.e("LogSaver", "Error saving logs", exception)
            false
        }
    }

    private suspend fun InputStream.saveTo(logFilePath: IOFile) {
        reader().buffered().use { reader ->
            logFilePath.writer().use { writer ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    currentCoroutineContext().ensureActive()
                    writer.write("$line\n")
                }
            }
        }
    }
}
