/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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

import android.util.Log
import com.google.gson.JsonObject
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRepository.addCategory
import com.infomaniak.drive.data.api.ApiRepository.createCategory
import com.infomaniak.drive.data.api.ApiRepository.createShareLink
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
import com.infomaniak.drive.data.api.ApiRepository.getLastActivities
import com.infomaniak.drive.data.api.ApiRepository.getMySharedFiles
import com.infomaniak.drive.data.api.ApiRepository.getTrashedFile
import com.infomaniak.drive.data.api.ApiRepository.getUserProfile
import com.infomaniak.drive.data.api.ApiRepository.moveFile
import com.infomaniak.drive.data.api.ApiRepository.postFavoriteFile
import com.infomaniak.drive.data.api.ApiRepository.postFileComment
import com.infomaniak.drive.data.api.ApiRepository.postLikeComment
import com.infomaniak.drive.data.api.ApiRepository.postRestoreTrashFile
import com.infomaniak.drive.data.api.ApiRepository.postUnlikeComment
import com.infomaniak.drive.data.api.ApiRepository.putFileComment
import com.infomaniak.drive.data.api.ApiRepository.removeCategory
import com.infomaniak.drive.data.api.ApiRepository.updateDropBox
import com.infomaniak.drive.data.api.ApiRepository.updateShareLink
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.utils.ApiTestUtils.assertApiResponseData
import com.infomaniak.drive.utils.ApiTestUtils.createDropBoxForTest
import com.infomaniak.drive.utils.ApiTestUtils.createFileForTest
import com.infomaniak.drive.utils.ApiTestUtils.createFolderWithName
import com.infomaniak.drive.utils.ApiTestUtils.deleteTestFile
import com.infomaniak.drive.utils.ApiTestUtils.getCategory
import com.infomaniak.drive.utils.ApiTestUtils.putNewFileInTrash
import com.infomaniak.drive.utils.Utils.ROOT_ID
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

/**
 * Logging activity testing class
 */
class ApiRepositoryTest : KDriveTest() {

    private val randomSuffix = UUID.randomUUID()

    @Test
    @DisplayName("Check if remote drive data are correctly retrieved")
    fun getDriveData() {
        assertApiResponseData(getAllDrivesData(okHttpClient))
    }

    @Test
    @DisplayName("Check if remote user profile is correctly retrieved")
    fun getUserProfileFromRemote() = runTest {
        with(getUserProfile(okHttpClient)) {
            assertApiResponseData(this)
            assertEquals(userDrive.userId, data?.id, "User ids should be the same")
        }
    }

    @Test
    @DisplayName("Create a folder with team space visibility")
    fun createTeamFolder() {
        with(createTeamFolder(okHttpClient, userDrive.driveId, "teamFolder$randomSuffix", true)) {
            assertApiResponseData(this)
            assertTrue(data!!.visibility.contains("is_team_space_folder"), "visibility should be 'is_team_space_folder'")
            deleteTestFile(data!!)
        }
    }

    @Test
    @DisplayName("Create a category then delete it")
    fun createCategory() {
        val color = "#0000FF"
        val name = "testCreateCategory-$randomSuffix}"
        val categoryId = createCategory(userDrive.driveId, name, color).let {
            assertApiResponseData(it)
            assertEquals(name, it.data?.name, "Name of the category should be equals to $name")
            assertEquals(color, it.data?.color, "Color of the category should be equals to $color")
            it.data!!.id
        }

        // Create again the same category should fail
        with(createCategory(userDrive.driveId, name, color)) {
            assertFalse(isSuccess())
            assertEquals(
                "category_already_exist_error",
                error?.code,
                "Error code should be 'category_already_exist_error'"
            )
        }

        // Delete the category
        assertApiResponseData(deleteCategory(userDrive.driveId, categoryId))
        assertNull(
            actual = getCategory(userDrive.driveId).data?.find { cat -> cat.id == categoryId },
            message = "The category shouldn't be found anymore",
        )
    }

