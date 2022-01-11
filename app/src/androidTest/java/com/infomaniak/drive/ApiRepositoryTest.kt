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
import com.infomaniak.drive.data.api.ApiRepository.addCategory
import com.infomaniak.drive.data.api.ApiRepository.createCategory
import com.infomaniak.drive.data.api.ApiRepository.deleteCategory
import com.infomaniak.drive.data.api.ApiRepository.deleteFavoriteFile
import com.infomaniak.drive.data.api.ApiRepository.deleteFileComment
import com.infomaniak.drive.data.api.ApiRepository.deleteFileShareLink
import com.infomaniak.drive.data.api.ApiRepository.editCategory
import com.infomaniak.drive.data.api.ApiRepository.getAllDrivesData
import com.infomaniak.drive.data.api.ApiRepository.getCategory
import com.infomaniak.drive.data.api.ApiRepository.getFavoriteFiles
import com.infomaniak.drive.data.api.ApiRepository.getFileActivities
import com.infomaniak.drive.data.api.ApiRepository.getFileComments
import com.infomaniak.drive.data.api.ApiRepository.getFileDetails
import com.infomaniak.drive.data.api.ApiRepository.getShareLink
import com.infomaniak.drive.data.api.ApiRepository.getUserProfile
import com.infomaniak.drive.data.api.ApiRepository.postFavoriteFile
import com.infomaniak.drive.data.api.ApiRepository.postFileComment
import com.infomaniak.drive.data.api.ApiRepository.postFileCommentLike
import com.infomaniak.drive.data.api.ApiRepository.postFileCommentUnlike
import com.infomaniak.drive.data.api.ApiRepository.postFileShareCheck
import com.infomaniak.drive.data.api.ApiRepository.postFileShareLink
import com.infomaniak.drive.data.api.ApiRepository.putFileComment
import com.infomaniak.drive.data.api.ApiRepository.putFileShareLink
import com.infomaniak.drive.data.api.ApiRepository.removeCategory
import com.infomaniak.drive.data.api.ApiRoutes.postFileShare
import com.infomaniak.drive.data.models.File
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
    private lateinit var testFile: File

    @Before
    @Throws(Exception::class)
    fun setUp() {
        realm = Realm.getInstance(getConfig())
        testFile = createFileForTest()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        deleteTestFile(testFile)
        if (!realm.isClosed) realm.close()
        Realm.deleteRealm(getConfig())
    }

    @Test
    fun getDriveData(): Unit = runBlocking {
        val okHttpClient = userDrive.userId.let { KDriveHttpClient.getHttpClient(it) }
        val drivesDataResponse = getAllDrivesData(okHttpClient)
        assertApiResponse(drivesDataResponse)
    }

    @Test
    fun getTestUserProfile(): Unit = runBlocking {
        val okHttpClient = userDrive.userId.let { KDriveHttpClient.getHttpClient(it) }
        val userProfileResponse = getUserProfile(okHttpClient)
        assertApiResponse(userProfileResponse)
        Assert.assertEquals("User ids should be the same", userDrive.userId, userProfileResponse.data?.id)
    }

    @Test
    fun manageFavoriteFileLifecycle(): Unit = runBlocking {
        // Make the file a favorite
        val postFavoriteFileResponse = postFavoriteFile(testFile)
        assertApiResponse(postFavoriteFileResponse)
        // Get the favorite files
        var getFavoriteResponse = getFavoriteFiles(userDrive.driveId, File.SortType.NAME_AZ, 1)
        assertApiResponse(getFavoriteResponse)
        Assert.assertTrue("Favorite file should exists", getFavoriteResponse.data!!.isNotEmpty())
        val favoriteFileCount = getFavoriteResponse.data!!.size
        // Delete the favorite file
        val deleteFavoriteFileResponse = deleteFavoriteFile(testFile)
        assertApiResponse(deleteFavoriteFileResponse)
        getFavoriteResponse = getFavoriteFiles(userDrive.driveId, File.SortType.NAME_AZ, 1)
        Assert.assertEquals(
            "The number of favorite files should be lowered by 1",
            favoriteFileCount - 1,
            getFavoriteResponse.data?.size
        )
    }

    @Test
    fun getTestFileActivities() {
        val file = createFileForTest()
        assertApiResponse(getFileActivities(file, 1))
        deleteTestFile(file)
    }

    @Test
    fun manageTestFileCommentLifeCycle() {
        // Get comments
        getFileComments(testFile, 1).also {
            assertApiResponse(it)
            Assert.assertTrue("Test file should not have comments", it.data.isNullOrEmpty())
        }

        // Post 2 comments
        val commentBody = "Hello world"
        val commentID = postFileComment(testFile, commentBody).also {
            assertApiResponse(it)
            Assert.assertEquals(commentBody, it.data?.body)
        }.data!!.id

        val commentID2 = postFileComment(testFile, commentBody).also { assertApiResponse(it) }.data!!.id
        Assert.assertNotEquals("Comments id should be different", commentID, commentID2)

        // Likes the second comment
        postFileCommentLike(testFile, commentID2).also {
            assertApiResponse(it)
            Assert.assertTrue(it.data!!)
        }

        // Get new comments
        getFileComments(testFile, 1).also {
            assertApiResponse(it)
            Assert.assertEquals("There should be 2 comments on the test file", 2, it.data?.size)
            it.data?.find { comment -> comment.id == commentID2 }?.liked?.let { it1 -> Assert.assertTrue(it1) }
        }
        // Delete first comment
        deleteFileComment(testFile, commentID)
        Assert.assertEquals("There should be 1 comment on the test file", 1, getFileComments(testFile, 1).data?.size)

        // Put second comment
        val putCommentResponse = putFileComment(testFile, commentID2, "42")
        assertApiResponse(putCommentResponse)
        putCommentResponse.data?.let { Assert.assertTrue(it) }

        // Unlike the comment
        postFileCommentUnlike(testFile, commentID2).also {
            assertApiResponse(it)
            Assert.assertTrue(it.data!!)
        }

        // Make sure data has been updated
        getFileComments(testFile, 1).also {
            val comment = it.data?.find { commentRes -> commentRes.id == commentID2 }
            assertApiResponse(it)
            Assert.assertEquals(
                "Comment should be equal to 42",
                "42",
                comment?.body
            )
            Assert.assertNotNull(comment)
            Assert.assertFalse(comment!!.liked)
        }
    }

    @Test
    fun shareLinkTest() {
        val body = mapOf(
            "permission" to "public",
            "block_downloads" to "false",
            "canEdit" to "false",
            "show_stats" to "false",
            "block_comments" to "false",
            "block_information" to "false"
        )
        postFileShareLink(
            testFile,
            body
        ).also {
            assertApiResponse(it)
            Assert.assertEquals("Permission should be public", "public", it.data!!.permission.name.lowercase())
            Assert.assertFalse("block downloads should be false", it.data!!.blockDownloads)
            Assert.assertFalse("can edit should be false", it.data!!.canEdit)
            Assert.assertFalse("show stats should be false", it.data!!.showStats)
            Assert.assertFalse("block comments should be false", it.data!!.blockDownloads)
            Assert.assertFalse("Block information should be false", it.data!!.blockInformation)
        }

        val response = putFileShareLink(
            testFile, mapOf(
                "permission" to "public",
                "block_downloads" to "true",
                "canEdit" to "true",
                "show_stats" to "true",
                "block_comments" to "true",
                "block_information" to "true"
            )
        )
        assertApiResponse(response)

        getShareLink(testFile).also {
            assertApiResponse(it)
            Assert.assertEquals("Permission should be public", "public", it.data!!.permission.name.lowercase())
            Assert.assertTrue("block downloads should be true", it.data!!.blockDownloads)
            Assert.assertTrue("can edit should be true", it.data!!.canEdit)
            Assert.assertTrue("show stats should be true", it.data!!.showStats)
            Assert.assertTrue("block comments should be true", it.data!!.blockDownloads)
            Assert.assertTrue("Block information should be true", it.data!!.blockInformation)
        }

        deleteFileShareLink(testFile).also {
            assertApiResponse(it)
            Assert.assertTrue(it.data!!)
        }
        Assert.assertFalse(postFileShareCheck(testFile, body).isSuccess())
    }

    @Test
    fun shareLink() {
        val fileShareLink = postFileShare(testFile)
        Assert.assertTrue(
            "Link should match regex 'https://drive.infomaniak.com/drive/[0-9]+/file/[0-9]+/share/'",
            fileShareLink.contains("https://drive.infomaniak.com/drive/[0-9]+/file/[0-9]+/share".toRegex())
        )
    }

    @Test
    fun manageCategoryLifecycle() {
        val categoryResponse = createCategory(userDrive.driveId, "category tests", "#0000FF").also {
            assertApiResponse(it)
            Assert.assertEquals("Name of the category should be equals to 'category tests'", "category tests", it.data?.name)
            Assert.assertEquals("Color of the category should be equals to blue", "#0000FF", it.data?.color)
        }
        val categoryId = categoryResponse.data!!.id

        // Create again the same category should fail
        createCategory(userDrive.driveId, "category tests", "#0000FF").also {
            Assert.assertFalse(it.isSuccess())
            Assert.assertEquals(
                "Error description should be 'category already exist error'",
                "Category already exist error",
                it.error?.description
            )
        }

        editCategory(userDrive.driveId, categoryId, "update cat", "#FF0000").also {
            assertApiResponse(it)
            Assert.assertEquals("Name of the category should be equals to 'update cat'", "update cat", it.data?.name)
            Assert.assertEquals("Color of the category should be equals to red", "#FF0000", it.data?.color)
        }

        assertApiResponse(deleteCategory(userDrive.driveId, categoryId))
        Assert.assertNull(
            "The category shouldn't be found anymore",
            getCategory(userDrive.driveId).data?.find { cat -> cat.id == categoryId })
    }

    @Test
    fun addCategoryToFile() {
        val file = createFileForTest()
        // Create a test category
        val category = createCategory(userDrive.driveId, "test cat", "#FFF").data
        Assert.assertNotNull(category)
        // Add the category to the test file
        addCategory(file, category!!.id)
        getFileDetails(file).also {
            assertApiResponse(it)
            Assert.assertNotNull(
                "The test category should be found",
                it.data!!.categories.find { cat -> cat.id == category.id })
        }
        // Delete the category before removing it from the test file
        deleteCategory(userDrive.driveId, category.id)
        getFileDetails(file).also {
            assertApiResponse(it)
            Assert.assertTrue("The test file should not have category", it.data?.categories.isNullOrEmpty())
        }
        deleteTestFile(file)
    }

    @Test
    fun removeCategoryToFile() {
        val file = createFileForTest()
        // Create a test category
        val category = createCategory(userDrive.driveId, "test cat", "#000").data
        Assert.assertNotNull(category)
        // Add the category to the test file
        addCategory(file, category!!.id)
        // Remove the category
        removeCategory(file, category.id)
        getFileDetails(file).also {
            assertApiResponse(it)
            Assert.assertTrue("The test file should not have a category", it.data?.categories.isNullOrEmpty())
        }
        // Delete the test category and file
        deleteCategory(userDrive.driveId, category.id)
        deleteTestFile(file)
    }
}