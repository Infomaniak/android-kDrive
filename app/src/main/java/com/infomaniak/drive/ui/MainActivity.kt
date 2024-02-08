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
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.navigation.NavigationBarItemView
import com.google.android.material.snackbar.Snackbar
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.GeniusScanUtils.scanResultProcessing
import com.infomaniak.drive.GeniusScanUtils.startScanFlow
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.MatomoDrive.trackInAppUpdate
import com.infomaniak.drive.MatomoDrive.trackScreen
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.services.DownloadReceiver
import com.infomaniak.drive.databinding.ActivityMainBinding
import com.infomaniak.drive.ui.addFiles.UploadFilesHelper
import com.infomaniak.drive.ui.fileList.FileListFragmentArgs
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.NavigationUiUtils.setupWithNavControllerCustom
import com.infomaniak.drive.utils.SyncUtils.launchAllUpload
import com.infomaniak.drive.utils.SyncUtils.startContentObserverService
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.drive.utils.Utils.getRootName
import com.infomaniak.lib.applock.LockActivity
import com.infomaniak.lib.applock.Utils.isKeyguardSecure
import com.infomaniak.lib.core.networking.LiveDataNetworkStatus
import com.infomaniak.lib.core.utils.CoilUtils.simpleImageLoader
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SnackbarUtils.showIndefiniteSnackbar
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.UtilsUi.generateInitialsAvatarDrawable
import com.infomaniak.lib.core.utils.UtilsUi.getBackgroundColorBasedOnId
import com.infomaniak.lib.core.utils.whenResultIsOk
import com.infomaniak.lib.stores.StoreUtils.checkUpdateIsAvailable
import com.infomaniak.lib.stores.StoreUtils.initAppUpdateManager
import com.infomaniak.lib.stores.StoreUtils.installDownloadedUpdate
import com.infomaniak.lib.stores.StoreUtils.launchInAppReview
import com.infomaniak.lib.stores.StoreUtils.unregisterAppUpdateListener
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class MainActivity : BaseActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val mainViewModel: MainViewModel by viewModels()
    private val navigationArgs: MainActivityArgs? by lazy { intent?.extras?.let { MainActivityArgs.fromBundle(it) } }
    private val uiSettings by lazy { UiSettings(this) }
    private val navController by lazy { setupNavController() }

    private lateinit var downloadReceiver: DownloadReceiver

    private var lastAppClosingTime = Date().time
    private var uploadedFilesToDelete = arrayListOf<UploadFile>()
    private var hasDisplayedInformationPanel: Boolean = false

    private lateinit var drivePermissions: DrivePermissions

    private val filesDeletionResult = registerForActivityResult(StartIntentSenderForResult()) {
        it.whenResultIsOk { lifecycleScope.launch(Dispatchers.IO) { UploadFile.deleteAll(uploadedFilesToDelete) } }
    }

    private val fileObserver: FileObserver by lazy {
        fun onEvent() {
            mainViewModel.syncOfflineFiles()
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

    private var uploadFilesHelper: UploadFilesHelper? = null

    private val scanFlowResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            activityResult.whenResultIsOk {
                it?.let { data -> scanResultProcessing(data, folder = null) }
            }
        }

    private val inAppUpdateResultLauncher = registerForActivityResult(StartIntentSenderForResult()) { result ->
        val isUserWantingUpdates = result.resultCode == RESULT_OK
        uiSettings.isUserWantingUpdates = isUserWantingUpdates
        trackInAppUpdate(if (isUserWantingUpdates) "discoverNow" else "discoverLater")
    }

    private var inAppUpdateSnackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        downloadReceiver = DownloadReceiver(mainViewModel)
        fileObserver.startWatching()

        setupBottomNavigation()
        handleNavigateToDestinationFileId()
        listenToNetworkStatus()

        navController.addOnDestinationChangedListener { _, dest, args -> onDestinationChanged(dest, args) }

        setupMainFab()
        setupDrivePermissions()
        handleInAppReview()
        handleShortcuts()

        LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver, IntentFilter(DownloadReceiver.TAG))

        initAppUpdateManager()
        observeAppUpdateDownload()
    }

    override fun onStart() {
        super.onStart()
        handleUpdates()
    }

    private fun getNavHostFragment() = supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment

    private fun setupNavController(): NavController {
        return getNavHostFragment().navController.apply {
            if (currentDestination == null) navigate(graph.startDestinationId)
        }
    }

    private fun setupBottomNavigation() = with(binding) {
        bottomNavigation.apply {
            setupWithNavControllerCustom(navController)
            itemIconTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.item_icon_tint_bottom)
            selectedItemId = uiSettings.bottomNavigationSelectedItem
            setOnItemReselectedListener { item ->
                when (item.itemId) {
                    R.id.rootFilesFragment, R.id.favoritesFragment -> {
                        navController.popBackStack(R.id.homeFragment, false)
                        navController.navigate(item.itemId)
                    }
                    else -> navController.popBackStack(item.itemId, false)
                }
            }
        }
    }

    private fun handleNavigateToDestinationFileId() {
        navigationArgs?.let {
            if (it.destinationFileId > 0) {
                clickOnBottomBarFolders()
                mainViewModel.navigateFileListTo(navController, it.destinationFileId)
            }
        }
    }

    private fun listenToNetworkStatus() {
        LiveDataNetworkStatus(this).observe(this) { isAvailable ->
            SentryLog.d("Internet availability", if (isAvailable) "Available" else "Unavailable")
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

    private fun setupMainFab() = with(binding) {
        mainFab.setOnClickListener { navController.navigate(R.id.addFileBottomSheetDialog) }
        mainViewModel.currentFolder.observe(this@MainActivity) { file ->
            mainFab.isEnabled = file?.rights?.canCreateFile == true
        }
    }

    private fun setupDrivePermissions() {
        drivePermissions = DrivePermissions().apply {
            registerPermissions(this@MainActivity)
        }
    }

    private fun handleInAppReview() = with(AppSettings) {
        if (appLaunches == 20 || (appLaunches != 0 && appLaunches % 100 == 0)) launchInAppReview()
    }


    //region In-App Updates
    private fun initAppUpdateManager() {
        initAppUpdateManager(
            context = this,
            onUpdateDownloaded = { mainViewModel.toggleAppUpdateStatus(isUpdateDownloaded = true) },
            onUpdateInstalled = { mainViewModel.toggleAppUpdateStatus(isUpdateDownloaded = false) },
        )
    }

    private fun handleUpdates() {
        if (uiSettings.isUserWantingUpdates || AppSettings.appLaunches % 10 == 0) {
            checkUpdateIsAvailable(
                appId = BuildConfig.APPLICATION_ID,
                versionCode = BuildConfig.VERSION_CODE,
                inAppResultLauncher = inAppUpdateResultLauncher,
                onFDroidResult = { updateIsAvailable ->
                    if (updateIsAvailable) navController.navigate(R.id.updateAvailableBottomSheetDialog)
                },
            )
        }
    }

    private fun launchUpdateInstall() {
        trackInAppUpdate("installUpdate")
        mainViewModel.canInstallUpdate.value = false
        uiSettings.hasAppUpdateDownloaded = false
        installDownloadedUpdate(
            onFailure = {
                Sentry.captureException(it)
                uiSettings.resetUpdateSettings()
                showSnackbar(title = R.string.errorUpdateInstall, anchor = getMainFab())
            },
        )
    }

    private fun observeAppUpdateDownload() {
        mainViewModel.canInstallUpdate.observe(this) { isUploadDownloaded ->
            if (isUploadDownloaded && canDisplayInAppSnackbar()) {
                inAppUpdateSnackbar = showIndefiniteSnackbar(
                    title = R.string.updateReadyTitle,
                    actionButtonTitle = R.string.updateInstallButton,
                    anchor = getMainFab(),
                    onActionClicked = ::launchUpdateInstall,
                )
            } else if (!isUploadDownloaded) {
                inAppUpdateSnackbar?.dismiss()
            }
        }
    }

    private fun canDisplayInAppSnackbar() = inAppUpdateSnackbar?.isShown != true && getMainFab().isShown
    //endregion

    override fun onResume() {
        super.onResume()

        if (isKeyguardSecure() && AppSettings.appSecurityLock) {
            LockActivity.lockAfterTimeout(
                lastAppClosingTime = lastAppClosingTime,
                context = this,
                destinationClass = this::class.java
            )
        }

        launchAllUpload(drivePermissions)

        if (!mainViewModel.ignoreSyncOffline) launchSyncOffline() else mainViewModel.ignoreSyncOffline = false

        AppSettings.appLaunches++

        displayInformationPanel()

        setBottomNavigationUserAvatar(this)
        startContentObserverService()

        handleDeletionOfUploadedPhotos()

        mainViewModel.checkAppUpdateStatus()
    }

    override fun onPause() {
        lastAppClosingTime = Date().time
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
        destination.addSentryBreadcrumb()

        val shouldHideBottomNavigation = navigationArgs?.let(FileListFragmentArgs::fromBundle)?.shouldHideBottomNavigation

        handleBottomNavigationVisibility(destination.id, shouldHideBottomNavigation)

        // TODO: Find a better way to do this. Currently, we need to put that
        //  here and not in the preview slider fragment because of APIs <= 27.
        if (destination.id != R.id.previewSliderFragment && destination.id != R.id.fileDetailsFragment) {
            binding.bottomNavigation.setOnApplyWindowInsetsListener(null)
        }

        when (destination.id) {
            R.id.favoritesFragment,
            R.id.homeFragment,
            R.id.menuFragment,
            R.id.mySharesFragment -> {
                // Defining default root folder
                mainViewModel.currentFolder.value = AccountUtils.getCurrentDrive()?.convertToFile(getRootName(this))
            }
        }

        when (destination.id) {
            R.id.fileDetailsFragment -> {
                setColorNavigationBar(true)
            }
            R.id.fileShareLinkSettingsFragment -> {
                setColorStatusBar(true)
                setColorNavigationBar(true)
            }
            R.id.downloadProgressDialog, R.id.previewSliderFragment, R.id.selectPermissionBottomSheetDialog -> Unit
            else -> {
                setColorStatusBar()
                setColorNavigationBar()
            }
        }

        destination.trackDestination()
    }

    @SuppressLint("RestrictedApi")
    private fun NavDestination.addSentryBreadcrumb() {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "Navigation"
            message = "Accessed to destination : $displayName"
            level = SentryLevel.INFO
        })
    }

    @SuppressLint("RestrictedApi")
    private fun NavDestination.trackDestination() {
        trackScreen(displayName.substringAfter("${BuildConfig.APPLICATION_ID}:id"), label.toString())
    }

    private fun handleBottomNavigationVisibility(destinationId: Int, shouldHideBottomNavigation: Boolean?) = with(binding) {

        val isVisible = when (destinationId) {
            R.id.addFileBottomSheetDialog,
            R.id.favoritesFragment,
            R.id.fileInfoActionsBottomSheetDialog,
            R.id.fileListFragment,
            R.id.rootFilesFragment,
            R.id.homeFragment,
            R.id.menuFragment,
            R.id.menuGalleryFragment,
            R.id.mySharesFragment,
            R.id.sharedWithMeFragment -> shouldHideBottomNavigation != true
            else -> false
        }

        mainFab.isVisible = isVisible
        bottomNavigation.isVisible = isVisible
        bottomNavigationBackgroundView.isVisible = isVisible
    }

    private fun handleShortcuts() = with(mainViewModel) {
        navigationArgs?.shortcutId?.let { shortcutId ->
            trackEvent("shortcuts", shortcutId)

            when (shortcutId) {
                Shortcuts.SEARCH.id -> {
                    ShortcutManagerCompat.reportShortcutUsed(this@MainActivity, Shortcuts.SEARCH.id)
                    navController.navigate(R.id.searchFragment)
                }
                Shortcuts.UPLOAD.id -> {
                    uploadFilesHelper = UploadFilesHelper(this@MainActivity, navController)
                    currentFolder.observe(this@MainActivity) { parentFolder ->
                        if (mustOpenShortcut && parentFolder?.id == ROOT_ID) {
                            mustOpenShortcut = false
                            uploadFilesHelper?.apply {
                                initParentFolder(parentFolder)
                                uploadFiles()
                            }
                        }
                    }
                }
                Shortcuts.SCAN.id -> startScanFlow(scanFlowResultLauncher)
                Shortcuts.FEEDBACK.id -> openSupport()
            }
        }
    }

    private fun launchSyncOffline() {
        if (drivePermissions.checkWriteStoragePermission(false)) mainViewModel.syncOfflineFiles()
    }

    override fun onStop() {
        unregisterAppUpdateListener()
        super.onStop()
        saveLastNavigationItemSelected()
    }

    fun saveLastNavigationItemSelected() {
        uiSettings.bottomNavigationSelectedItem = binding.bottomNavigation.selectedItemId
    }

    override fun onDestroy() {
        super.onDestroy()
        fileObserver.stopWatching()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver)
    }

    private fun displayInformationPanel() = with(uiSettings) {
        if (!hasDisplayedInformationPanel) {
            val destinationId = when {
                !hasDisplayedSyncDialog && !AccountUtils.isEnableAppSync() -> {
                    hasDisplayedSyncDialog = true
                    R.id.syncConfigureBottomSheetDialog
                }
                else -> null
            }
            destinationId?.let {
                hasDisplayedInformationPanel = true
                findNavController(R.id.hostFragment).navigate(it)
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
                val bottomNavigationMenuView = binding.bottomNavigation.getChildAt(0) as BottomNavigationMenuView
                val menuItemView = bottomNavigationMenuView[4] as NavigationBarItemView
                val request = ImageRequest.Builder(context)
                    .data(avatar)
                    .crossfade(true)
                    .transformations(CircleCropTransformation())
                    .fallback(fallback)
                    .error(fallback)
                    .placeholder(R.drawable.ic_account)
                    .build()
                val userAvatar = this@MainActivity.simpleImageLoader.execute(request).drawable

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
                        binding.bottomNavigation.menu.findItem(R.id.menuFragment).icon = stateListDrawable
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

    fun getMainFab() = binding.mainFab

    fun clickOnBottomBarFolders() {
        binding.bottomNavigation.findViewById<View>(R.id.rootFilesFragment).performClick()
    }

    companion object {
        private const val SYNCED_FILES_DELETION_FILES_AMOUNT = 10
    }
}
