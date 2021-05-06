package com.infomaniak.drive

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.CreateFile
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.`Env-Exemple`.DRIVE_ID
import io.realm.Realm
import kotlinx.android.parcel.RawValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

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
    fun getFiles_CanGetRemoteSavedFilesFromRealm() {
        // Get and save remote root files in realm db test
        val remoteResult =
            FileController.getFilesFromCacheOrDownload(Utils.ROOT_ID, 1, true, userDrive = userDrive, customRealm = realm)
        Assert.assertNotNull(remoteResult)

        // We check that we get the data saved in realm
        val localResult =
            FileController.getFilesFromCacheOrDownload(Utils.ROOT_ID, 1, false, userDrive = userDrive, customRealm = realm)
        Assert.assertNotNull(localResult)
        Assert.assertFalse(localResult?.second.isNullOrEmpty())
        Assert.assertTrue(remoteResult?.second?.size == localResult?.second?.size)
    }

    @Test
    fun getFavoriteFiles_CanGetRemoteSavedFilesFromRealm() {
        // Get remote favorite files
        val remoteResult = ApiRepository.getFavoriteFiles(DRIVE_ID, File.SortType.NAME_AZ, 1)
        Assert.assertTrue(remoteResult.isSuccess())
        Assert.assertFalse(remoteResult.data.isNullOrEmpty())

        // Save remote favorite files in realm db test
        val remoteFavoriteFiles = remoteResult.data!!
        FileController.saveFavoritesFiles(remoteFavoriteFiles)

        // Get saved favorite files and compare with remote files
        val localFavoriteFiles =
            FileController.getFilesFromCacheOrDownload(
                parentId = FileController.FAVORITES_FILE_ID,
                page = 1,
                ignoreCache = false,
                userDrive = userDrive,
                customRealm = realm,
                ignoreCloud = true
            )
        val parent = localFavoriteFiles?.first
        val files = localFavoriteFiles?.second
        Assert.assertNotNull(localFavoriteFiles)
        Assert.assertFalse(files.isNullOrEmpty())

        // Compare remote files and local files
        Assert.assertTrue(parent?.id == FileController.FAVORITES_FILE_ID)
        Assert.assertTrue("local files and remote files cannot be different", files?.size == remoteFavoriteFiles.size)
    }

    @Test
    fun getMySharedFiles_CanGetRemoteSavedFilesFromRealm() {
        // Get remote files
        val remoteFiles = arrayListOf<File>()
        var isCompletedRemoteFiles = false
        runBlocking(Dispatchers.IO) {
            FileController.getMySharedFiles(userDrive, File.SortType.NAME_AZ) { files, isComplete ->
                remoteFiles.addAll(files)
                isCompletedRemoteFiles = isComplete
            }
        }
        Assert.assertTrue(remoteFiles.isNotEmpty())
        Assert.assertTrue(isCompletedRemoteFiles)

        // Get local files
        val localFiles = arrayListOf<File>()
        var isCompletedLocaleFiles = false
        runBlocking(Dispatchers.IO) {
            FileController.getMySharedFiles(userDrive, File.SortType.NAME_AZ, 1, true) { files, isComplete ->
                localFiles.addAll(files)
                isCompletedLocaleFiles = isComplete
            }
        }
        Assert.assertTrue(localFiles.isNotEmpty())
        Assert.assertTrue(isCompletedLocaleFiles)

        // Compare remote files and local files
        Assert.assertTrue("local files and remote files cannot be different", remoteFiles.size == localFiles.size)
    }

    @Test
    fun getPictures_CanGetRemoteSavedFilesFromRealm() {
        // Get remote pictures
        val apiResponse = ApiRepository.getLastPictures(DRIVE_ID, 1)
        Assert.assertTrue("get pictures request must pass", apiResponse.isSuccess())
        Assert.assertFalse("get pictures response data cannot be null or empty", apiResponse.data.isNullOrEmpty())

        // Store remote pictures
        FileController.storeDriveSoloPictures(apiResponse.data!!, realm)

        // Get saved remote files from realm
        val localFiles = FileController.getDriveSoloPictures(realm)
        Assert.assertTrue("local pictures cannot be empty ", localFiles.isNotEmpty())

        // Compare remote pictures with local pictures
        Assert.assertTrue("remote files and local files are different", localFiles.size == apiResponse.data?.size)
    }

    @Test
    fun getOfflineFiles() {
        // Create offline test file
        val remoteFile = createAndStoreOfficeFile { remoteFile ->
            remoteFile.isOffline = true
        }

        // Get offline files
        val offlineFiles = FileController.getOfflineFiles(null, customRealm = realm)
        Assert.assertTrue("local offline files cannot be null", offlineFiles.isNotEmpty())

        val localFile = offlineFiles.firstOrNull { it.id == remoteFile.id }
        Assert.assertNotNull("stored file was not found in realm db", localFile)

        // Delete remote offline test files
        deleteTestFile(remoteFile)
    }

    @Test
    fun searchFile_FromRealm_IsCorrect() {
        val remoteFile = createAndStoreOfficeFile()
        val searchResult = FileController.searchFiles(remoteFile.name, File.SortType.NAME_AZ, customRealm = realm)
        Assert.assertTrue("the list of search results must contain results", searchResult.isNotEmpty())
        Assert.assertTrue("the search result must match", searchResult.first().name.contains(remoteFile.name))
        deleteTestFile(remoteFile)
    }

    private fun createAndStoreOfficeFile(transaction: ((remoteFile: File) -> Unit)? = null): @RawValue File {
        val createFile = CreateFile("offline doc ${UUID.randomUUID()}", File.Office.DOCS.extension)
        val apiResponse = ApiRepository.createOfficeFile(DRIVE_ID, Utils.ROOT_ID, createFile)
        Assert.assertTrue("create office file request must pass", apiResponse.isSuccess())
        Assert.assertNotNull("create office api response data cannot be null", apiResponse.data)

        // Save and set file as offline file
        val remoteFile = apiResponse.data!!
        transaction?.invoke(remoteFile)
        realm.executeTransaction { realm.insert(remoteFile) }
        return remoteFile
    }

    private fun deleteTestFile(remoteFile: File) {
        val deleteResponse = ApiRepository.deleteFile(remoteFile)
        Assert.assertTrue("created file couldn't be deleted from the remote", deleteResponse.isSuccess())
    }
}