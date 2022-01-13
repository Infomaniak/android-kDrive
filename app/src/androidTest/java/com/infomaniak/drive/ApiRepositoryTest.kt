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

import androidx.collection.arrayMapOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRepository.addCategory
import com.infomaniak.drive.data.api.ApiRepository.createCategory
import com.infomaniak.drive.data.api.ApiRepository.createFolder
import com.infomaniak.drive.data.api.ApiRepository.createTeamFolder
import com.infomaniak.drive.data.api.ApiRepository.deleteCategory
import com.infomaniak.drive.data.api.ApiRepository.deleteDropBox
import com.infomaniak.drive.data.api.ApiRepository.deleteFavoriteFile
import com.infomaniak.drive.data.api.ApiRepository.deleteFileComment
import com.infomaniak.drive.data.api.ApiRepository.deleteFileShareLink
import com.infomaniak.drive.data.api.ApiRepository.deleteTrashFile
import com.infomaniak.drive.data.api.ApiRepository.duplicateFile
import com.infomaniak.drive.data.api.ApiRepository.editCategory
import com.infomaniak.drive.data.api.ApiRepository.emptyTrash
import com.infomaniak.drive.data.api.ApiRepository.getAllDrivesData
import com.infomaniak.drive.data.api.ApiRepository.getCategory
import com.infomaniak.drive.data.api.ApiRepository.getDriveTrash
import com.infomaniak.drive.data.api.ApiRepository.getDropBox
import com.infomaniak.drive.data.api.ApiRepository.getFavoriteFiles
import com.infomaniak.drive.data.api.ApiRepository.getFileActivities
import com.infomaniak.drive.data.api.ApiRepository.getFileComments
import com.infomaniak.drive.data.api.ApiRepository.getFileCount
import com.infomaniak.drive.data.api.ApiRepository.getFileDetails
import com.infomaniak.drive.data.api.ApiRepository.getFileShare
import com.infomaniak.drive.data.api.ApiRepository.getLastActivities
import com.infomaniak.drive.data.api.ApiRepository.getMySharedFiles
import com.infomaniak.drive.data.api.ApiRepository.getShareLink
import com.infomaniak.drive.data.api.ApiRepository.getTrashFile
import com.infomaniak.drive.data.api.ApiRepository.getUserProfile
import com.infomaniak.drive.data.api.ApiRepository.moveFile
import com.infomaniak.drive.data.api.ApiRepository.postDropBox
import com.infomaniak.drive.data.api.ApiRepository.postFavoriteFile
import com.infomaniak.drive.data.api.ApiRepository.postFileComment
import com.infomaniak.drive.data.api.ApiRepository.postFileCommentLike
import com.infomaniak.drive.data.api.ApiRepository.postFileCommentUnlike
import com.infomaniak.drive.data.api.ApiRepository.postFileShareCheck
import com.infomaniak.drive.data.api.ApiRepository.postFileShareLink
import com.infomaniak.drive.data.api.ApiRepository.postFolderAccess
import com.infomaniak.drive.data.api.ApiRepository.postRestoreTrashFile
import com.infomaniak.drive.data.api.ApiRepository.putFileComment
import com.infomaniak.drive.data.api.ApiRepository.putFileShareLink
import com.infomaniak.drive.data.api.ApiRepository.removeCategory
import com.infomaniak.drive.data.api.ApiRepository.renameFile
import com.infomaniak.drive.data.api.ApiRepository.updateDropBox
import com.infomaniak.drive.data.api.ApiRoutes.postFileShare
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.ApiTestUtils.assertApiResponse
import com.infomaniak.drive.utils.ApiTestUtils.createFileForTest
import com.infomaniak.drive.utils.ApiTestUtils.deleteTestFile
import com.infomaniak.drive.utils.KDriveHttpClient
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.lib.core.networking.HttpClient.okHttpClient
import io.realm.Realm
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.*
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
    fun duplicateTestFile() {
        val copyName = "test copy"
        val copyFile = duplicateFile(testFile, copyName, ROOT_ID).also {
            assertApiResponse(it)
            Assert.assertEquals("The copy name should be equal to $copyName", copyName, it.data?.name)
            Assert.assertNotEquals("The id should be different from the original file", testFile.id, it.data?.id)
            Assert.assertEquals(testFile.driveColor, it.data?.driveColor)

        }.data!!

        // Duplicate one more time with same name and location
        duplicateFile(testFile, copyName, ROOT_ID).also {
            assertApiResponse(it)
            Assert.assertEquals("The copy name should be equal to $copyName (1)", "$copyName (1)", it.data?.name)
            deleteTestFile(it.data!!)
        }

        deleteTestFile(copyFile)
    }

    @Test
    fun moveFileToAnotherFolder() {
        val file = createFileForTest()
        // Create test folder
        val folderName = "f"
        createFolder(OkHttpClient(), userDrive.driveId, ROOT_ID, folderName).also {
            Assert.assertNotNull(it.data)
            // Move file in the test folder
            assertApiResponse(moveFile(file, it.data!!))
            val folderFileCount = getFileCount(it.data!!)
            assertApiResponse(folderFileCount)
            Assert.assertEquals("There should be 1 file in the folder", 1, folderFileCount.data?.count)

            val folderData = getFileDetails(it.data!!).data
            Assert.assertNotNull(folderData)
            Assert.assertTrue(folderData!!.children.contains(file))
            deleteTestFile(folderData)
        }
    }

    @Test
    fun createTeamTestFolder() {
        createTeamFolder(OkHttpClient(), userDrive.driveId, "teamFolder", true).also {
            assertApiResponse(it)
            Assert.assertTrue("visibility should be 'is_team_space_folder", it.data!!.visibility.contains("is_team_space_folder"))
            deleteTestFile(it.data!!)
        }
    }

    @Test
    fun shareLinkTest() {
        // TODO Changes for api-v2 : boolean instead of "true", "false", and can_edit instead of canEdit
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
            Assert.assertFalse("Block downloads should be false", it.data!!.blockDownloads)
            Assert.assertFalse("Can edit should be false", it.data!!.canEdit)
            Assert.assertFalse("Show stats should be false", it.data!!.showStats)
            Assert.assertFalse("Block comments should be false", it.data!!.blockDownloads)
            Assert.assertFalse("Block information should be false", it.data!!.blockInformation)
        }

        getFileShare(OkHttpClient(), testFile).also {
            assertApiResponse(it)
            Assert.assertEquals("Path should be the name of the file", "/${testFile.name}", it.data!!.path)
        }

        val response = putFileShareLink(
            testFile, mapOf(
                "permission" to "public",
                "block_downloads" to true,
                "can_edit" to true,
                "show_stats" to true,
                "block_comments" to true,
                "block_information" to true
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

    @Test
    fun getLastActivityTest() {
        getLastActivities(userDrive.driveId, 1).also {
            assertApiResponse(it)
            Assert.assertTrue("Last activities shouldn't be empty or null", it.data!!.isNotEmpty())
        }
    }

    @Ignore("Don't know the wanted api behaviour")
    @Test
    fun postTestFolderAccess() {
        val folder = createFolder(OkHttpClient(), userDrive.driveId, ROOT_ID, "folder").data
        Assert.assertNotNull("test folder must not be null", folder)
        val postResponse = postFolderAccess(folder!!)
        assertApiResponse(postResponse)
        deleteTestFile(folder)
    }

    @Test
    fun manageDropboxLifecycle() {
        // Create a folder to convert it in dropbox
        val name = "testFold"
        val folder = createFolder(okHttpClient, userDrive.driveId, ROOT_ID, name, false).data
        Assert.assertNotNull(folder)
        // No dropbox yet
        assertApiResponse(getDropBox(folder!!), false)

        val maxSize = 16384L
        val body = arrayMapOf(
            "email_when_finished" to true,
            "limit_file_size" to maxSize,
            "password" to "password"
        )
        // Add a dropBox
        val dropboxId = postDropBox(folder, body).let {
            assertApiResponse(it)
            Assert.assertTrue("Email when finished must be true", it.data!!.emailWhenFinished)
            Assert.assertEquals("Limit file size should be $maxSize", maxSize, it.data!!.limitFileSize)
            it.data!!.id
        }

        getDropBox(folder).also {
            assertApiResponse(it)
            Assert.assertEquals("Dropbox name should be '$name'", name, it.data!!.alias)
            Assert.assertEquals("Dropbox id should be $dropboxId", dropboxId, it.data!!.id)
        }
        val data = JsonObject().apply {
            addProperty("email_when_finished", false)
            addProperty("limit_file_size", maxSize * 2)
        }

        updateDropBox(folder, data).also {
            assertApiResponse(it)
            Assert.assertTrue(it.data!!)
        }

        getDropBox(folder).also {
            assertApiResponse(it)
            Assert.assertEquals("Dropbox id should be $dropboxId", dropboxId, it.data!!.id)
            Assert.assertEquals("Email when finished should be false", false, it.data!!.emailWhenFinished)
            Assert.assertEquals("Limit file size should be ${maxSize * 2}", maxSize * 2, it.data!!.limitFileSize)
        }

        assertApiResponse(deleteDropBox(folder))
        // No dropbox left
        assertApiResponse(getDropBox(folder), false)
        deleteTestFile(folder)
    }

    @Test
    fun manageTrashLifecycle() {
        createFileForTest().also { file ->
            val newName = "Trash test"
            renameFile(file, newName)
            val modifiedFile = ApiRepository.getLastModifiedFiles(userDrive.driveId).data?.first()
            Assert.assertNotNull(modifiedFile)
            deleteTestFile(modifiedFile!!)
            getTrashFile(modifiedFile, File.SortType.RECENT).also {
                assertApiResponse(it)
                Assert.assertEquals("file id should be the same", it.data?.id, file.id)
                Assert.assertEquals("file name should be updated to 'Trash test'", newName, it.data?.name)
            }

            getDriveTrash(userDrive.driveId, File.SortType.RECENT, 1, 30).also {
                assertApiResponse(it)
                Assert.assertTrue("Trash should not be empty", it.data!!.isNotEmpty())
                Assert.assertEquals("Last file trashed should be 'trash test'", file.id, it.data?.first()?.id)
            }

            // Restore the file from the trash
            assertApiResponse(postRestoreTrashFile(modifiedFile, mapOf("destination_folder_id" to ROOT_ID)))
            getDriveTrash(userDrive.driveId, File.SortType.RECENT, 1, 30).also {
                assertApiResponse(it)
                if (it.data!!.isNotEmpty()) {
                    Assert.assertNotEquals("Last file trashed should not be 'trash test'", file.id, it.data?.first()?.id)
                }
            }
            deleteTestFile(modifiedFile)
        }

        // Clean the trash to make sure nothing is left in
        assertApiResponse(emptyTrash(userDrive.driveId, 30))
        getDriveTrash(userDrive.driveId, File.SortType.NAME_ZA, 1, 30).also {
            assertApiResponse(it)
            Assert.assertTrue("Trash should be empty", it.data!!.isEmpty())
        }

        // Create a new file, put it in trash then permanently delete it
        createFileForTest().apply {
            deleteTestFile(this)
            deleteTrashFile(this)
        }

        // Trash should still be empty
        getDriveTrash(userDrive.driveId, File.SortType.NAME_ZA, 1, 30).also {
            assertApiResponse(it)
            Assert.assertTrue("Trash should be empty", it.data!!.isEmpty())
        }
    }

    @Test
    fun mySharedFileTest() {
        val order = File.SortType.BIGGER
        assertApiResponse(getMySharedFiles(OkHttpClient(), userDrive.driveId, order.order, order.orderBy, 1))
    }
}