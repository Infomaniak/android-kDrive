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
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.buildShareLinkSettingsJsonElement
import com.infomaniak.drive.data.services.CopyToDriveOutcome
import com.infomaniak.drive.data.services.reconcileCopyToDrive
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
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
import java.io.IOException

@OptIn(ManualAuthorizationRequired::class)
class MigrationJourneyRegressionTest {

    private lateinit var request: Request
    private var responseBody = ""
    private var responseCode = 200
    private var activeUserId = 101
    private var failTransport = false
    private val diagnosticMessages = mutableListOf<String>()

    @BeforeEach
    fun configureHttp() {
        // Fake the account/token boundary; no Android account database or real credentials are involved.
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().header("Authorization", "Bearer fixture-user-$activeUserId").build()
            )
        }.addInterceptor { chain ->
            if (failTransport) throw IOException("private-fixture transport detail")
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
        mockkObject(SentryLog)
        every { SentryLog.i(any(), capture(diagnosticMessages), any()) } just Runs
        every { SentryLog.w(any(), capture(diagnosticMessages), any()) } just Runs
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
        verify { SentryLog.i("CopyToDrive", "stage=request_started", null) }
        verify { SentryLog.i("CopyToDrive", "stage=response outcome=accepted http_status=200 result=ASYNCHRONOUS", null) }
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

    @Test
    fun `copy uses the active login not a destination owner's separate login`() {
        val sourceUserId = 101
        val destinationUserId = 202
        activeUserId = sourceUserId
        responseCode = 403
        responseBody = """{"result":"error","error":{"code":"access_denied","description":"Destination denied"}}"""

        val denied = ApiRepository.copyFileToAnotherDrive(9, "/private-fixture/source.txt", 10, 99)
        assertEquals("Bearer fixture-user-$sourceUserId", request.header("Authorization"))
        assertEquals(ApiResponseStatus.ERROR, denied.result)
        assertEquals("access_denied", denied.error?.code)
        verify { SentryLog.w("CopyToDrive", "stage=response outcome=http_error http_status=403 result=ERROR", null) }

        // Changing the active login changes the supplied HTTP client's token, not the import request fields.
        activeUserId = destinationUserId
        ApiRepository.copyFileToAnotherDrive(9, "/private-fixture/source.txt", 10, 99)
        assertEquals("Bearer fixture-user-$destinationUserId", request.header("Authorization"))
        assertEquals(JsonPrimitive("9"), requestJson()["application_drive_id"])
        assertFalse(diagnosticMessages.any { it.contains("fixture-user") || it.contains("source.txt") })
    }

    @Test
    fun `copy diagnostics distinguish decoder failure without forwarding private exception content`() {
        responseBody = """{"result":"success","data":{"private-fixture":"not an import array"}}"""
        val response = ApiRepository.copyFileToAnotherDrive(9, "/private-fixture/source.txt", 10, 99)
        assertEquals(ApiResponseStatus.ERROR, response.result)
        verify { SentryLog.w("CopyToDrive", "stage=response outcome=decoder_failure http_status=200 result=ERROR", null) }
        assertFalse(diagnosticMessages.any { it.contains("private-fixture") || it.contains("fixture-user") })
    }

    @Test
    fun `copy diagnostics identify API error on HTTP success`() {
        responseBody = """{"result":"error","error":{"code":"access_denied"}}"""
        ApiRepository.copyFileToAnotherDrive(9, "/private-fixture/source.txt", 10, 99)
        verify { SentryLog.w("CopyToDrive", "stage=response outcome=api_or_client_error http_status=200 result=ERROR", null) }
    }

    @Test
    fun `copy diagnostics identify transport failure without a received HTTP response`() {
        failTransport = true
        val response = ApiRepository.copyFileToAnotherDrive(9, "/private-fixture/source.txt", 10, 99)
        assertEquals(ApiResponseStatus.ERROR, response.result)
        verify { SentryLog.w("CopyToDrive", "stage=response outcome=transport_failure http_status=none result=ERROR", null) }
        assertFalse(diagnosticMessages.any { it.contains("private-fixture") || it.contains("fixture-user") })
    }

    @Test
    fun `drive init preserves every decoded drive and reports refresh count`() {
        responseBody = """{"result":"success","data":{"ips":{"uuid":"fixture","valid_until":1792675175},
            "drives":[{"id":1,"role":"admin"},{"id":2,"role":"user"},{"id":3,"role":"user"},
            {"id":4,"role":"external"},{"id":5,"role":"none"}]}}"""
        val response = ApiRepository.getAllDrivesData(AuthHttpClientProvider.authOkHttpClient)
        assertTrue(response.isSuccess())
        assertEquals(listOf(1, 2, 3, 4, 5), response.data!!.drives.map { it.id })
        verify {
            SentryLog.i(
                "CopyToDrive",
                "stage=drive_api_refresh result=SUCCESS data_present=true drive_count=5 failure=none_or_api",
                null,
            )
        }
    }

    @Test
    fun `drive init decoding failure rejects entire envelope instead of silently returning a partial list`() {
        responseBody = """{"result":"success","data":{"ips":{"uuid":"fixture","valid_until":1792675175},
            "drives":[{"id":1,"role":"admin"},{"id":{},"role":"user"}]}}"""
        val response = ApiRepository.getAllDrivesData(AuthHttpClientProvider.authOkHttpClient)
        assertEquals(ApiResponseStatus.ERROR, response.result)
        assertEquals(null, response.data)
        verify {
            SentryLog.i(
                "CopyToDrive",
                "stage=drive_api_refresh result=ERROR data_present=false drive_count=0 failure=decoder",
                null,
            )
        }
    }

    @Test
    fun `MQTT silence reconciles successful remote copy using the exact import and list endpoint`() = runBlocking {
        responseBody = """{"result":"success","page":1,"pages":1,"data":[
            {"id":44,"status":"failed"},{"id":45,"status":"done","directory_id":99}]}"""
        val outcome = reconcileCopyToDrive(45) { page ->
            ApiRepository.getExternalImports(10, page, AuthHttpClientProvider.authOkHttpClient)
        }
        assertEquals(CopyToDriveOutcome.SUCCESS, outcome)
        assertTrue(request.url.encodedPath.endsWith("/10/imports"))
        assertEquals("1", request.url.queryParameter("page"))
        assertEquals("100", request.url.queryParameter("per_page"))
        assertEquals("true", request.url.queryParameter("total"))
        assertEquals("GET", request.method)
    }

    @Test
    fun `reconciliation API error remains unknown not failed or successful transfer`() = runBlocking {
        responseCode = 403
        responseBody = """{"result":"error","error":{"code":"access_denied"}}"""
        val outcome = reconcileCopyToDrive(45) { page ->
            ApiRepository.getExternalImports(10, page, AuthHttpClientProvider.authOkHttpClient)
        }
        assertEquals(CopyToDriveOutcome.UNKNOWN, outcome)
    }

    @Test
    fun `reconciliation transport and decoder failures cannot determine transfer outcome`() = runBlocking {
        responseBody = """{"result":"success","data":{"unexpected":"shape"}}"""
        assertEquals(
            CopyToDriveOutcome.UNKNOWN,
            reconcileCopyToDrive(45) { page ->
                ApiRepository.getExternalImports(10, page, AuthHttpClientProvider.authOkHttpClient)
            },
        )
        failTransport = true
        assertEquals(
            CopyToDriveOutcome.UNKNOWN,
            reconcileCopyToDrive(45) { page ->
                ApiRepository.getExternalImports(10, page, AuthHttpClientProvider.authOkHttpClient)
            },
        )
    }
}
