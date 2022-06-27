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
package com.infomaniak.drive.ui

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import coil.ImageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.navigation.NavigationBarItemView
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.R
import com.infomaniak.drive.checkUpdateIsAvailable
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.services.DownloadReceiver
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.launchInAppReview
import com.infomaniak.drive.ui.fileList.FileListFragmentArgs
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MatomoUtils.trackScreen
import com.infomaniak.drive.utils.NavigationUiUtils.setupWithNavControllerCustom
import com.infomaniak.drive.utils.SyncUtils.launchAllUpload
import com.infomaniak.drive.utils.SyncUtils.startContentObserverService
import com.infomaniak.drive.utils.Utils.getRootName
import com.infomaniak.lib.core.utils.LiveDataNetworkStatus
import com.infomaniak.lib.core.utils.UtilsUi.generateInitialsAvatarDrawable
import com.infomaniak.lib.core.utils.UtilsUi.getBackgroundColorBasedOnId
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class MainActivity : BaseActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var downloadReceiver: DownloadReceiver

    private var lastCloseApp = Date()
    private var updateAvailableShow = false
    private var uploadedFilesToDelete = arrayListOf<UploadFile>()
    private var hasDisplayedInformationPanel: Boolean = false

    private lateinit var drivePermissions: DrivePermissions

    private val filesDeletionResult = registerForActivityResult(StartIntentSenderForResult()) {
        it.whenResultIsOk { lifecycleScope.launch(Dispatchers.IO) { UploadFile.deleteAll(uploadedFilesToDelete) } }
    }

    private val fileObserver: FileObserver by lazy {
        fun onEvent() {
            CoroutineScope(Dispatchers.IO).launch {
                mainViewModel.syncOfflineFiles()
            }
        }

        val offlineFolder = File.getOfflineFolder(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(offlineFolder) {
                override fun onEvent(event: Int, path: String?) = onEvent()
            }
        } else {
            object : FileObserver(offlineFolder.path) {
                override fun onEvent(event: Int, path: String?) = onEvent()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadReceiver = DownloadReceiver(mainViewModel)
        fileObserver.startWatching()
        val navController = setupNavController()

        setupBottomNavigation(navController)
        handleShowProgressIntent(navController)
        listenToNetworkStatus()

        navController.addOnDestinationChangedListener { _, dest, args -> onDestinationChanged(dest, args) }

        setupMainFab(navController)
        setupDrivePermissions()
        handleInAppReview()
        handleUpdates(navController)

        LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver, IntentFilter(DownloadReceiver.TAG))
    }

    private fun setupNavController(): NavController {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment
        return navHostFragment.navController.apply {
            if (currentDestination == null) navigate(graph.startDestinationId)
        }
    }

    private fun setupBottomNavigation(navController: NavController) {
        bottomNavigation.apply {
            setupWithNavControllerCustom(navController)
            itemIconTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.item_icon_tint_bottom)
            selectedItemId = UiSettings(this@MainActivity).bottomNavigationSelectedItem
            setOnItemReselectedListener { item ->
                when (item.itemId) {
                    R.id.fileListFragment, R.id.favoritesFragment -> {
                        navController.popBackStack(R.id.homeFragment, false)
                        navController.navigate(item.itemId)
                    }
                    else -> navController.popBackStack(item.itemId, false)
                }
            }
        }
    }

    private fun handleShowProgressIntent(navController: NavController) {
        intent?.getIntExtra(INTENT_SHOW_PROGRESS, 0)?.let { folderId ->
            if (folderId > 0) {
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = UploadWorker.BREADCRUMB_TAG
                    message = "Upload notification has been clicked"
                    level = SentryLevel.INFO
                })

                bottomNavigation.findViewById<View>(R.id.fileListFragment).performClick()
                mainViewModel.navigateFileListToFolderId(navController, folderId)
            }
        }
    }

    private fun listenToNetworkStatus() {
        LiveDataNetworkStatus(this).observe(this) { isAvailable ->
            Log.d("Internet availability", if (isAvailable) "Available" else "Unavailable")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "Network"
                message = "Internet access is available : $isAvailable"
                level = if (isAvailable) SentryLevel.INFO else SentryLevel.WARNING
            })
            mainViewModel.isInternetAvailable.value = isAvailable
            if (isAvailable) {
                lifecycleScope.launch {
                    AccountUtils.updateCurrentUserAndDrives(this@MainActivity)
                    mainViewModel.restartUploadWorkerIfNeeded()
                }
            }
        }
    }

    private fun setupMainFab(navController: NavController) {
        mainFab.setOnClickListener { navController.navigate(R.id.addFileBottomSheetDialog) }
        mainViewModel.currentFolder.observe(this) { file ->
            mainFab.isEnabled = file?.rights?.canCreateFile == true
        }
    }

    private fun setupDrivePermissions() {
        drivePermissions = DrivePermissions().apply {
            registerPermissions(this@MainActivity)
            checkWriteStoragePermission()
        }
    }

    private fun handleInAppReview() {
        with(AppSettings) {
            if (appLaunches == 20 || (appLaunches != 0 && appLaunches % 100 == 0)) {
                launchInAppReview()
            }
        }
    }

    private fun handleUpdates(navController: NavController) {
        if (!UiSettings(this).updateLater || AppSettings.appLaunches % 10 == 0) {
            checkUpdateIsAvailable { updateIsAvailable ->
                if (!updateAvailableShow && updateIsAvailable) {
                    navController.navigate(R.id.updateAvailableBottomSheetDialog)
                    updateAvailableShow = true
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (isKeyguardSecure() && AppSettings.appSecurityLock) {
            val lastCloseAppWithTolerance = Date(lastCloseApp.time + SECURITY_APP_TOLERANCE)
            val now = Date()
            if (now.after(lastCloseAppWithTolerance)) startActivity(Intent(this, LockActivity::class.java))
        }

        launchAllUpload(drivePermissions)

        if (!mainViewModel.ignoreSyncOffline) launchSyncOffline() else mainViewModel.ignoreSyncOffline = false

        AppSettings.appLaunches++

        displayInformationPanel()

        setBottomNavigationUserAvatar(this)
        startContentObserverService()

        handleDeletionOfUploadedPhotos()
    }

    override fun onPause() {
        lastCloseApp = Date()
        super.onPause()
    }

    private fun handleDeletionOfUploadedPhotos() {
        if (UploadFile.getAppSyncSettings()?.deleteAfterSync == true && UploadFile.getCurrentUserPendingUploadsCount() == 0) {
            UploadFile.getAllUploadedFiles()?.let { filesUploadedRecently ->
                if (filesUploadedRecently.size >= SYNCED_FILES_DELETION_FILES_AMOUNT) {
                    Utils.createConfirmation(
                        context = this,
                        title = getString(R.string.modalDeletePhotosTitle),
                        message = getString(R.string.modalDeletePhotosNumericDescription, filesUploadedRecently.size),
                        buttonText = getString(R.string.buttonDelete),
                        isDeletion = true,
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val filesDeletionRequest = MediaStore.createDeleteRequest(
                                contentResolver,
                                filesUploadedRecently
                                    .filter {
                                        !it.getUriObject().scheme.equals(ContentResolver.SCHEME_FILE) &&
                                                !DocumentsContract.isDocumentUri(this, it.getUriObject())
                                    }
                                    .map { it.getUriObject() }
                            )
                            uploadedFilesToDelete = filesUploadedRecently
                            filesDeletionResult.launch(IntentSenderRequest.Builder(filesDeletionRequest.intentSender).build())
                        } else {
                            mainViewModel.deleteSynchronizedFilesOnDevice(filesUploadedRecently)
                        }
                    }
                }
            }
        }
    }

    private fun onDestinationChanged(destination: NavDestination, navigationArgs: Bundle?) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "Navigation"
            message = "Accessed to destination : ${destination.displayName}"
            level = SentryLevel.INFO
        })

        with(destination) {
            application.trackScreen(displayName.substringAfter("${BuildConfig.APPLICATION_ID}:id"), label.toString())
        }

        val shouldHideBottomNavigation = navigationArgs?.let(FileListFragmentArgs::fromBundle)?.shouldHideBottomNavigation

        handleBottomNavigationVisibility(destination.id, shouldHideBottomNavigation)

        // TODO: Find a better way to do this. Currently, we need to put that
        // here and not in the preview slider fragment because of APIs <= 27.
        if (destination.id != R.id.previewSliderFragment && destination.id != R.id.fileDetailsFragment) {
            bottomNavigation.setOnApplyWindowInsetsListener(null)
        }

        when (destination.id) {
            R.id.favoritesFragment,
            R.id.homeFragment,
            R.id.menuFragment -> {
                // Defining default root folder
                mainViewModel.currentFolder.value = AccountUtils.getCurrentDrive()?.convertToFile(getRootName(this))
            }
        }

        when (destination.id) {
            R.id.fileDetailsFragment, R.id.fileShareLinkSettingsFragment -> {
                setColorStatusBar(destination.id == R.id.fileShareLinkSettingsFragment)
                setColorNavigationBar(true)
            }
            R.id.downloadProgressDialog, R.id.previewSliderFragment -> Unit
            else -> {
                setColorStatusBar()
                setColorNavigationBar()
            }
        }
    }

    private fun handleBottomNavigationVisibility(destinationId: Int, shouldHideBottomNavigation: Boolean?) {

        val isVisible = when (destinationId) {
            R.id.addFileBottomSheetDialog,
            R.id.favoritesFragment,
            R.id.fileInfoActionsBottomSheetDialog,
            R.id.fileListFragment,
            R.id.homeFragment,
            R.id.menuFragment,
            R.id.sharedWithMeFragment -> shouldHideBottomNavigation != true
            else -> false
        }

        mainFab.isVisible = isVisible
        bottomNavigation.isVisible = isVisible
        bottomNavigationBackgroundView.isVisible = isVisible
    }

    private fun launchSyncOffline() {
        lifecycleScope.launch {
            if (drivePermissions.checkWriteStoragePermission(false)) mainViewModel.syncOfflineFiles()
        }
    }

    override fun onStop() {
        super.onStop()
        saveLastNavigationItemSelected()
    }

    fun saveLastNavigationItemSelected() {
        UiSettings(this).bottomNavigationSelectedItem = bottomNavigation.selectedItemId
    }

    override fun onDestroy() {
        super.onDestroy()
        fileObserver.stopWatching()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver)
    }

    private fun displayInformationPanel() {
        if (!hasDisplayedInformationPanel) {
            UiSettings(this).apply {
                val destinationId = when {
                    !hasDisplayedSyncDialog && !AccountUtils.isEnableAppSync() -> {
                        hasDisplayedSyncDialog = true
                        if (AppSettings.migrated) R.id.syncAfterMigrationBottomSheetDialog else R.id.syncConfigureBottomSheetDialog
                    }
                    else -> null
                }
                destinationId?.let {
                    hasDisplayedInformationPanel = true
                    findNavController(R.id.hostFragment).navigate(it)
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun setBottomNavigationUserAvatar(context: Context) {
        AccountUtils.currentUser?.apply {
            lifecycleScope.launch(Dispatchers.IO) {
                val fallback =
                    context.generateInitialsAvatarDrawable(
                        initials = getInitials(),
                        background = context.getBackgroundColorBasedOnId(id)
                    )
                val menuItemView = (bottomNavigation.getChildAt(0) as BottomNavigationMenuView)[4] as NavigationBarItemView
                val imageLoader = ImageLoader.Builder(this@MainActivity).build()
                val request = ImageRequest.Builder(context)
                    .data(avatar)
                    .crossfade(true)
                    .transformations(CircleCropTransformation())
                    .fallback(fallback)
                    .error(fallback)
                    .placeholder(R.drawable.ic_account)
                    .build()
                val userAvatar = imageLoader.execute(request).drawable

                userAvatar?.let {
                    val selectedAvatar = generateSelectedAvatar(userAvatar)
                    val stateListDrawable = StateListDrawable()
                    stateListDrawable.addState(
                        intArrayOf(android.R.attr.state_checked),
                        BitmapDrawable(resources, selectedAvatar)
                    )
                    stateListDrawable.addState(intArrayOf(), userAvatar)

                    withContext(Dispatchers.Main) {
                        menuItemView.setIconTintList(null)
                        menuItemView.setIcon(stateListDrawable)
                        bottomNavigation.menu.findItem(R.id.menuFragment).icon = stateListDrawable
                    }
                }
            }
        }
    }

    private fun generateSelectedAvatar(userAvatar: Drawable): Bitmap {
        val bitmap = userAvatar.toBitmap(100, 100)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = ContextCompat.getColor(this@MainActivity, R.color.primary)
            strokeWidth = 8F
            style = Paint.Style.STROKE
            isAntiAlias = true
            isDither = true
        }

        canvas.drawCircle(50F, 50F, 46F, paint)
        return bitmap
    }

    companion object {
        private const val SYNCED_FILES_DELETION_FILES_AMOUNT = 10
        private const val SECURITY_APP_TOLERANCE = 1 * 60 * 1000 // 1min (ms)
        const val INTENT_SHOW_PROGRESS = "intent_folder_id_progress"
    }
}
