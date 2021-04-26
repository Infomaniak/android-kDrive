package com.infomaniak.drive

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * File Controllers testing class
 * (!) Disabled for now, because FileController structure changed, these tests are now invalid
 */
@RunWith(AndroidJUnit4::class)
class FileControllerTest {
    /*
    private lateinit var realm: Realm
    val context = ApplicationProvider.getApplicationContext<Context>()

    companion object {
        const val PER_PAGE = 5
    }

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val config = RealmConfiguration.Builder().inMemory().name("KDrive-test.realm").build()
        realm = Realm.getInstance(config)
    }

    @Test
    fun saveFileTest() {
        // Generate 1 file
        val apiFile = createFile(1).data!!
        //Save the file
        saveFileByRealm(apiFile)
        // Assert that realm writes correct data
        val file = realm.where(File::class.java).equalTo("id", 0L).findFirst()
        assertThat(file?.id, equalTo(apiFile.id))
        assertThat(file?.name, equalTo(apiFile.name))
    }

    @Test
    fun saveManyPages() {
        // Save 5 page
        getFilesFromCacheOrDownloadRecursive(0, 1, 5)
        // Assert that realm writes correct data
        val file = realm.where(File::class.java).equalTo("id", 0L).findFirst()
        assert(file?.id == 0)
        assert(file?.children?.size == 25)
        assert(file?.isComplete == true)
    }

    @Test
    fun testSwitchDrive() {
        FileController.switchDriveDB(1)
        assert(realm.path.contains("${FileController.REALM_PREFIX_FILE}-1"))
    }

    @Test
    fun testDeleteUnusedDriveFiles() {
        FileController.switchDriveDB(1)
        // Generate 1 file
        val apiFile = createFile(1).data!!
        //Save the file
        saveFileByRealm(apiFile)
        // Assert
        var exists = false
        FileController.deleteUnusedDriveFiles(arrayListOf(2))
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name.contains("${FileController.REALM_PREFIX_FILE}1", true)) {
                exists = true
            }
        }
        assertFalse(exists)
    }

    @After
    @Throws(java.lang.Exception::class)
    fun tearDown() {
        realm.executeTransaction {
            realm.delete(File::class.java)
        }
        realm.close()
    }

    private fun saveFileByRealm(file: @RawValue File) {
        realm.executeTransaction {
            realm.copyToRealmOrUpdate(file)
        }
    }

    private fun createFile(count: Int, page: Int = 1): ApiResponse<File> {
        val faker = Faker()

        val list = RealmList<File>()
        for (i in 1..count) {
            list.add(
                File(
                    id = i,
                    canUseTag = false,
                    name = faker.name.name()
                )
            )
        }
        val file = File(
            id = 0,
            canUseTag = false,
            name = faker.name.name(),
            children = list
        )
        return ApiResponse(
            result = ApiResponse.Status.SUCCESS,
            data = file,
            page = page
        )
    }

    private fun getFilesFromCacheOrDownloadRecursive(parentId: Int, page: Int, pageCount: Int) {
        val resultList = getFilesFromCacheOrDownload(parentId, page)
        when {
            resultList.isNullOrEmpty() -> return
            pageCount == 0 -> return
            else -> getFilesFromCacheOrDownloadRecursive(parentId, page + 1, pageCount - 1)
        }
    }

    private fun getFilesFromCacheOrDownload(parentId: Int, page: Int): ArrayList<File>? {
        try {
            val file = realm.where(File::class.java).equalTo("id", parentId).findFirst()
            val children = file?.let { realm.copyFromRealm(it.children) as ArrayList } ?: arrayListOf()

            if (file == null || children.isNullOrEmpty() || !file.isComplete || children.size < PER_PAGE) {
                val apiResponse = createFile(5, page)
                if (apiResponse.isSuccess()) {
                    apiResponse.data?.let { newFile ->
                        val apiChildrenRealmList = newFile.children
                        val apiChildren = ArrayList<File>(apiChildrenRealmList.toList())

                        if (!apiChildren.isNullOrEmpty()) {
                            newFile.initRightIds()
                            // Restore same children data
                            children.filter { it?.children?.isNullOrEmpty() == false }.map { oldFile ->
                                newFile.children.find { it.id == oldFile.id }?.children = oldFile.children
                            }
                            // Save
                            realm.executeTransaction {
                                if (children.isNullOrEmpty() || page == 1) {
                                    if (apiChildren.size < PER_PAGE) newFile.isComplete = true
                                    it.copyToRealmOrUpdate(newFile, ImportFlag.CHECK_SAME_VALUES_BEFORE_SET)
                                } else {
                                    if (apiChildren.size < PER_PAGE) file?.isComplete = true
                                    file?.children?.addAll(apiChildren)
                                }
                            }
                            return apiChildren
                        } else {
                            file?.let { oldFile ->
                                realm.executeTransaction {
                                    oldFile.isComplete = true
                                    it.copyToRealmOrUpdate(oldFile)
                                }
                            }
                        }
                    }
                } else if (page == 1) return children
            } else if (page == 1) return children
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
     */
}