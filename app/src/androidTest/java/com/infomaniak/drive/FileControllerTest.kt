/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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

import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRepository.addMultiAccess
import com.infomaniak.drive.data.api.ApiRepository.getFolderFiles
import com.infomaniak.drive.data.api.ApiRepository.renameFile
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.cache.FileController.FAVORITES_FILE_ID
import com.infomaniak.drive.data.cache.FileController.getGalleryDrive
import com.infomaniak.drive.data.cache.FileController.getMySharedFiles
import com.infomaniak.drive.data.cache.FileController.getOfflineFiles
import com.infomaniak.drive.data.cache.FileController.removeFile
import com.infomaniak.drive.data.cache.FileController.saveFavoritesFiles
import com.infomaniak.drive.data.cache.FileController.searchFiles
import com.infomaniak.drive.data.cache.FileController.storeGalleryDrive
import com.infomaniak.drive.data.cache.FolderFilesProvider
import com.infomaniak.drive.data.cache.FolderFilesProvider.SourceRestrictionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.ApiTestUtils.assertApiResponseData
import com.infomaniak.drive.utils.ApiTestUtils.createFileForTest
import com.infomaniak.drive.utils.ApiTestUtils.deleteTestFile
import com.infomaniak.drive.utils.Env
import com.infomaniak.drive.utils.Utils
import com.infomaniak.lib.core.utils.Utils.getDefaultAcceptedLanguage
import io.realm.Realm
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * File Controllers testing class
 */
class FileControllerTest : KDriveTest() {

    private val randomSuffix = UUID.randomUUID()
    private lateinit var realm: Realm

    @BeforeEach
    @Throws(Exception::class)
    fun setUp() {
        realm = Realm.getInstance(getRealmConfigurationTest())
        if (AccountUtils.currentUser == null) AccountUtils.currentUser = user
    }

    @AfterEach
    @Throws(Exception::class)
    fun tearDown() {
        if (!realm.isClosed) realm.close()
        Realm.deleteRealm(getRealmConfigurationTest())
    }

    @Test
    @DisplayName("Create a folder at the drive's root")
    fun createTestFolder() {
        val folderName = "TestFolder-$randomSuffix"
        // Create a folder under root
        with(ApiRepository.createFolder(okHttpClient, userDrive.driveId, Utils.ROOT_ID, folderName, true)) {
            assertApiResponseData(this)
            assertEquals(folderName, data?.name, "The name should correspond")

            // Delete the test folder
            deleteTestFile(data!!)
        }
    }

    @Test
    @DisplayName("Check if remote and local files are the same")
    fun getRootFiles_CanGetRemoteSavedFilesFromRealm() = runTest {
        val remoteFolderFiles = getAndSaveRemoteRootFiles()?.folderFiles

        // We check that we get the data saved in realm
        val localRootFiles = getLocalRootFiles()
        assertNotNull(localRootFiles, "local root files cannot be null")
        assertFalse(localRootFiles.isNullOrEmpty(), "local root files cannot be empty")
        assertTrue(
            remoteFolderFiles?.size == localRootFiles?.size,
            "the size of the local and remote files must be identical",
        )
    }

    @Test
    @DisplayName("Create a file then put it to trash")
    fun deleteAddedFileFromAPI() {
        // Create a file
        val remoteFile = createAndStoreOfficeFile()

        // Delete the file
        deleteTestFile(remoteFile)

        // Search the deleted file
        with(ApiRepository.getFileDetails(remoteFile)) {
            assertTrue(!isSuccess(), "Api response must be a success")
            assertTrue(data == null, "Founded files should be empty")
        }
    }

    @Test
    @DisplayName("Make a file favorite then store it in realm and compare results")
    fun getFavoriteFiles_CanGetRemoteSavedFilesFromRealm() {
        // Create a test file and store it in favorite
        val remoteFile = createAndStoreOfficeFile()
        ApiRepository.postFavoriteFile(remoteFile)

        // Get remote favorite files
        val remoteResult = ApiRepository.getFavoriteFiles(Env.DRIVE_ID, File.SortType.NAME_AZ, null)
        assertTrue(remoteResult.isSuccess(), "get favorite files request must pass successfully")
        assertFalse(remoteResult.data.isNullOrEmpty(), "remote favorite files cannot be empty ")

        // Save remote favorite files in realm db test
        val remoteFavoriteFiles = remoteResult.data!!
        saveFavoritesFiles(remoteFavoriteFiles, realm = realm)

        // Get saved favorite files
        val localFavoriteFiles = FileController.getFilesFromCache(FAVORITES_FILE_ID)
        assertNotNull(localFavoriteFiles, "local favorite files cannot be null")
        assertFalse(localFavoriteFiles.isEmpty(), "local favorite files cannot be empty")

        // Compare remote files and local files
        assertTrue(localFavoriteFiles.firstOrNull()?.parentId == FAVORITES_FILE_ID)
        assertTrue(localFavoriteFiles.size == remoteFavoriteFiles.size, "local files and remote files cannot be different")

        // Delete Test file
        deleteTestFile(remoteFile)
    }

