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

import com.infomaniak.core.auth.networking.AuthHttpClientProvider
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.core.network.networking.HttpUtils
import com.infomaniak.core.network.networking.ManualAuthorizationRequired
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.buildShareLinkSettingsJsonElement
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ManualAuthorizationRequired::class)
class MigrationJourneyRegressionTest {

    private lateinit var request: Request
    private var responseBody = ""
    private var responseCode = 200

    @BeforeEach
    fun configureHttp() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            request = chain.request()
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message("Test response")
                .body(responseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        mockkObject(AuthHttpClientProvider)
        every { AuthHttpClientProvider.authOkHttpClient } returns client
        mockkObject(HttpUtils)
        every { HttpUtils.getHeaders(any()) } returns Headers.Builder().build()
    }

    @AfterEach
    fun clearMocks() = unmockkAll()

    private fun requestJson() = Json.parseToJsonElement(
        Buffer().also { request.body!!.writeTo(it) }.readUtf8()
    ).jsonObject

    @Test
    fun `home activity keeps the user required by the home filter and cursor`() {
        responseBody = """{"result":"success","response_at":1792675175,"cursor":"next-page","has_more":true,
            "data":[{"id":42,"action":"file_create","created_at":1792675175,"file_id":12,
            "user":{"id":7,"display_name":"Alice","email":"alice@example.test"},
            "file":{"id":12,"drive_id":9,"name":"report.pdf","type":"file"}}]}"""

        val response = ApiRepository.getLastActivities(9, null)
        assertTrue(response.isSuccess())
        assertEquals("next-page", response.cursor)
        assertTrue(response.hasMoreAndCursorExists)
        val activity = response.data!!.single()
        assertEquals(7, activity.user?.id)
        assertEquals("Alice", activity.user?.displayName)
        assertEquals("12_9", activity.file?.uid)
        assertEquals(1_792_675_175_000L, activity.createdAt.time)
        assertTrue(request.url.encodedPath.endsWith("/files/activities"))
        assertTrue(request.url.queryParameter("with")!!.split(',').contains("user"))
    }

    @Test
    fun `create public link sends the API settings rather than the unannotated object`() {
        responseCode = 201
        responseBody = """{"result":"success","data":{"url":"https://example.test/share/abc",
            "file_id":12,"right":"public","capabilities":{"can_download":true,"can_edit":false}}}"""
        val settings = spyk(
            ShareLink().ShareLinkSettings(
                right = ShareLink.ShareLinkFilePermission.PUBLIC,
                canDownload = true,
                canEdit = false,
            )
        )
        // Avoid the Android account database; the body builder itself is exercised, not stubbed JSON.
        every { settings.toJsonElement() } returns buildShareLinkSettingsJsonElement(
            canDownload = true,
            canEdit = false,
            canSeeStats = null,
            password = null,
            right = ShareLink.ShareLinkFilePermission.PUBLIC,
            validUntil = null,
            isKSuiteFreeTier = false,
        )

        val response = ApiRepository.createShareLink(File(id = 12, driveId = 9), settings)
        val body = requestJson()
        assertEquals(JsonPrimitive("public"), body["right"])
        assertEquals(JsonPrimitive(true), body["can_download"])
        assertEquals(JsonPrimitive(false), body["can_edit"])
        assertFalse("canDownload" in body)
        assertFalse("canEdit" in body)
        assertTrue(response.isSuccess())
        assertEquals(12, response.data?.fileId)
        assertEquals("POST", request.method)
        assertTrue(request.url.encodedPath.endsWith("/12/link"))
    }

    @Test
    fun `copy to another drive preserves body types and asynchronous import response`() {
        responseBody = """{"result":"asynchronous","data":[{"id":45,"application":"kdrive","account_name":"Source",
            "status":"waiting","path":"/Documents/report.pdf","directory_id":99,"has_shared_files":false,
            "created_at":1792675175,"updated_at":1792675175,"count_success_files":0,"count_failed_files":0}]}"""
        val response = ApiRepository.copyFileToAnotherDrive(9, "/Documents/report.pdf", 10, 99)
        val body = requestJson()
        assertEquals(JsonPrimitive("9"), body["application_drive_id"])
        assertEquals(JsonPrimitive(99), body["directory_id"])
        assertEquals(JsonPrimitive("/Documents/report.pdf"), body["source_path"])
        assertEquals(ApiResponseStatus.ASYNCHRONOUS, response.result)
        assertEquals(45, response.data!!.single().id)
        assertEquals(99, response.data!!.single().directoryId)
        assertEquals("false", response.data!!.single().hasSharedFiles)
        assertEquals("POST", request.method)
        assertTrue(request.url.encodedPath.endsWith("/10/imports/kdrive"))
    }

    @Test
    fun `copy failure remains an error with server details`() {
        responseCode = 403
        responseBody = """{"result":"error","error":{"code":"access_denied","description":"No destination access"}}"""
        val response = ApiRepository.copyFileToAnotherDrive(9, "/Documents/report.pdf", 10, 99)
        assertFalse(response.isSuccess())
        assertNotNull(response.error)
        assertEquals("access_denied", response.error?.code)
    }

    @Test
    fun `copy import response accepts nullable directory and string shared flag from spec`() {
        responseBody = """{"result":"success","data":[{"id":45,"application":"kdrive","account_name":"Source",
            "status":"waiting","path":"/Documents/report.pdf","directory_id":null,"has_shared_files":"false",
            "created_at":1792675175,"updated_at":1792675175,"count_success_files":0,"count_failed_files":0}]}"""
        val response = ApiRepository.copyFileToAnotherDrive(9, "/Documents/report.pdf", 10, 99)
        assertTrue(response.isSuccess())
        val externalImport = response.data!!.single()
        assertEquals(45, externalImport.id)
        assertEquals(0, externalImport.directoryId)
        assertEquals("false", externalImport.hasSharedFiles)
    }

    @Test
    fun `source file path used for copying survives file details decoding`() {
        responseBody = """{"result":"success","data":{"id":12,"drive_id":9,
            "name":"report.pdf","path":"/Documents/report.pdf","type":"file"}}"""
        val response = ApiRepository.getFileDetails(
            File(id = 12, driveId = 9),
            AuthHttpClientProvider.authOkHttpClient,
        )
        assertTrue(response.isSuccess())
        assertEquals("12_9", response.data?.uid)
        assertEquals("/Documents/report.pdf", response.data?.path)
    }
}
