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
import com.google.gson.JsonObject
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
import com.infomaniak.drive.data.api.ApiRepository.getDriveTrash
import com.infomaniak.drive.data.api.ApiRepository.getDropBox
import com.infomaniak.drive.data.api.ApiRepository.getFileActivities
import com.infomaniak.drive.data.api.ApiRepository.getFileComments
import com.infomaniak.drive.data.api.ApiRepository.getFileCount
import com.infomaniak.drive.data.api.ApiRepository.getFileDetails
import com.infomaniak.drive.data.api.ApiRepository.getFileShare
import com.infomaniak.drive.data.api.ApiRepository.getLastActivities
import com.infomaniak.drive.data.api.ApiRepository.getLastModifiedFiles
import com.infomaniak.drive.data.api.ApiRepository.getMySharedFiles
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
import com.infomaniak.drive.utils.ApiTestUtils.getCategory
import com.infomaniak.drive.utils.ApiTestUtils.getShareLink
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.lib.core.networking.HttpClient.okHttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Logging activity testing class
 */
class ApiRepositoryTest : KDriveTest() {
    private lateinit var testFile: File

    @BeforeEach
    @Throws(Exception::class)
    fun setUp() {
        testFile = createFileForTest()
    }

    @AfterEach
    @Throws(Exception::class)
    fun tearDown() {
        deleteTestFile(testFile)
    }

    @Test
    fun getDriveData() {
        assertApiResponse(getAllDrivesData(okHttpClient))
    }

    @Test
    fun getUserProfile() {
        with(getUserProfile(okHttpClient)) {
            assertApiResponse(this)
            assertEquals(userDrive.userId, data?.id, "User ids should be the same")
        }
    }

    @Test
    fun manageFavoriteFileLifecycle() {
        // Create favorite
        assertApiResponse(postFavoriteFile(testFile))

        // File must be a favorite
        with(getFileDetails(testFile)) {
            assertApiResponse(this)
            assertTrue(data!!.isFavorite, "File must be a favorite")
        }

        // Delete created Favorite
        assertApiResponse(deleteFavoriteFile(testFile))
        // File must not be a favorite
        with(getFileDetails(testFile)) {
            assertApiResponse(this)
            assertFalse(data!!.isFavorite, "File must not be a favorite")
        }
    }

    @Test
    fun getFileActivities() {
        assertApiResponse(getFileActivities(testFile, 1))
    }

    @Test
    fun manageFileCommentLifeCycle() {
        // Get comments
        with(getFileComments(testFile, 1)) {
            assertApiResponse(this)
            assertTrue(data.isNullOrEmpty(), "Test file should not have comments")
        }

        // Post 2 comments
        val commentBody = "Hello world"
        val commentID = postFileComment(testFile, commentBody).let {
            assertApiResponse(it)
            assertEquals(commentBody, it.data?.body)
            it.data!!.id
        }

        val commentID2 = postFileComment(testFile, commentBody).let {
            assertApiResponse(it)
            it.data!!.id
        }
        assertNotEquals(commentID, commentID2, "Comments id should be different")

        // Likes the second comment
        with(postFileCommentLike(testFile, commentID2)) {
            assertApiResponse(this)
            assertTrue(data ?: false)
        }

        // Get new comments
        with(getFileComments(testFile, 1)) {
            assertApiResponse(this)
            assertEquals(2, data?.size, "There should be 2 comments on the test file")
            val likedComment = data?.find { comment -> comment.id == commentID2 }?.liked
            assertNotNull(likedComment, "Liked comment should not be null")
            assertTrue(likedComment!!, "Comment should be liked")
        }
        // Delete first comment
        deleteFileComment(testFile, commentID)
        assertEquals(1, getFileComments(testFile, 1).data?.size, "There should be 1 comment on the test file")

        // Put second comment
        with(putFileComment(testFile, commentID2, "42")) {
            assertApiResponse(this)
            assertTrue(data ?: false)
        }

        // Unlike the comment
        with(postFileCommentUnlike(testFile, commentID2)) {
            assertApiResponse(this)
            assertTrue(data ?: false)
        }

        // Make sure data has been updated
        with(getFileComments(testFile, 1)) {
            val comment = data?.find { commentRes -> commentRes.id == commentID2 }
            assertApiResponse(this)
            assertEquals(
                "42",
                comment?.body,
                "Comment should be equal to 42",
            )
            assertNotNull(comment)
            assertFalse(comment!!.liked)
        }
    }

