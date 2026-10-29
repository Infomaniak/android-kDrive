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

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.drive.data.models.file.FileExternalImport
import com.infomaniak.drive.data.services.CopyToDriveOutcome
import com.infomaniak.drive.data.services.completeCopyToDrive
import com.infomaniak.drive.data.services.reconcileCopyToDrive
import com.infomaniak.drive.utils.SerializationUtils
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
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