    @Test
    @DisplayName("Update a created category then delete it")
    fun updateCategory() {
        var color = "#0000FF"
        var name = "category-$randomSuffix}"
        val categoryId = createCategory(userDrive.driveId, name, color).let {
            assertNotNull(actual = it.data)
            it.data!!.id
        }

        name = "updateCategory-$randomSuffix}"
        color = "#FF0000"
        // Edit the category by changing its color and name
        with(editCategory(userDrive.driveId, categoryId, name, color)) {
            assertApiResponseData(this)
            assertEquals(name, data?.name, "Name of the category should be equals to $name")
            assertEquals(color, data?.color, "Color of the category should be equals to $color")
        }
        // Delete test Category
        deleteCategory(userDrive.driveId, categoryId)
    }

    @Test
    @DisplayName("Retrieve recent activities from remote")
    fun getLastActivity() {
        with(getLastActivities(userDrive.driveId, null)) {
            assertApiResponseData(this)
            assertTrue(data!!.isNotEmpty(), "Last activities shouldn't be empty or null")
        }
    }

    @Test
    @DisplayName("Put a file in trash then get it from here")
    fun getGivenTrashFile() {
        // Create File to put it in trash
        val fileToDelete = putNewFileInTrash()
        // Get the deleted File from the trash, info should be the same
        with(getTrashedFile(fileToDelete)) {
            assertApiResponseData(this)
            assertEquals(fileToDelete.id, data?.id, "file id should be the same")
        }
    }

    @Test
    @DisplayName("Put a file in trash then get all files in trash")
    fun getAllDriveTrashFiles() {
        // Create File to put it in trash
        val fileToDelete = putNewFileInTrash()
        // Get all trash Files
        with(getDriveTrash(userDrive.driveId, File.SortType.RECENT, null)) {
            assertApiResponseData(this)
            assertTrue(data!!.isNotEmpty(), "Trash should not be empty")
            assertEquals(fileToDelete.id, data?.first()?.id, "First trash testFile's id should be ${fileToDelete.id}")
        }
    }

    @Test
    @DisplayName("Put a file in trash then restore it to root folder")
    fun restoreFileFromTrash() {
        // Create File and put it in trash
        val file = putNewFileInTrash()
        // Restore file from trash
        assertApiResponseData(postRestoreTrashFile(file, mapOf("destination_folder_id" to ROOT_ID)))
        // Get the trash files, the file restored should not be here
        with(getDriveTrash(userDrive.driveId, File.SortType.RECENT, null)) {
            assertApiResponseData(this)
            if (data!!.isNotEmpty()) {
                assertNotEquals(file.id, data?.first()?.id, "Last trash file's id should not be ${file.id}")
            }
        }
        // Put the file in trash again
        deleteTestFile(file)
    }

    @Test
    @DisplayName("Delete all trashed files, then delete one created file specifically")
    fun permanentlyDeleteFiles() {
        // Clean the trash to make sure nothing is left in
        assertApiResponseData(emptyTrash(userDrive.driveId))
        with(getDriveTrash(userDrive.driveId, File.SortType.NAME_ZA, null)) {
            assertApiResponseData(this)
            assertTrue(data!!.isEmpty(), "Trash should be empty")
        }

        // Create a new file, put it in trash then permanently delete it
        deleteTrashFile(putNewFileInTrash())

        // Trash should still be empty because new file has been deleted from trash
        with(getDriveTrash(userDrive.driveId, File.SortType.NAME_ZA, null)) {
            assertApiResponseData(this)
            assertTrue(data!!.isEmpty(), "Trash should be empty")
        }
    }

    @Test
    @DisplayName("Retrieve shared remote file")
    fun mySharedFileTest() {
        val order = File.SortType.BIGGER
        assertApiResponseData(getMySharedFiles(okHttpClient, userDrive.driveId, order, null))
    }

