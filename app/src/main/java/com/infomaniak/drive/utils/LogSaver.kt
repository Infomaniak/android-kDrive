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
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.invoke
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.TimeUnit

class LogSaver(private val appContext: Context) {

    private val logsDir
        get() = IOFile(
            appContext.cacheDir,
            appContext.getString(R.string.EXPOSED_LOGS_DIR)
        ).apply { if (!exists()) mkdirs() }

    init {
        require(appContext == appContext.applicationContext) { "The context must be the applicationContext" }
    }

    suspend fun saveLogsToFile(): Uri? = withContext(Dispatchers.IO) {
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

            ensureActive()

            try {
                // Save logs
                process.inputStream.use { inputStream ->
                    inputStream.saveTo(logFile)
                }

                getLogFileUri(process, logFile)
            } finally {
                process.destroy()
            }

        }.cancellable().getOrElse { exception ->
            Log.e("LogSaver", "Error saving logs", exception)
            null
        }
    }

    private fun getLogFileUri(process: Process, logFile: IOFile): Uri? = when {
        process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0 -> {
            SentryLog.i("LogSaver", "Logs saved to ${logFile.path}")
            FileProvider.getUriForFile(
                appContext,
                appContext.getString(R.string.FILE_AUTHORITY),
                logFile
            )
        }
        else -> {
            SentryLog.e("LogSaver", "Process finished error")
            null
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
