package com.infomaniak.drive

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRepository.getFileListForFolder
import com.infomaniak.drive.data.api.ApiRepository.getLastModifiedFiles
import com.infomaniak.drive.data.api.ApiRepository.renameFile
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.cache.FileController.FAVORITES_FILE_ID
import com.infomaniak.drive.data.cache.FileController.getFilesFromCacheOrDownload
import com.infomaniak.drive.data.cache.FileController.getMySharedFiles
import com.infomaniak.drive.data.cache.FileController.removeFile
import com.infomaniak.drive.data.cache.FileController.saveFavoritesFiles
import com.infomaniak.drive.data.cache.FileController.searchFiles
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.ApiTestUtils.assertApiResponse
import com.infomaniak.drive.utils.ApiTestUtils.createFileForTest
import com.infomaniak.drive.utils.ApiTestUtils.deleteTestFile
import com.infomaniak.drive.utils.Env
import com.infomaniak.drive.utils.Utils
import io.realm.Realm
import kotlinx.android.parcel.RawValue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * File Controllers testing class
 */
@RunWith(AndroidJUnit4::class)
class FileControllerTest : KDriveTest() {

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
    fun createTestFolder() {
        val folderName = "TestFolder"
        // Create a folder under root
        with(ApiRepository.createFolder(okHttpClient, userDrive.driveId, Utils.ROOT_ID, folderName, true)) {
            assertApiResponse(this)
            Assert.assertEquals("The name should correspond", folderName, data?.name)
            // Delete the test folder
            deleteTestFile(data!!)
        }
    }

    @Test
    fun getRootFiles_CanGetRemoteSavedFilesFromRealm() {
        val remoteResult = getAndSaveRemoteRootFiles()

        // We check that we get the data saved in realm
        val localResult = getLocalRootFiles()
        Assert.assertNotNull("local root files cannot be null", localResult)
        Assert.assertFalse("local root files cannot be empty", localResult?.second.isNullOrEmpty())
        Assert.assertTrue(
            "the size of the local and remote files must be identical",
            remoteResult?.second?.size == localResult?.second?.size
        )
    }

    @Test
    fun deleteAddedFileFromAPI() {
        // Create a file
        val remoteFile = createAndStoreOfficeFile()
        val order = File.SortType.NAME_AZ

        // Delete the file
        deleteTestFile(remoteFile)

        // Search the deleted file
        with(ApiRepository.searchFiles(userDrive.driveId, remoteFile.name, order.order, order.orderBy, 1)) {
            Assert.assertTrue("Api response must be a success", isSuccess())
            Assert.assertTrue("Founded files should be empty", data.isNullOrEmpty())
        }
    }

    @Test
    fun getFavoriteFiles_CanGetRemoteSavedFilesFromRealm() {
        // Create a test file and store it in favorite
        val remoteFile = createAndStoreOfficeFile()
        ApiRepository.postFavoriteFile(remoteFile)
        // Get remote favorite files
        val remoteResult = ApiRepository.getFavoriteFiles(Env.DRIVE_ID, File.SortType.NAME_AZ, 1)
        Assert.assertTrue("get favorite files request must pass successfully", remoteResult.isSuccess())
        Assert.assertFalse("remote favorite files cannot be empty ", remoteResult.data.isNullOrEmpty())

        // Save remote favorite files in realm db test
        val remoteFavoriteFiles = remoteResult.data!!
        saveFavoritesFiles(remoteFavoriteFiles, realm = realm)

        // Get saved favorite files and compare with remote files
        val localFavoriteFiles =
            getFilesFromCacheOrDownload(
                parentId = FAVORITES_FILE_ID,
                page = 1,
                ignoreCache = false,
                userDrive = userDrive,
                customRealm = realm,
                ignoreCloud = true
            )
        val parent = localFavoriteFiles?.first
        val files = localFavoriteFiles?.second
        Assert.assertNotNull("local favorite files cannot be null", localFavoriteFiles)
        Assert.assertFalse("local favorite files cannot be empty", files.isNullOrEmpty())

        // Compare remote files and local files
        Assert.assertTrue(parent?.id == FAVORITES_FILE_ID)
        Assert.assertTrue("local files and remote files cannot be different", files?.size == remoteFavoriteFiles.size)
        // Delete Test file
        deleteTestFile(remoteFile)
    }

