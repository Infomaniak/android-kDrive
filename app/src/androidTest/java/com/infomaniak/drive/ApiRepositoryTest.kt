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
import com.infomaniak.drive.data.api.ApiRepository.getFavoriteFiles
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
import org.junit.*
import org.junit.runner.RunWith

/**
 * Logging activity testing class
 */
@RunWith(AndroidJUnit4::class)
class ApiRepositoryTest : KDriveTest() {
    private lateinit var testFile: File

    @Before
    @Throws(Exception::class)
    fun setUp() {
        testFile = createFileForTest()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        deleteTestFile(testFile)
        emptyTrash(userDrive.driveId)
    }

    @Test
    fun getDriveData() {
        assertApiResponse(getAllDrivesData(okHttpClient))
    }

    @Test
    fun getTestUserProfile() {
        with(getUserProfile(okHttpClient)) {
            assertApiResponse(this)
            Assert.assertEquals("User ids should be the same", userDrive.userId, data?.id)
        }
    }

    @Test
    fun manageFavoriteFileLifecycle() {
        assertApiResponse(postFavoriteFile(testFile))

        val favoriteFileCount = getFavoriteFiles(userDrive.driveId, File.SortType.NAME_AZ, 1).let { favoriteFile ->
            assertApiResponse(favoriteFile)
            Assert.assertTrue("Favorite file should exists", favoriteFile.data!!.isNotEmpty())
            favoriteFile.data!!.size
        }

        assertApiResponse(deleteFavoriteFile(testFile))
        with(getFavoriteFiles(userDrive.driveId, File.SortType.NAME_AZ, 1)) {
            Assert.assertEquals(
                "The number of favorite files should be lowered by 1",
                favoriteFileCount - 1,
                data?.size
            )
        }
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
        with(getFileComments(testFile, 1)) {
            assertApiResponse(this)
            Assert.assertTrue("Test file should not have comments", data.isNullOrEmpty())
        }

        // Post 2 comments
        val commentBody = "Hello world"
        val commentID = postFileComment(testFile, commentBody).let {
            assertApiResponse(it)
            Assert.assertEquals(commentBody, it.data?.body)
            it.data!!.id
        }

        val commentID2 = postFileComment(testFile, commentBody).let {
            assertApiResponse(it)
            it.data!!.id
        }
        Assert.assertNotEquals("Comments id should be different", commentID, commentID2)

        // Likes the second comment
        with(postFileCommentLike(testFile, commentID2)) {
            assertApiResponse(this)
            Assert.assertTrue(data ?: false)
        }

        // Get new comments
        with(getFileComments(testFile, 1)) {
            assertApiResponse(this)
            Assert.assertEquals("There should be 2 comments on the test file", 2, data?.size)
            Assert.assertNotNull(data?.find { comment -> comment.id == commentID2 }?.liked?.let { it -> Assert.assertTrue(it) })
        }
        // Delete first comment
        deleteFileComment(testFile, commentID)
        Assert.assertEquals("There should be 1 comment on the test file", 1, getFileComments(testFile, 1).data?.size)

        // Put second comment
        with(putFileComment(testFile, commentID2, "42")) {
            assertApiResponse(this)
            Assert.assertTrue(data ?: false)
        }

        // Unlike the comment
        with(postFileCommentUnlike(testFile, commentID2)) {
            assertApiResponse(this)
            Assert.assertTrue(data ?: false)
        }

        // Make sure data has been updated
        with(getFileComments(testFile, 1)) {
            val comment = data?.find { commentRes -> commentRes.id == commentID2 }
            assertApiResponse(this)
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
        val copyFile = duplicateFile(testFile, copyName, ROOT_ID).let {
            assertApiResponse(it)
            Assert.assertEquals("The copy name should be equal to $copyName", copyName, it.data?.name)
            Assert.assertNotEquals("The id should be different from the original file", testFile.id, it.data?.id)
            Assert.assertEquals(testFile.driveColor, it.data?.driveColor)
            it.data!!
        }

        // Duplicate one more time with same name and location
        with(duplicateFile(testFile, copyName, ROOT_ID)) {
            assertApiResponse(this)
            Assert.assertEquals("The copy name should be equal to $copyName (1)", "$copyName (1)", data?.name)
            deleteTestFile(data!!)
        }

        deleteTestFile(copyFile)
    }

    @Test
    fun moveFileToAnotherFolder() {
        val file = createFileForTest()
        // Create test folder
        val folderName = "f"
        with(createFolder(okHttpClient, userDrive.driveId, ROOT_ID, folderName)) {
            Assert.assertNotNull(data)
            // Move file in the test folder
            assertApiResponse(moveFile(file, data!!))
            val folderFileCount = getFileCount(data!!)
            assertApiResponse(folderFileCount)
            Assert.assertEquals("There should be 1 file in the folder", 1, folderFileCount.data?.count)

            val folderData = getFileDetails(data!!).data
            Assert.assertNotNull(folderData)
            Assert.assertTrue(folderData!!.children.contains(file))
            deleteTestFile(folderData)
        }
    }

    @Test
    fun createTeamTestFolder() {
        with(createTeamFolder(okHttpClient, userDrive.driveId, "teamFolder", true)) {
            assertApiResponse(this)
            Assert.assertTrue("visibility should be 'is_team_space_folder", data!!.visibility.contains("is_team_space_folder"))
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
            Assert.assertEquals("Permission should be public", "public", data!!.permission.name.lowercase())
            Assert.assertFalse("Block downloads should be false", data!!.blockDownloads)
            Assert.assertFalse("Can edit should be false", data!!.canEdit)
            Assert.assertFalse("Show stats should be false", data!!.showStats)
            Assert.assertFalse("Block comments should be false", data!!.blockDownloads)
            Assert.assertFalse("Block information should be false", data!!.blockInformation)
        }

        with(getFileShare(okHttpClient, testFile)) {
            assertApiResponse(this)
            Assert.assertEquals("Path should be the name of the file", "/${testFile.name}", data!!.path)
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
            Assert.assertEquals("Permission should be public", "public", data!!.permission.name.lowercase())
            Assert.assertTrue("block downloads should be true", data!!.blockDownloads)
            Assert.assertTrue("can edit should be true", data!!.canEdit)
            Assert.assertTrue("show stats should be true", data!!.showStats)
            Assert.assertTrue("block comments should be true", data!!.blockDownloads)
            Assert.assertTrue("Block information should be true", data!!.blockInformation)
        }

        with(deleteFileShareLink(testFile)) {
            assertApiResponse(this)
            Assert.assertTrue(data!!)
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
        val categoryId = createCategory(userDrive.driveId, "category tests", "#0000FF").let {
            assertApiResponse(it)
            Assert.assertEquals("Name of the category should be equals to 'category tests'", "category tests", it.data?.name)
            Assert.assertEquals("Color of the category should be equals to blue", "#0000FF", it.data?.color)
            it.data!!.id
        }

        // Create again the same category should fail
        with(createCategory(userDrive.driveId, "category tests", "#0000FF")) {
            Assert.assertFalse(isSuccess())
            Assert.assertEquals(
                "Error description should be 'category already exist error'",
                "Category already exist error",
                error?.description
            )
        }

        with(editCategory(userDrive.driveId, categoryId, "update cat", "#FF0000")) {
            assertApiResponse(this)
            Assert.assertEquals("Name of the category should be equals to 'update cat'", "update cat", data?.name)
            Assert.assertEquals("Color of the category should be equals to red", "#FF0000", data?.color)
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
        with(getFileDetails(file)) {
            assertApiResponse(this)
            Assert.assertNotNull(
                "The test category should be found",
                data!!.categories.find { cat -> cat.id == category.id })
        }
        // Delete the category before removing it from the test file
        deleteCategory(userDrive.driveId, category.id)
        with(getFileDetails(file)) {
            assertApiResponse(this)
            Assert.assertTrue("The test file should not have category", data!!.categories.isNullOrEmpty())
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
        with(getFileDetails(file)) {
            assertApiResponse(this)
            Assert.assertTrue("The test file should not have a category", data!!.categories.isNullOrEmpty())
        }
        // Delete the test category and file
        deleteCategory(userDrive.driveId, category.id)
        deleteTestFile(file)
    }

    @Test
    fun getLastActivityTest() {
        with(getLastActivities(userDrive.driveId, 1)) {
            assertApiResponse(this)
            Assert.assertTrue("Last activities shouldn't be empty or null", data!!.isNotEmpty())
        }
    }

    @Ignore("Don't know the wanted api behaviour")
    @Test
    fun postTestFolderAccess() {
        val folder = createFolder(okHttpClient, userDrive.driveId, ROOT_ID, "folder").data
        Assert.assertNotNull("test folder must not be null", folder)
        val postResponse = postFolderAccess(folder!!)
        assertApiResponse(postResponse)
        deleteTestFile(folder)
    }

    @Test
    fun manageDropboxLifecycle() {
        // Create a folder to convert it in dropbox
        val name = "testFolder"
        val folder = createFolder(okHttpClient, userDrive.driveId, ROOT_ID, name, false).data
        Assert.assertNotNull(folder)
        // No dropbox yet
        Assert.assertNull("not dropbox should be returned, data should be null", getDropBox(folder!!).data)

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

        with(getDropBox(folder)) {
            assertApiResponse(this)
            Assert.assertEquals("Dropbox name should be '$name'", name, data!!.alias)
            Assert.assertEquals("Dropbox id should be $dropboxId", dropboxId, data!!.id)
        }
        val updateBody = JsonObject().apply {
            addProperty("email_when_finished", false)
            addProperty("limit_file_size", maxSize * 2)
        }

        with(updateDropBox(folder, updateBody)) {
            assertApiResponse(this)
            Assert.assertTrue(data ?: false)
        }

        with(getDropBox(folder)) {
            assertApiResponse(this)
            Assert.assertEquals("Dropbox id should be $dropboxId", dropboxId, data!!.id)
            Assert.assertEquals("Email when finished should be false", false, data!!.emailWhenFinished)
            Assert.assertEquals("Limit file size should be ${maxSize * 2}", maxSize * 2, data!!.limitFileSize)
        }

        assertApiResponse(deleteDropBox(folder))
        // No dropbox left
        Assert.assertNull("not dropbox should be returned, data should be null", getDropBox(folder).data)
        deleteTestFile(folder)
    }

    @Test
    fun manageTrashLifecycle() {
        createFileForTest().let { file ->
            val newName = "Trash test"
            renameFile(file, newName)
            val modifiedFile = getLastModifiedFiles(userDrive.driveId).data?.first()
            Assert.assertNotNull(modifiedFile)
            deleteTestFile(modifiedFile!!)
            with(getTrashFile(modifiedFile, File.SortType.RECENT, 1)) {
                assertApiResponse(this)
                Assert.assertEquals("file id should be the same", file.id, data?.id)
                Assert.assertEquals("file name should be updated to '$newName'", newName, data?.name)
            }

            with(getDriveTrash(userDrive.driveId, File.SortType.RECENT, 1)) {
                assertApiResponse(this)
                Assert.assertTrue("Trash should not be empty", data!!.isNotEmpty())
                Assert.assertEquals("Last trash file's id should be ${file.id}", file.id, data?.first()?.id)
            }

            // Restore the file from the trash
            assertApiResponse(postRestoreTrashFile(modifiedFile, mapOf("destination_folder_id" to ROOT_ID)))
            with(getDriveTrash(userDrive.driveId, File.SortType.RECENT, 1)) {
                assertApiResponse(this)
                if (data!!.isNotEmpty()) {
                    Assert.assertNotEquals("Last trash file's id should not be ${file.id}", file.id, data?.first()?.id)
                }
            }
            deleteTestFile(modifiedFile)
        }

        // Clean the trash to make sure nothing is left in
        assertApiResponse(emptyTrash(userDrive.driveId))
        with(getDriveTrash(userDrive.driveId, File.SortType.NAME_ZA, 1)) {
            assertApiResponse(this)
            Assert.assertTrue("Trash should be empty", data!!.isEmpty())
        }

        // Create a new file, put it in trash then permanently delete it
        with(createFileForTest()) {
            deleteTestFile(this)
            deleteTrashFile(this)
        }

        // Trash should still be empty
        with(getDriveTrash(userDrive.driveId, File.SortType.NAME_ZA, 1)) {
            assertApiResponse(this)
            Assert.assertTrue("Trash should be empty", data!!.isEmpty())
        }
    }

    @Test
    fun mySharedFileTest() {
        val order = File.SortType.BIGGER
        assertApiResponse(getMySharedFiles(okHttpClient, userDrive.driveId, order.order, order.orderBy, 1))
    }
}