    @Nested
    @DisplayName("Given test file")
    inner class ShareTestFile {

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
        @DisplayName("Add a file to favorite, then remove it")
        fun manageFavoriteFileLifecycle() {
            // Creates favorite
            assertApiResponseData(postFavoriteFile(testFile))

            // File must be a favorite
            with(getFileDetails(testFile)) {
                assertApiResponseData(this)
                assertTrue(data!!.isFavorite, "File must be a favorite")
            }

            // Deletes created Favorite
            assertApiResponseData(deleteFavoriteFile(testFile))
            // File must not be a favorite
            with(getFileDetails(testFile)) {
                assertApiResponseData(this)
                assertFalse(data!!.isFavorite, "File must not be a favorite")
            }
        }

        @Test
        @DisplayName("Check if the file activities are correctly retrieved")
        fun getFileActivities() {
            with(getFileActivities(testFile, null, false)) {
                if (isSuccess()) {
                    assertApiResponseData(this)
                } else {
                    Log.e("getFileActivityTest", "api response error : ${context.getString(translatedError)}")
                }
            }
        }

        @Test
        @DisplayName("Check the creation of comment on a file")
        fun createCommentOnFile() {
            // Posts 2 comments
            val commentBody = helloWorld
            with(postFileComment(testFile, commentBody)) {
                assertApiResponseData(this)
                assertEquals(commentBody, data!!.body)
            }

            // Gets comments
            with(getFileComments(testFile, null)) {
                assertApiResponseData(this)
                assertTrue(data!!.isNotEmpty(), "Test file should have 1 comment")
                assertEquals(commentBody, data!![0].body, "Comment body should be $commentBody")
            }
        }

        @Test
        @DisplayName("Create a comment on a file then update it")
        fun updateCommentOnFile() {
            val commentId = postFileComment(testFile, helloWorld).let {
                assertApiResponseData(it)
                it.data!!.id
            }
            // Updates the comment
            with(putFileComment(testFile, commentId, "42")) {
                assertApiResponseData(this)
                assertTrue(data ?: false)
            }

            // Makes sure comment has been updated
            with(getFileComments(testFile, null)) {
                assertApiResponseData(this)
                assertEquals("42", data!![0].body, "Comment body should be 42")
            }
        }

        @Test
        @DisplayName("Create a comment on a file then delete it")
        fun deleteCommentOnFile() {
            // Adds a comment on file then deletes it
            with(postFileComment(testFile, helloWorld)) {
                assertApiResponseData(this)
                // Delete the comment
                deleteFileComment(testFile, data!!.id)
                assertTrue(getFileComments(testFile, null).data.isNullOrEmpty(), "There should not be comment on the test file")
            }
        }

        @Test
        @DisplayName("Like a file's comment then unlike it")
        fun likesCommentOnFile() {
            val commentBody = helloWorld
            val commentID = postFileComment(testFile, commentBody).let {
                assertApiResponseData(it)
                it.data!!.id
            }

            // Likes the comment
            with(postLikeComment(testFile, commentID)) {
                assertApiResponseData(this)
                assertTrue(data ?: false)
            }

            // Gets the comment
            with(getFileComments(testFile, null)) {
                assertApiResponseData(this)
                val comment = data!!.find { comment -> comment.id == commentID }
                assertNotNull(actual = comment, message = "Comment should not be null")
                assertTrue(comment.liked, "Comment should be liked")
            }

            // Unlike the comment
            with(postUnlikeComment(testFile, commentID)) {
                assertApiResponseData(this)
                assertTrue(data ?: false)
            }

            // Make sure data has been updated
            with(getFileComments(testFile, null)) {
                val comment = data?.find { commentRes -> commentRes.id == commentID }
                assertNotNull(actual = comment, message = "Comment should not be null")
                assertFalse(comment.liked, "Comment should not be liked anymore")
            }
        }

        @Test
        @DisplayName("Duplicate the test file to root folder")
        fun duplicateFile() {
            val copyFile = duplicateFile(testFile, testFile.parentId).let {
                assertApiResponseData(it)
                assertNotEquals(testFile.id, it.data?.id, "The id should be different from the original file")
                assertEquals(testFile.driveColor, it.data?.driveColor)
                it.data!!
            }

            // Duplicate one more time with location
            with(duplicateFile(testFile, testFile.parentId)) {
                assertApiResponseData(this)
                deleteTestFile(data!!)
            }

            // Delete the copy
            deleteTestFile(copyFile)
        }

        @Test
        @DisplayName("Create a custom share link, update it then delete it")
        fun shareLinkTest() {
            val body = ShareLink().ShareLinkSettings(
                right = ShareLink.ShareLinkFilePermission.PUBLIC,
                canDownload = true,
                canEdit = false,
                canSeeStats = false,
            )

            // Creates the share link
            with(createShareLink(testFile, body)) {
                assertApiResponseData(this)
                assertNotNull(actual = data?.capabilities, message = "The data's capabilities cannot be null")
                assertEquals(ShareLink.ShareLinkFilePermission.PUBLIC, data!!.right, "Permission should be public")

                with(data?.capabilities!!) {
                    assertTrue(canDownload, "Can downloads should be true")
                    assertFalse(canEdit, "Can edit should be false")
                    assertFalse(canSeeStats, "Show stats should be false")
                }

            }

            // Get the share link
            val shareLink = ApiRepository.getShareLink(testFile)
            with(shareLink) {
                assertApiResponseData(this)
                assertFalse(data?.url.isNullOrBlank(), "Sharelink url cannot be null")
            }

            // Modifies the share link
            updateShareLink(
                testFile, shareLink.data!!.ShareLinkSettings(
                    right = ShareLink.ShareLinkFilePermission.PUBLIC,
                    canDownload = false,
                    canEdit = true,
                    canSeeStats = true
                ).toJsonElement()
            ).let(::assertApiResponseData)

            // Makes sure modification has been made
            with(ApiRepository.getShareLink(testFile)) {
                assertApiResponseData(this)
                assertNotNull(actual = data?.capabilities, message = "The data's capabilities cannot be null")
                assertEquals(ShareLink.ShareLinkFilePermission.PUBLIC, data!!.right, "Permission should be public")

                with(data?.capabilities!!) {
                    assertFalse(canDownload, "can downloads should be false")
                    assertTrue(canEdit, "can edit should be true")
                    assertTrue(canSeeStats, "show stats should be true")
                }

            }

            // Delete the shareLink
            with(deleteFileShareLink(testFile)) {
                assertApiResponseData(this)
                assertTrue(data!!)
            }

            assertFalse(ApiRepository.getShareLink(testFile).isSuccess(), "Share link check should fail")
        }

        @Test
        @DisplayName("Add a category to the test file, then delete this category")
        fun addCategoryToFile() {
            val category = createCategoryForTest()

            // Add the category to the test file
            addCategory(testFile, category.id)
            with(getFileDetails(testFile)) {
                assertApiResponseData(this)
                assertNotNull(
                    actual = data!!.categories.find { it.categoryId == category.id },
                    message = "The test category should be found",
                )
            }

            // Delete the category before removing it from the test file
            deleteCategory(userDrive.driveId, category.id)
            with(getFileDetails(testFile)) {
                assertApiResponseData(this)
                assertTrue(data!!.categories.isEmpty(), "The test file should not have category")
            }
        }

        @Test
        @DisplayName("Add a category to the test file, then remove this category from the file")
        fun removeCategoryToFile() {
            val category = createCategoryForTest()

            // Add the category to the test file
            assertApiResponseData(addCategory(testFile, category.id))
            // Remove the category
            assertApiResponseData(removeCategory(testFile, category.id))
            // Make sure the category is removed
            with(getFileDetails(testFile)) {
                assertApiResponseData(this)
                assertTrue(data!!.categories.isEmpty(), "The test file should not have a category")
            }
            // Delete the test category
            deleteCategory(userDrive.driveId, category.id)
        }

        @Test
        @DisplayName("Adds a category to several files, then removes and deletes it")
        fun manageCategoryOnManyFiles() {
            val files = listOf(testFile, createFileForTest())
            val category = createCategoryForTest()

            // assert adding the category worked
            checkCategoryCallsOnFiles(files, category.id, true)

            // assert removing the category worked
            checkCategoryCallsOnFiles(files, category.id, false)

            deleteCategory(userDrive.driveId, category.id)
        }

        private fun createCategoryForTest() = with(createCategory(userDrive.driveId, "testCategory-$randomSuffix", "#FFF")) {
            assertNotNull(actual = data, message = "Category should not be null")
            data!!
        }

        private fun checkCategoryCallsOnFiles(files: List<File>, categoryId: Int, isAdding: Boolean) {
            val apiResponse = if (isAdding) addCategory(files, categoryId) else removeCategory(files, categoryId)

            with(apiResponse) {
                assertApiResponseData(this)
                assertEquals(files.size, data?.size, "The data should have the same size as the files list")

                data?.forEach { assertTrue(it.result, "Error message :${it.message}") }
            }
        }
    }