    @Test
    fun getMySharedFiles_CanGetRemoteSavedFilesFromRealm() = runBlocking {
        // Get remote files
        val remoteFiles = arrayListOf<File>()
        var isCompletedRemoteFiles = false
        getMySharedFiles(userDrive, File.SortType.NAME_AZ) { files, isComplete ->
            remoteFiles.addAll(files)
            isCompletedRemoteFiles = isComplete
        }

        Assert.assertNotNull("remote my shares data cannot be null", remoteFiles)
        Assert.assertTrue("remote my shares data must be complete", isCompletedRemoteFiles)

        // Get local files
        val localFiles = arrayListOf<File>()
        var isCompletedLocaleFiles = false
        getMySharedFiles(userDrive, File.SortType.NAME_AZ, 1, true) { files, isComplete ->
            localFiles.addAll(files)
            isCompletedLocaleFiles = isComplete
        }

        Assert.assertNotNull("local my shares data cannot be null", localFiles)
        Assert.assertTrue("local my shares data must be complete", isCompletedLocaleFiles)

        // Compare remote files and local files
        Assert.assertTrue("local files and remote files cannot be different", remoteFiles.size == localFiles.size)
    }

    @Test
    fun getPictures_CanGetRemoteSavedFilesFromRealm() {
        // Get remote pictures
        val apiResponseData = ApiRepository.getLastPictures(Env.DRIVE_ID, 1).let {
            Assert.assertTrue("get pictures request must pass", it.isSuccess())
            Assert.assertFalse("get pictures response data cannot be null or empty", it.data.isNullOrEmpty())
            it.data!!
        }

        // Store remote pictures
        FileController.storePicturesDrive(apiResponseData, customRealm = realm)

        // Get saved remote files from realm
        with(FileController.getPicturesDrive(realm)) {
            Assert.assertTrue("local pictures cannot be empty ", isNotEmpty())

            // Compare remote pictures with local pictures
            Assert.assertTrue("remote files and local files are different", size == apiResponseData.size)
        }
    }

    @Test
    fun getOfflineFiles() {
        // Create offline test file
        val file = createAndStoreOfficeFile { remoteFile ->
            remoteFile.isOffline = true
        }

        // Get offline files
        with(FileController.getOfflineFiles(null, customRealm = realm)) {
            Assert.assertTrue("local offline files cannot be null", isNotEmpty())
            Assert.assertNotNull("stored file was not found in realm db", firstOrNull { it.id == file.id })
        }

        // Delete remote offline test files
        deleteTestFile(file)

    }

    @Test
    fun searchFile_FromRealm_IsCorrect() {
        val file = createAndStoreOfficeFile()
        with(searchFiles(file.name, File.SortType.NAME_AZ, customRealm = realm)) {
            Assert.assertTrue("the list of search results must contain results", isNotEmpty())
            Assert.assertTrue("the search result must match", first().name.contains(file.name))
        }
        deleteTestFile(file)
    }

    @Test
    fun removeFileCascade_IsCorrect() {
        getAndSaveRemoteRootFiles()
        with(getLocalRootFiles()) {
            // Check if remote files are stored
            Assert.assertNotNull("local root files cannot be null", this)
            Assert.assertFalse("local root files cannot be empty", this?.second.isNullOrEmpty())
        }

        // Delete root files
        removeFile(Utils.ROOT_ID, customRealm = realm)
        // Check that all root files are deleted
        with(realm.where(File::class.java).findAll()) {
            Assert.assertTrue("Realm must not contain any files", isNullOrEmpty())
        }
    }

    @Test
    fun getTestFileListForFolder() {
        // Get the file list of root folder
        with(getFileListForFolder(okHttpClient, userDrive.driveId, Utils.ROOT_ID, order = File.SortType.NAME_AZ)) {
            assertApiResponse(this)
            // Use non null assertion because data nullability has been checked in assertApiResponse()
            Assert.assertTrue("Root folder should contains files", data!!.children.isNotEmpty())
        }
    }

    @Test
    fun renameTestFile() {
        val newName = "renamed file"
        val file = createFileForTest()
        assertApiResponse(renameFile(file, newName))
        with(getLastModifiedFiles(userDrive.driveId)) {
            assertApiResponse(this)
            Assert.assertEquals("Last modified file should have id ${file.id}", file.id, data!!.first().id)
            Assert.assertEquals("File should be named '$newName'", newName, data!!.first().name)
        }
        deleteTestFile(file)

    }

    private fun getAndSaveRemoteRootFiles(): Pair<File, ArrayList<File>>? {
        // Get and save remote root files in realm db test
        return getFilesFromCacheOrDownload(Utils.ROOT_ID, 1, true, userDrive = userDrive, customRealm = realm).also {
            Assert.assertNotNull("remote root files cannot be null", it)
        }
    }

    private fun getLocalRootFiles() =
        getFilesFromCacheOrDownload(Utils.ROOT_ID, 1, false, userDrive = userDrive, customRealm = realm)

    private fun createAndStoreOfficeFile(transaction: ((remoteFile: File) -> Unit)? = null): @RawValue File {
        val remoteFile = createFileForTest()
        // Save the file as offline file
        transaction?.invoke(remoteFile)
        realm.executeTransaction { realm.insert(remoteFile) }
        return remoteFile
    }
}
