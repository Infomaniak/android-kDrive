/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.drive

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.infomaniak.drive.data.api.ApiRepository.deleteFavoriteFile
import com.infomaniak.drive.data.api.ApiRepository.deleteFileComment
import com.infomaniak.drive.data.api.ApiRepository.getAllDrivesData
import com.infomaniak.drive.data.api.ApiRepository.getFavoriteFiles
import com.infomaniak.drive.data.api.ApiRepository.getFileActivities
import com.infomaniak.drive.data.api.ApiRepository.getFileComments
import com.infomaniak.drive.data.api.ApiRepository.getUserProfile
import com.infomaniak.drive.data.api.ApiRepository.postFavoriteFile
import com.infomaniak.drive.data.api.ApiRepository.postFileComment
import com.infomaniak.drive.data.api.ApiRepository.putFileComment
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.ApiTestUtils
import com.infomaniak.drive.utils.ApiTestUtils.assertApiResponse
import com.infomaniak.drive.utils.ApiTestUtils.createFileForTest
import com.infomaniak.drive.utils.ApiTestUtils.deleteTestFile
import com.infomaniak.drive.utils.KDriveHttpClient
import io.realm.Realm
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Logging activity testing class
 */
@RunWith(AndroidJUnit4::class)
class ApiRepositoryTest : KDriveTest() {
    private lateinit var realm: Realm

    @Before
    @Throws(Exception::class)
    fun setUp() {
        realm = Realm.getInstance(getConfig())
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        if (!realm.isClosed) realm.close()
        Realm.deleteRealm(getConfig())
    }

    @Test
    fun getDriveData(): Unit = runBlocking {
        val okHttpClient = userDrive.userId.let { KDriveHttpClient.getHttpClient(it) }
        val drivesDataResponse = getAllDrivesData(okHttpClient)
        ApiTestUtils.assertApiResponse(drivesDataResponse)
    }

    @Test
    fun getTestUserProfile(): Unit = runBlocking {
        val okHttpClient = userDrive.userId.let { KDriveHttpClient.getHttpClient(it) }
        val userProfileResponse = getUserProfile(okHttpClient)
        ApiTestUtils.assertApiResponse(userProfileResponse)
        Assert.assertEquals("User ids should be the same", userDrive.userId, userProfileResponse.data?.id)
    }

    @Test
    fun manageFavoriteFileLifecycle(): Unit = runBlocking {
        val remoteFile = createFileForTest()
        // Make the file a favorite
        val postFavoriteFileResponse = postFavoriteFile(remoteFile)
        ApiTestUtils.assertApiResponse(postFavoriteFileResponse)
        // Get the favorite files
        var getFavoriteResponse = getFavoriteFiles(userDrive.driveId, File.SortType.NAME_AZ, 1)
        ApiTestUtils.assertApiResponse(getFavoriteResponse)
        Assert.assertTrue("Favorite file should exists", getFavoriteResponse.data!!.isNotEmpty())
        val favoriteFileCount = getFavoriteResponse.data!!.size
        // Delete the favorite file
        val deleteFavoriteFileResponse = deleteFavoriteFile(remoteFile)
        ApiTestUtils.assertApiResponse(deleteFavoriteFileResponse)
        getFavoriteResponse = getFavoriteFiles(userDrive.driveId, File.SortType.NAME_AZ, 1)
        Assert.assertEquals(
            "The number of favorite files should be lowered by 1",
            favoriteFileCount - 1,
            getFavoriteResponse.data?.size
        )
    }

    @Test
    fun getTestFileActivities() {
        val remoteFile = createFileForTest()
        assertApiResponse(getFileActivities(remoteFile, 1))
        deleteTestFile(remoteFile)
        val deletedFileActivitiesResponse = getFileActivities(remoteFile, 1)
        Assert.assertFalse(deletedFileActivitiesResponse.isSuccess())
    }

    @Test
    fun manageTestFileCommentLifeCycle() {
        val remoteFile = createFileForTest()
        // Get comments
        getFileComments(remoteFile, 1).also {
            assertApiResponse(it)
            Assert.assertTrue("Test file should not have comments", it.data.isNullOrEmpty())
        }

        // Post 2 comments
        val commentBody = "Hello world"
        val commentID = postFileComment(remoteFile, commentBody).also {
            assertApiResponse(it)
            Assert.assertEquals(commentBody, it.data?.body)
        }.data!!.id
        val commentID2 = postFileComment(remoteFile, commentBody).also { assertApiResponse(it) }.data!!.id
        Assert.assertNotEquals("Comments id should be different", commentID, commentID2)
        // Get new comments
        getFileComments(remoteFile, 1).also {
            assertApiResponse(it)
            Assert.assertEquals("There should be 2 comments on the test file", 2, it.data?.size)
        }
        // Delete first comment
        deleteFileComment(remoteFile, commentID)
        Assert.assertEquals("There should be 1 comment on the test file", 1, getFileComments(remoteFile, 1).data?.size)

        // Put second comment
        val putCommentResponse = putFileComment(remoteFile, commentID2, "42")
        assertApiResponse(putCommentResponse)
        putCommentResponse.data?.let { Assert.assertTrue(it) }
        // Delete file
        deleteTestFile(remoteFile)
    }
}