    @Nested
    @DisplayName("Given test Folder")
    inner class ShareTestFolder {

        private lateinit var testFolder: File
        private val folderName = "testFolder-$randomSuffix}"

        @BeforeEach
        @Throws(Exception::class)
        fun setUp() {
            testFolder = createFolderWithName(folderName)
        }

        @AfterEach
        @Throws(Exception::class)
        fun tearDown() {
            deleteTestFile(testFolder)
        }

        @Test
        @DisplayName("Create a file under root then move it to a created folder")
        fun moveFileToAnotherFolder() {
            val file = createFileForTest()
            // Creates test folder
            with(testFolder) {
                // Moves file in the test folder
                assertApiResponseData(moveFile(file, this))

                // Gets the count of file in the folder
                with(getFileCount(this)) {
                    assertApiResponseData(this)
                    assertEquals(1, data!!.count, "There should be 1 file in the folder")
                }

                // Makes sure the folder contains the file
                with(getFileDetails(file)) {
                    assertNotNull(actual = data)
                    assertEquals(data?.parentId, id, "The file should be contained in the test folder")
                }
            }
        }

        @Test
        @DisplayName("Duplicate the test file to another folder")
        fun duplicateFileToAnotherFolder() {
            val file = createFileForTest()

            val copyFile = duplicateFile(file, file.parentId).let {
                assertApiResponseData(it)
                assertNotEquals(file.id, it.data?.id, "The id should be different from the original file")
                assertEquals(file.driveColor, it.data?.driveColor)
                it.data!!
            }

            // Delete the copy
            deleteTestFile(copyFile)
        }

        @Test
        @DisplayName("Create a folder then convert it to dropbox")
        fun createDropboxFromFolder() {
            // No dropbox yet
            assertNull(actual = getDropBox(testFolder).data, message = "not dropbox should be returned, data should be null")

            val maxSize = 16384L

            // Add a dropBox with default body and get its id
            val dropboxId = createDropBoxForTest(testFolder, maxSize).let {
                assertTrue(it.hasNotification, "Email when finished must be true")
                assertEquals(maxSize, it.limitFileSize, "Limit file size should be $maxSize")
                it.id
            }

            // Get the dropbox
            with(getDropBox(testFolder)) {
                assertApiResponseData(this)
                assertEquals(folderName, data!!.name, "Dropbox name should be '$folderName'")
                assertEquals(dropboxId, data!!.id, "Dropbox id should be $dropboxId")
            }
        }

        @Test
        @DisplayName("Update the properties of a dropbox")
        fun updateDropBox() {
            val maxSize = 16384L
            createDropBoxForTest(testFolder, maxSize)

            // Update the dropbox info
            val updateBody = JsonObject().apply {
                addProperty("email_when_finished", false)
                addProperty("limit_file_size", maxSize * 2)
            }
            with(updateDropBox(testFolder, updateBody)) {
                assertApiResponseData(this)
                assertTrue(data ?: false)
            }

            // Make sure the dropbox has been updated
            with(getDropBox(testFolder)) {
                assertFalse(data?.hasNotification ?: true, "Email when finished should be false")
                assertEquals(maxSize * 2, data?.limitFileSize, "Limit file size should be ${maxSize * 2}")
            }
        }

        @Test
        @DisplayName("Convert a dropbox back to a normal folder")
        fun deleteDropbox() {
            // Convert the folder to dropbox
            createDropBoxForTest(testFolder, 16384L)
            // Delete the dropbox
            assertApiResponseData(deleteDropBox(testFolder))
            // Assert no dropbox left
            assertNull(actual = getDropBox(testFolder).data, message = "not dropbox should be returned, data should be null")
        }
    }

    companion object {
        const val helloWorld = "Hello World"
    }
}