    @Test
    fun duplicateFile() {
        val copyName = "test copy"
        val copyFile = duplicateFile(testFile, copyName, ROOT_ID).let {
            assertApiResponse(it)
            assertEquals(copyName, it.data?.name, "The copy name should be equal to $copyName")
            assertNotEquals(testFile.id, it.data?.id, "The id should be different from the original file")
            assertEquals(testFile.driveColor, it.data?.driveColor)
            it.data!!
        }

        // Duplicate one more time with same name and location
        with(duplicateFile(testFile, copyName, ROOT_ID)) {
            assertApiResponse(this)
            assertEquals("$copyName (1)", data?.name, "The copy name should be equal to $copyName (1)")
            deleteTestFile(data!!)
        }

        deleteTestFile(copyFile)
    }

    @Test
    fun moveFileToAnotherFolder() {
        val file = createFileForTest()
        // Create test folder
        val folderName = "folder"
        with(createFolder(okHttpClient, userDrive.driveId, ROOT_ID, folderName)) {
            assertNotNull(data)
            // Move file in the test folder
            assertApiResponse(moveFile(file, data!!))
            val folderFileCount = getFileCount(data!!)
            assertApiResponse(folderFileCount)
            assertEquals(1, folderFileCount.data?.count, "There should be 1 file in the folder")

            val folderData = getFileDetails(data!!).data
            assertNotNull(folderData)
            assertTrue(folderData!!.children.contains(file))
            deleteTestFile(folderData)
        }
    }

