/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025-2026 Infomaniak Network SA
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
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.sentry.SentryLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.invoke
import kotlinx.coroutines.withContext
import java.io.InputStream

class LogSaver(private val appContext: Context) {

    private val logsDir get() = IOFile(appContext.cacheDir, "logs").apply { if (!exists()) mkdirs() }

    init {
        require(appContext == appContext.applicationContext) { "The context must to be an applicationContext" }
    }

    suspend fun saveLogsToFile(): Boolean = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val logFile = IOFile(logsDir, "kDrive_logs.txt").apply {
                if (exists()) delete()
                createNewFile()
            }

            ensureActive()

            // Exec logcat command
            val process = ProcessBuilder("logcat", "-d", "--pid=${android.os.Process.myPid()}")
                .redirectErrorStream(true)
                .start()

            // Save logs
            process.inputStream.use { inputStream ->
                inputStream.saveTo(logFile)
            }

            val isSuccessfullySaved = when {
                process.waitFor() != 0 -> {
                    SentryLog.e("LogSaver", "Process finished error")
                    false
                }
                else -> {
                    Log.i("LogSaver", "Logs saved to ${logFile.path}")
                    true
                }
            }

            isSuccessfullySaved
        }.cancellable().getOrElse { exception ->
            Log.e("LogSaver", "Error saving logs", exception)
            false
        }
    }

    suspend fun deleteLogs() = Dispatchers.IO {
        if (logsDir.exists()) logsDir.deleteRecursively()
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