    @Test
    @DisplayName("Get shared files from remote then get local shared files and compare them")
    fun getMySharedFiles_CanGetRemoteSavedFilesFromRealm() = runBlocking {
        // Add a file to myShared
        val file = createFileForTest()
        val body = mutableMapOf(
            "emails" to listOf(Env.INVITE_USER_NAME),
            "right" to Shareable.ShareablePermission.READ,
            "lang" to getDefaultAcceptedLanguage(),
        )
        assertApiResponseData(addMultiAccess(file, body))

        // Get remote files
        val remoteFiles = arrayListOf<File>()
        var isCompletedRemoteFiles = false
        getMySharedFiles(userDrive, File.SortType.NAME_AZ, transaction = { files, isComplete ->
            remoteFiles.addAll(files)
            isCompletedRemoteFiles = isComplete
        })
        assertNotNull(remoteFiles, "remote my shares data cannot be null")
        assertTrue(isCompletedRemoteFiles, "remote my shares data must be complete")
        assertFalse(remoteFiles.isEmpty(), "remote files should not be empty")

        // Get local files
        val localFiles = arrayListOf<File>()
        var isCompletedLocaleFiles = false
        getMySharedFiles(userDrive, File.SortType.NAME_AZ, onlyLocal = true, transaction = { files, isComplete ->
            localFiles.addAll(files)
            isCompletedLocaleFiles = isComplete
        })
        assertNotNull(localFiles, "local my shares data cannot be null")
        assertTrue(isCompletedLocaleFiles, "local my shares data must be complete")

        // Compare remote files and local files
        assertTrue(remoteFiles.size == localFiles.size, "local files and remote files cannot be different")
        deleteTestFile(file)
    }

    @Test
    @DisplayName("Retrieve remote picture then store it in realm and compare results")
    fun getPictures_CanGetRemoteSavedFilesFromRealm() {
        // Get remote pictures
        val apiResponseData = ApiRepository.getLastGallery(Env.DRIVE_ID, null).let {
            assertApiResponseData(it)
            it.data!!
        }

        // Store remote pictures
        storeGalleryDrive(apiResponseData, customRealm = realm)

        // Get saved remote files from realm
        with(getGalleryDrive(realm)) {
            assertTrue(isNotEmpty(), "local pictures cannot be empty ")

            // Compare remote pictures with local pictures
            assertTrue(size == apiResponseData.size, "remote files and local files are different")
        }

    }

    @Test
    @DisplayName("Store a file offline then retrieve it with realm")
    fun getOfflineFiles() {
        // Create offline test file
        val file = createAndStoreOfficeFile { remoteFile ->
            remoteFile.isOffline = true
        }

        // Get offline files
        with(getOfflineFiles(null, customRealm = realm)) {
            assertTrue(isNotEmpty(), "local offline files cannot be null")
            assertNotNull(firstOrNull { it.id == file.id }, "stored file was not found in realm db")
        }

        // Delete remote offline test files
        deleteTestFile(file)
    }

    @Test
    @DisplayName("Store a file then check if search results are correct")
    fun searchFile_FromRealm_IsCorrect() {
        val file = createAndStoreOfficeFile()
        with(searchFiles(file.name, File.SortType.NAME_AZ, customRealm = realm)) {
            assertTrue(isNotEmpty(), "the list of search results must contain results")
            assertTrue(first().name.contains(file.name), "the search result must match")
        }
        deleteTestFile(file)
    }

    @Test
    @DisplayName("Check if removing realm's root remove all files")
    fun removeFileCascade_IsCorrect() = runTest {
        getAndSaveRemoteRootFiles()
        getLocalRootFiles().also { localRootFiles ->

            // Check if remote files are stored
            assertNotNull(localRootFiles, "local root files cannot be null")
            assertFalse(localRootFiles.isNullOrEmpty(), "local root files cannot be empty")
        }

        // Delete root files
        removeFile(Utils.ROOT_ID, customRealm = realm)

        // Check that all root files are deleted
        with(realm.where(File::class.java).findAll()) {
            assertTrue(isNullOrEmpty(), "Realm must not contain any files")
        }
    }

    @Test
    @DisplayName("Check if realm root contains files")
    fun getTestFileListForFolder() {
        // Get the file list of root folder
        with(getFolderFiles(okHttpClient, userDrive.driveId, Utils.ROOT_ID, order = File.SortType.NAME_AZ)) {
            assertApiResponseData(this)

            // Use non null assertion because data nullability has been checked in assertApiResponse()
            assertFalse(data.isNullOrEmpty(), "Root folder should contains files")
        }
    }

    @Test
    @DisplayName("Check if renaming file's results are correct")
    fun renameTestFile() {
        val newName = "renamed file $randomSuffix"
        val file = createFileForTest()
        assertApiResponseData(renameFile(file, newName))
        with(ApiRepository.getFileDetails(file)) {
            assertApiResponseData(this)
            assertTrue(data?.name == newName, "File's name should be $newName")
        }
        deleteTestFile(file)
    }

    private suspend fun getAndSaveRemoteRootFiles(): FolderFilesProvider.FolderFilesProviderResult? {
        // Get and save remote root files in realm db test
        return FolderFilesProvider.getFiles(
            FolderFilesProvider.FolderFilesProviderArgs(
                folderId = Utils.ROOT_ID,
                isFirstPage = true,
                realm = realm,
                sourceRestrictionType = SourceRestrictionType.ONLY_FROM_REMOTE,
                userDrive = userDrive
            )
        ).also {
            assertNotNull(it, "remote root files cannot be null")
        }
    }

    private suspend fun getLocalRootFiles() =
        FolderFilesProvider.getFiles(
            FolderFilesProvider.FolderFilesProviderArgs(
                folderId = Utils.ROOT_ID,
                isFirstPage = true,
                realm = realm,
                sourceRestrictionType = SourceRestrictionType.ONLY_FROM_LOCAL,
                userDrive = userDrive
            )
        )?.folderFiles

    private fun createAndStoreOfficeFile(transaction: ((remoteFile: File) -> Unit)? = null): File {
        val remoteFile = createFileForTest()

        // Save the file as offline file
        transaction?.invoke(remoteFile)
        realm.executeTransaction { realm.insert(remoteFile) }
        return remoteFile
    }
}