    @Test
    fun createTeamFolder() {
        with(createTeamFolder(okHttpClient, userDrive.driveId, "teamFolder", true)) {
            assertApiResponse(this)
            assertTrue(data!!.visibility.contains("is_team_space_folder"), "visibility should be 'is_team_space_folder'")
            deleteTestFile(data!!)
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
        with(postFileShareLink(testFile, body)) {
            assertApiResponse(this)
            assertEquals("public", data!!.permission.name.lowercase(), "Permission should be public")
            assertFalse(data!!.blockDownloads, "Block downloads should be false")
            assertFalse(data!!.canEdit, "Can edit should be false")
            assertFalse(data!!.showStats, "Show stats should be false")
            assertFalse(data!!.blockDownloads, "Block comments should be false")
            assertFalse(data!!.blockInformation, "Block information should be false")
        }

        with(getFileShare(okHttpClient, testFile)) {
            assertApiResponse(this)
            assertEquals("/${testFile.name}", data!!.path, "Path should be the name of the file")
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

        with(getShareLink(testFile)) {
            assertApiResponse(this)
            assertEquals("public", data!!.permission.name.lowercase(), "Permission should be public")
            assertTrue(data!!.blockDownloads, "block downloads should be true")
            assertTrue(data!!.canEdit, "can edit should be true")
            assertTrue(data!!.showStats, "show stats should be true")
            assertTrue(data!!.blockDownloads, "block comments should be true")
            assertTrue(data!!.blockInformation, "Block information should be true")
        }

        with(deleteFileShareLink(testFile)) {
            assertApiResponse(this)
            assertTrue(data!!)
        }
        assertFalse(postFileShareCheck(testFile, body).isSuccess())
    }

    @Test
    fun shareLink() {
        val fileShareLink = postFileShare(testFile)
        assertTrue(

            fileShareLink.contains("https://drive.infomaniak.com/drive/[0-9]+/file/[0-9]+/share".toRegex()),
            "Link should match regex 'https://drive.infomaniak.com/drive/[0-9]+/file/[0-9]+/share/'",
        )
    }

    @Test
    fun manageCategoryLifecycle() {
        var name = "category tests"
        var color = "#0000FF"
        val categoryId = createCategory(userDrive.driveId, name, color).let {
            assertApiResponse(it)
            assertEquals("category tests", it.data?.name, "Name of the category should be equals to 'category tests'")
            assertEquals("#0000FF", it.data?.color, "Color of the category should be equals to blue")
            it.data!!.id
        }

        // Create again the same category should fail
        with(createCategory(userDrive.driveId, "category tests", "#0000FF")) {
            assertFalse(isSuccess())
            assertEquals(
                "Category already exist error",
                error?.description,
                "Error description should be 'category already exist error'"
            )
        }

        name = "update cat"
        color = "#FF0000"
        with(editCategory(userDrive.driveId, categoryId, name, color)) {
            assertApiResponse(this)
            assertEquals("update cat", data?.name, "Name of the category should be equals to 'update cat'")
            assertEquals("#FF0000", data?.color, "Color of the category should be equals to red")
        }

        assertApiResponse(deleteCategory(userDrive.driveId, categoryId))
        assertNull(
            getCategory(userDrive.driveId).data?.find { cat -> cat.id == categoryId },
            "The category shouldn't be found anymore",
        )
    }

    @Test
    fun addCategoryToFile() {
        // Create a test category
        val category = createCategory(userDrive.driveId, "test cat", "#FFF").data
        assertNotNull(category)
        // Add the category to the test file
        addCategory(testFile, category!!.id)
        with(getFileDetails(testFile)) {
            assertApiResponse(this)
            assertNotNull(
                data!!.categories.find { cat -> cat.id == category.id }, "The test category should be found"
            )
        }
        // Delete the category before removing it from the test file
        deleteCategory(userDrive.driveId, category.id)
        with(getFileDetails(testFile)) {
            assertApiResponse(this)
            assertTrue(data!!.categories.isNullOrEmpty(), "The test file should not have category")
        }
    }

    @Test
    fun removeCategoryToFile() {
        // Create a test category
        val category = createCategory(userDrive.driveId, "test cat", "#000").data
        assertNotNull(category)
        // Add the category to the test file
        addCategory(testFile, category!!.id)
        // Remove the category
        removeCategory(testFile, category.id)
        with(getFileDetails(testFile)) {
            assertApiResponse(this)
            assertTrue(data!!.categories.isNullOrEmpty(), "The test file should not have a category")
        }
        // Delete the test category and file
        deleteCategory(userDrive.driveId, category.id)
    }

    @Test
    fun getLastActivityTest() {
        with(getLastActivities(userDrive.driveId, 1)) {
            assertApiResponse(this)
            assertTrue(data!!.isNotEmpty(), "Last activities shouldn't be empty or null")
        }
    }

    @Disabled("Don't know the wanted api behaviour")
    @Test
    fun postTestFolderAccess() {
        val folder = createFolder(okHttpClient, userDrive.driveId, ROOT_ID, "folder").data
        assertNotNull(folder, "test folder must not be null")
        val postResponse = postFolderAccess(folder!!)
        assertApiResponse(postResponse)
        deleteTestFile(folder)
    }

    @Test
    fun manageDropboxLifecycle() {
        // Create a folder to convert it in dropbox
        val name = "testFolder"
        val folder = createFolder(okHttpClient, userDrive.driveId, ROOT_ID, name, false).data
        assertNotNull(folder)
        // No dropbox yet
        assertNull(getDropBox(folder!!).data, "not dropbox should be returned, data should be null")

        val maxSize = 16384L
        val body = arrayMapOf(
            "email_when_finished" to true,
            "limit_file_size" to maxSize,
            "password" to "password"
        )
        // Add a dropBox
        val dropboxId = postDropBox(folder, body).let {
            assertApiResponse(it)
            assertTrue(it.data!!.emailWhenFinished, "Email when finished must be true")
            assertEquals(maxSize, it.data!!.limitFileSize, "Limit file size should be $maxSize")
            it.data!!.id
        }

        with(getDropBox(folder)) {
            assertApiResponse(this)
            assertEquals(name, data!!.alias, "Dropbox name should be '$name'")
            assertEquals(dropboxId, data!!.id, "Dropbox id should be $dropboxId")
        }
        val updateBody = JsonObject().apply {
            addProperty("email_when_finished", false)
            addProperty("limit_file_size", maxSize * 2)
        }

        with(updateDropBox(folder, updateBody)) {
            assertApiResponse(this)
            assertTrue(data ?: false)
        }

        with(getDropBox(folder)) {
            assertApiResponse(this)
            assertEquals(dropboxId, data!!.id, "Dropbox id should be $dropboxId")
            assertEquals(false, data!!.emailWhenFinished, "Email when finished should be false")
            assertEquals(maxSize * 2, data!!.limitFileSize, "Limit file size should be ${maxSize * 2}")
        }

        assertApiResponse(deleteDropBox(folder))
        // No dropbox left
        assertNull(getDropBox(folder).data, "not dropbox should be returned, data should be null")
        deleteTestFile(folder)
    }

    @Test
    fun manageTrashLifecycle() {
        val file = createFileForTest()
        val newName = "Trash test"
        renameFile(file, newName)
        val modifiedFile = getLastModifiedFiles(userDrive.driveId).data?.first()
        assertNotNull(modifiedFile, "Modified file should not be null")
        deleteTestFile(modifiedFile!!)
        with(getTrashFile(modifiedFile, File.SortType.RECENT, 1)) {
            assertApiResponse(this)
            assertEquals(file.id, data?.id, "file id should be the same")
            assertEquals(newName, data?.name, "file name should be updated to '$newName'")
        }

        with(getDriveTrash(userDrive.driveId, File.SortType.RECENT, 1)) {
            assertApiResponse(this)
            Assert.assertTrue("Trash should not be empty", data!!.isNotEmpty())
            Assert.assertEquals("Last trash file's id should be ${file.id}", file.id, data?.first()?.id)
        }

        // Restore the file from the trash
        assertApiResponse(postRestoreTrashFile(modifiedFile, mapOf("destination_directory_id" to ROOT_ID)))
        with(getDriveTrash(userDrive.driveId, File.SortType.RECENT, 1)) {
            assertApiResponse(this)
            if (data!!.isNotEmpty()) {
                Assert.assertNotEquals("Last trash file's id should not be ${file.id}", file.id, data?.first()?.id)
            }
        }
        deleteTestFile(modifiedFile)
    }

    @Test
    fun permanentlyDeleteFile() {
        // Clean the trash to make sure nothing is left in
        assertApiResponse(emptyTrash(userDrive.driveId))
        with(getDriveTrash(userDrive.driveId, File.SortType.NAME_ZA, 1)) {
            assertApiResponse(this)
            assertTrue(data!!.isEmpty(), "Trash should be empty")
        }

        // Create a new file, put it in trash then permanently delete it
        with(createFileForTest()) {
            deleteTestFile(this)
            deleteTrashFile(this)
        }

        // Trash should still be empty
        with(getDriveTrash(userDrive.driveId, File.SortType.NAME_ZA, 1)) {
            assertApiResponse(this)
            assertTrue(data!!.isEmpty(), "Trash should be empty")
        }
    }


    @Test
    fun mySharedFileTest() {
        val order = File.SortType.BIGGER
        assertApiResponse(getMySharedFiles(okHttpClient, userDrive.driveId, order.order, order.orderBy, 1))
    }
}
