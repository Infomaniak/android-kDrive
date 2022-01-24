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

import com.google.gson.JsonObject
import com.infomaniak.drive.data.api.ApiRepository.addCategory
import com.infomaniak.drive.data.api.ApiRepository.createCategory
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
import com.infomaniak.drive.data.api.ApiRepository.getMySharedFiles
import com.infomaniak.drive.data.api.ApiRepository.getTrashFile
import com.infomaniak.drive.data.api.ApiRepository.getUserProfile
import com.infomaniak.drive.data.api.ApiRepository.moveFile
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
import com.infomaniak.drive.utils.ApiTestUtils.createDropBoxForTest
import com.infomaniak.drive.utils.ApiTestUtils.createFileForTest
import com.infomaniak.drive.utils.ApiTestUtils.createFolderWithName
import com.infomaniak.drive.utils.ApiTestUtils.deleteTestFile
import com.infomaniak.drive.utils.ApiTestUtils.getCategory
import com.infomaniak.drive.utils.ApiTestUtils.getShareLink
import com.infomaniak.drive.utils.ApiTestUtils.putNewFileInTrash
import com.infomaniak.drive.utils.Utils.ROOT_ID
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
        // Creates favorite
        assertApiResponse(postFavoriteFile(testFile))

        // File must be a favorite
        with(getFileDetails(testFile)) {
            assertApiResponse(this)
            assertTrue(data!!.isFavorite, "File must be a favorite")
        }

        // Deletes created Favorite
        assertApiResponse(deleteFavoriteFile(testFile))
        // File must not be a favorite
        with(getFileDetails(testFile)) {
            assertApiResponse(this)
            assertFalse(data!!.isFavorite, "File must not be a favorite")
        }
    }

    @Test
    fun getFileActivities() {
        renameFile(testFile, "new name")
        assertApiResponse(getFileActivities(testFile, 1))
    }

    @Test
    fun createCommentOnFile() {
        // Posts 2 comments
        val commentBody = "Hello world"
        with(postFileComment(testFile, commentBody)) {
            assertApiResponse(this)
            assertEquals(commentBody, data!!.body)
        }

        // Gets comments
        with(getFileComments(testFile, 1)) {
            assertApiResponse(this)
            assertTrue(data!!.isNotEmpty(), "Test file should have 1 comment")
            assertEquals(commentBody, data!![0].body, "Comment body should be $commentBody")
        }
    }

    @Test
    fun updateCommentOnFile() {
        val commentId = postFileComment(testFile, "Hello world").let {
            assertApiResponse(it)
            it.data!!.id
        }
        // Updates the comment
        with(putFileComment(testFile, commentId, "42")) {
            assertApiResponse(this)
            assertTrue(data ?: false)
        }

        // Makes sure comment has been updated
        with(getFileComments(testFile, 1)) {
            assertApiResponse(this)
            assertEquals("42", data!![0].body, "Comment body should be 42")
        }
    }

    @Test
    fun deleteCommentOnFile() {
        // Adds a comment on file then deletes it
        with(postFileComment(testFile, "Hello world")) {
            assertApiResponse(this)
            // Delete the comment
            deleteFileComment(testFile, data!!.id)
            assertTrue(getFileComments(testFile, 1).data.isNullOrEmpty(), "There should not be comment on the test file")
        }
    }

    @Test
    fun likesCommentOnFile() {
        val commentBody = "Hello world"
        val commentID = postFileComment(testFile, commentBody).let {
            assertApiResponse(it)
            it.data!!.id
        }

        // Likes the comment
        with(postFileCommentLike(testFile, commentID)) {
            assertApiResponse(this)
            assertTrue(data ?: false)
        }

        // Gets the comment
        with(getFileComments(testFile, 1)) {
            assertApiResponse(this)
            val comment = data!!.find { comment -> comment.id == commentID }
            assertNotNull(comment, "Comment should not be null")
            assertTrue(comment?.liked ?: false, "Comment should be liked")
        }

        // Unlike the comment
        with(postFileCommentUnlike(testFile, commentID)) {
            assertApiResponse(this)
            assertTrue(data ?: false)
        }

        // Make sure data has been updated
        with(getFileComments(testFile, 1)) {
            val comment = data?.find { commentRes -> commentRes.id == commentID }
            assertNotNull(comment, "Comment should not be null")
            assertFalse(comment?.liked ?: true, "Comment should not be liked anymore")
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

        // Delete the copy
        deleteTestFile(copyFile)
    }

    @Test
    fun moveFileToAnotherFolder() {
        val file = createFileForTest()
        // Creates test folder
        val folderName = "folder"
        with(createFolderWithName(folderName)) {
            // Moves file in the test folder
            assertApiResponse(moveFile(file, this))

            // Gets the count of file in the folder
            with(getFileCount(this)) {
                assertApiResponse(this)
                assertEquals(1, data!!.count, "There should be 1 file in the folder")
            }

            // Makes sure the folder contains the file
            with(getFileDetails(this)) {
                assertNotNull(data)
                assertTrue(data!!.children.contains(file), "The file should be contained in the test folder")
            }
            // Deletes the test folder
            deleteTestFile(this)
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

        // Creates the share link
        with(postFileShareLink(testFile, body)) {
            assertApiResponse(this)
            assertEquals("public", data!!.permission.name.lowercase(), "Permission should be public")
            assertFalse(data!!.blockDownloads, "Block downloads should be false")
            assertFalse(data!!.canEdit, "Can edit should be false")
            assertFalse(data!!.showStats, "Show stats should be false")
            assertFalse(data!!.blockDownloads, "Block comments should be false")
            assertFalse(data!!.blockInformation, "Block information should be false")
        }

        // Get the share link
        with(getFileShare(okHttpClient, testFile)) {
            assertApiResponse(this)
            assertEquals("/${testFile.name}", data!!.path, "Path should be the name of the file")
        }

        // Modifies the share link
        with(
            putFileShareLink(
                testFile, mapOf(
                    "permission" to "public",
                    "block_downloads" to true,
                    "can_edit" to true,
                    "show_stats" to true,
                    "block_comments" to true,
                    "block_information" to true
                )
            )
        ) { assertApiResponse(this) }

        // Makes sure modification has been made
        with(getShareLink(testFile)) {
            assertApiResponse(this)
            assertEquals("public", data!!.permission.name.lowercase(), "Permission should be public")
            assertTrue(data!!.blockDownloads, "block downloads should be true")
            assertTrue(data!!.canEdit, "can edit should be true")
            assertTrue(data!!.showStats, "show stats should be true")
            assertTrue(data!!.blockDownloads, "block comments should be true")
            assertTrue(data!!.blockInformation, "Block information should be true")
        }

        // Delete the shareLink
        with(deleteFileShareLink(testFile)) {
            assertApiResponse(this)
            assertTrue(data!!)
        }

        assertFalse(postFileShareCheck(testFile, body).isSuccess(), "Share link check should fail")
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
    fun createCategory() {
        val color = "#0000FF"
        val name = "category tests"
        val categoryId = createCategory(userDrive.driveId, name, color).let {
            assertApiResponse(it)
            assertEquals(name, it.data?.name, "Name of the category should be equals to $name")
            assertEquals(color, it.data?.color, "Color of the category should be equals to $color")
            it.data!!.id
        }

        // Create again the same category should fail
        with(createCategory(userDrive.driveId, name, color)) {
            assertFalse(isSuccess())
            assertEquals(
                "Category already exist error",
                error?.description,
                "Error description should be 'category already exist error'"
            )
        }

        // Delete the category
        assertApiResponse(deleteCategory(userDrive.driveId, categoryId))
        assertNull(
            getCategory(userDrive.driveId).data?.find { cat -> cat.id == categoryId },
            "The category shouldn't be found anymore",
        )
    }

    @Test
    fun updateCategory() {
        var color = "#0000FF"
        var name = "category tests"
        val categoryId = createCategory(userDrive.driveId, name, color).let {
            assertNotNull(it.data)
            it.data!!.id
        }

        name = "update category"
        color = "#FF0000"
        // Edit the category by changing its color and name
        with(editCategory(userDrive.driveId, categoryId, name, color)) {
            assertApiResponse(this)
            assertEquals(name, data?.name, "Name of the category should be equals to $name")
            assertEquals(color, data?.color, "Color of the category should be equals to $color")
        }
        // Delete test Category
        deleteCategory(userDrive.driveId, categoryId)
    }

    @Test
    fun addCategoryToFile() {
        // Create a test category
        val category = createCategory(userDrive.driveId, "test category", "#FFF").data
        assertNotNull(category)

        // Add the category to the test file
        addCategory(testFile, category!!.id)
        with(getFileDetails(testFile)) {
            assertApiResponse(this)
            assertNotNull(data!!.categories.find { it.id == category.id }, "The test category should be found")
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
        assertNotNull(category, "Category should not be null")
        // Add the category to the test file
        addCategory(testFile, category!!.id)
        // Remove the category
        removeCategory(testFile, category.id)
        // Make sure the category is removed
        with(getFileDetails(testFile)) {
            assertApiResponse(this)
            assertTrue(data!!.categories.isNullOrEmpty(), "The test file should not have a category")
        }
        // Delete the test category
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
        val folder = createFolderWithName("folder")
        assertNotNull(folder, "test folder must not be null")
        val postResponse = postFolderAccess(folder)
        assertApiResponse(postResponse)
        deleteTestFile(folder)
    }

    @Test
    fun createDropboxFromFolder() {
        // Create a folder to convert it in dropbox
        val name = "testFolder"
        val folder = createFolderWithName(name)
        // No dropbox yet
        assertNull(getDropBox(folder).data, "not dropbox should be returned, data should be null")

        val maxSize = 16384L

        // Add a dropBox with default body and get its id
        val dropboxId = createDropBoxForTest(folder, maxSize).let {
            assertTrue(it.emailWhenFinished, "Email when finished must be true")
            assertEquals(maxSize, it.limitFileSize, "Limit file size should be $maxSize")
            it.id
        }

        // Get the dropbox
        with(getDropBox(folder)) {
            assertApiResponse(this)
            assertEquals(name, data!!.alias, "Dropbox name should be '$name'")
            assertEquals(dropboxId, data!!.id, "Dropbox id should be $dropboxId")
        }
        // Delete the folder
        deleteTestFile(folder)
    }

    @Test
    fun updateDropBox() {
        val folder = createFolderWithName("testFolder")

        val maxSize = 16384L
        createDropBoxForTest(folder, maxSize)

        // Update the dropbox info
        val updateBody = JsonObject().apply {
            addProperty("email_when_finished", false)
            addProperty("limit_file_size", maxSize * 2)
        }
        with(updateDropBox(folder, updateBody)) {
            assertApiResponse(this)
            assertTrue(data ?: false)
        }

        // Make sure the dropbox has been updated
        with(getDropBox(folder)) {
            assertFalse(data?.emailWhenFinished ?: true, "Email when finished should be false")
            assertEquals(maxSize * 2, data?.limitFileSize, "Limit file size should be ${maxSize * 2}")
        }

        //Delete the folder
        deleteTestFile(folder)
    }

    @Test
    fun deleteDropbox() {
        val folder = createFolderWithName("folder")
        // Convert the folder to dropbox
        createDropBoxForTest(folder, 16384L)
        // Delete the dropbox
        assertApiResponse(deleteDropBox(folder))
        // Assert no dropbox left
        assertNull(getDropBox(folder).data, "not dropbox should be returned, data should be null")
        deleteTestFile(folder)
    }

    @Test
    fun getTrashFiles() {
        // Create File to put it in trash
        val fileToDelete = putNewFileInTrash()
        // Get the deleted File from the trash, info should be the same
        with(getTrashFile(fileToDelete, File.SortType.RECENT, 1)) {
            assertApiResponse(this)
            assertEquals(fileToDelete.id, data?.id, "file id should be the same")
        }
    }

    @Test
    fun getAllDriveTrashFiles() {
        // Create File to put it in trash
        val fileToDelete = putNewFileInTrash()
        // Get all trash Files
        with(getDriveTrash(userDrive.driveId, File.SortType.RECENT, 1)) {
            assertApiResponse(this)
            assertTrue(data!!.isNotEmpty(), "Trash should not be empty")
            assertEquals(fileToDelete.id, data?.first()?.id, "First trash testFile's id should be ${fileToDelete.id}")
        }
    }

    @Test
    fun restoreFileFromTrash() {
        // Create File and put it in trash
        val file = putNewFileInTrash()
        // Restore file from trash
        assertApiResponse(postRestoreTrashFile(file, mapOf("destination_folder_id" to ROOT_ID)))
        // Get the trash files, the file restored should not be here
        with(getDriveTrash(userDrive.driveId, File.SortType.RECENT, 1)) {
            assertApiResponse(this)
            if (data!!.isNotEmpty()) {
                assertNotEquals(file.id, data?.first()?.id, "Last trash file's id should not be ${file.id}")
            }
        }
        // Put the file in trash again
        deleteTestFile(file)
    }


    @Test
    fun permanentlyDeleteFiles() {
        // Clean the trash to make sure nothing is left in
        assertApiResponse(emptyTrash(userDrive.driveId))
        with(getDriveTrash(userDrive.driveId, File.SortType.NAME_ZA, 1)) {
            assertApiResponse(this)
            assertTrue(data!!.isEmpty(), "Trash should be empty")
        }

        // Create a new file, put it in trash then permanently delete it
        deleteTrashFile(putNewFileInTrash())

        // Trash should still be empty because new file has been deleted from trash
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
