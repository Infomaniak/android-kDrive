/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.drive.data.api

import androidx.concurrent.futures.CallbackToFutureAdapter
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.drive.data.models.file.FileExternalImport
import com.infomaniak.drive.data.services.CopyToDriveOutcome
import com.infomaniak.drive.data.services.completeCopyToDrive
import com.infomaniak.drive.data.services.failCopyToDriveTracking
import com.infomaniak.drive.data.services.reconcileCopyToDrive
import com.infomaniak.drive.utils.SerializationUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopyToDriveReconciliationTest {
    private fun response(json: String) =
        SerializationUtils.decodeFromString<ApiResponse<List<FileExternalImport>>>(json)

    @Test
    fun `unknown outcome cleans up ongoing notification without sending a final notification`() {
        val actions = mutableListOf<String>()
        completeCopyToDrive(
            CopyToDriveOutcome.UNKNOWN,
            releaseResources = { actions.add("cleanup") },
            notifyResult = { actions.add("notify:$it") },
        )
        assertEquals(listOf("cleanup"), actions)
    }

    @Test
    fun `confirmed outcomes clean up before notifying the actual result`() {
        listOf(CopyToDriveOutcome.SUCCESS to true, CopyToDriveOutcome.FAILURE to false).forEach { (outcome, success) ->
            val actions = mutableListOf<String>()
            completeCopyToDrive(
                outcome,
                releaseResources = { actions.add("cleanup") },
                notifyResult = { actions.add("notify:$it") },
            )
            assertEquals(listOf("cleanup", "notify:$success"), actions)
        }
    }

    @Test
    fun `successful import without MQTT events is confirmed by server status`() = runBlocking {
        assertEquals(
            CopyToDriveOutcome.SUCCESS,
            reconcileCopyToDrive(45) { response("""{"result":"success","data":[{"id":45,"status":"done"}]}""") },
        )
    }

    @Test
    fun `only failed and canceled server states confirm failure`() = runBlocking {
        listOf("failed", "canceled").forEach { status ->
            assertEquals(
                CopyToDriveOutcome.FAILURE,
                reconcileCopyToDrive(45) {
                    response("""{"result":"success","data":[{"id":45,"status":"$status"}]}""")
                },
            )
        }
    }

    @Test
    fun `nonterminal or unknown server status is not a failed transfer`() = runBlocking {
        listOf("waiting", "queued", "in_progress", "canceling", "new_status", "").forEach { status ->
            assertEquals(
                CopyToDriveOutcome.UNKNOWN,
                reconcileCopyToDrive(45) {
                    response("""{"result":"success","data":[{"id":45,"status":"$status"}]}""")
                },
            )
        }
    }

    @Test
    fun `missing import empty data absent data and error cannot confirm a result`() = runBlocking {
        listOf(
            """{"result":"success","data":[{"id":44,"status":"done"}]}""",
            """{"result":"success","data":[]}""",
            """{"result":"success"}""",
            """{"result":"error","error":{"code":"access_denied"}}""",
        ).forEach { json ->
            assertEquals(CopyToDriveOutcome.UNKNOWN, reconcileCopyToDrive(45) { response(json) })
        }
    }

    @Test
    fun `matching import can be on a later page and other imports do not determine the result`() = runBlocking {
        val requestedPages = mutableListOf<Int>()
        val outcome = reconcileCopyToDrive(45) { page ->
            requestedPages.add(page)
            when (page) {
                1 -> response("""{"result":"success","page":1,"pages":2,"data":[{"id":44,"status":"failed"}]}""")
                else -> response("""{"result":"success","page":2,"pages":2,"data":[{"id":45,"status":"done"}]}""")
            }
        }
        assertEquals(CopyToDriveOutcome.SUCCESS, outcome)
        assertEquals(listOf(1, 2), requestedPages)
    }

    @Test
    fun `status lookup timeout leaves outcome unknown instead of reporting failure`() = runBlocking {
        assertEquals(
            CopyToDriveOutcome.UNKNOWN,
            reconcileCopyToDrive(45, timeoutMillis = 20) { awaitCancellation() },
        )
    }

    @Test
    fun `status lookup exception leaves outcome unknown instead of leaving tracking unresolved`() = runBlocking {
        var reportedException: Exception? = null
        assertEquals(
            CopyToDriveOutcome.UNKNOWN,
            reconcileCopyToDrive(
                importId = 45,
                onException = { reportedException = it },
                loadPage = { _: Int -> throw IOException("synthetic transport failure") },
            ),
        )
        assertEquals(IOException::class.java, reportedException?.javaClass)
    }

    @Test
    fun `successful response without data is reported but empty lists and API errors are not`() = runBlocking {
        listOf(
            """{"result":"success"}""" to 1,
            """{"result":"success","data":null}""" to 1,
            """{"result":"success","data":[]}""" to 0,
            """{"result":"error","error":{"code":"access_denied"}}""" to 0,
        ).forEach { (json, expectedReports) ->
            var reports = 0
            assertEquals(
                CopyToDriveOutcome.UNKNOWN,
                reconcileCopyToDrive(45, onMissingData = { reports++ }) { response(json) },
            )
            assertEquals(expectedReports, reports)
        }
    }

    @Test
    fun `external cancellation completes the future exceptionally and cleans up exactly once`() = runBlocking {
        listOf(CoroutineStart.DEFAULT, CoroutineStart.LAZY).forEach { start ->
            val isFinished = AtomicBoolean(false)
            val cancellation = CancellationException("external cancellation")
            var cleanups = 0
            val job = launch(start = start) { reconcileCopyToDrive(45) { awaitCancellation() } }
            val future = CallbackToFutureAdapter.getFuture<Unit> { completer ->
                job.invokeOnCompletion { cause ->
                    if (cause != null) {
                        failCopyToDriveTracking(
                            isFinished, false, cause,
                            releaseResources = { cleanups++ },
                            setException = { completer.setException(it) },
                        )
                    }
                }
                "copy tracking"
            }
            yield()
            job.cancel(cancellation)
            job.join()
            val failure = assertThrows(ExecutionException::class.java) { future.get(1, TimeUnit.SECONDS) }
            assertSame(cancellation, failure.cause)
            failCopyToDriveTracking(isFinished, false, cancellation, { cleanups++ }, { error("completed twice") })
            assertEquals(1, cleanups)
        }
    }

    @Test
    fun `worker owned cancellation suppresses exceptional completion of active reconciliation`() = runBlocking {
        val isFinished = AtomicBoolean(false)
        var stopped = false
        var completions = 0
        val job = launch { reconcileCopyToDrive(45) { awaitCancellation() } }
        job.invokeOnCompletion { cause ->
            if (cause != null) {
                failCopyToDriveTracking(
                    isFinished, stopped, cause,
                    releaseResources = { error("worker already cleaned up") },
                    setException = { completions++ },
                )
            }
        }
        yield()
        stopped = true
        isFinished.set(true)
        job.cancelAndJoin()
        assertEquals(0, completions)
    }

    @Test
    fun `worker stop and already completed tracking suppress cancellation completion`() {
        listOf(false to true, true to false, true to true).forEach { (finished, stopped) ->
            var actions = 0
            failCopyToDriveTracking(
                AtomicBoolean(finished), stopped, CancellationException(),
                releaseResources = { actions++ },
                setException = { actions++ },
            )
            assertEquals(0, actions)
        }
    }

    @Test
    fun `future cancellation cancels reconciliation without exceptional or late completion`() = runBlocking {
        val isFinished = AtomicBoolean(false)
        var exceptionalCompletions = 0
        val job = launch { reconcileCopyToDrive(45) { awaitCancellation() } }
        val future = CallbackToFutureAdapter.getFuture<Unit> { completer ->
            completer.addCancellationListener(
                {
                    isFinished.set(true)
                    job.cancel()
                },
                { command -> command.run() },
            )
            job.invokeOnCompletion { cause ->
                if (cause != null) {
                    failCopyToDriveTracking(
                        isFinished, false, cause,
                        releaseResources = {},
                        setException = { exceptionalCompletions++; completer.setException(it) },
                    )
                }
            }
            "copy tracking"
        }
        yield()
        assertTrue(future.cancel(true))
        job.join()
        assertTrue(future.isCancelled)
        assertEquals(0, exceptionalCompletions)
    }

    @Test
    fun `stopping tracking cancels reconciliation without emitting a terminal result`() = runBlocking {
        var outcome: CopyToDriveOutcome? = null
        val job = launch {
            outcome = reconcileCopyToDrive(45) { awaitCancellation() }
        }
        yield()
        job.cancelAndJoin()
        assertEquals(null, outcome)
    }
}
