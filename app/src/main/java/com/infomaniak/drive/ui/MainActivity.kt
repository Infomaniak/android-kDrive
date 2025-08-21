/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import android.app.Dialog
import android.content.ContentResolver
import android.content.Context
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.FileObserver
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.activity.viewModels
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.get
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationBarItemView
import com.google.android.material.snackbar.Snackbar
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.GeniusScanUtils.scanResultProcessing
import com.infomaniak.drive.GeniusScanUtils.startScanFlow
import com.infomaniak.drive.MatomoDrive.MatomoCategory
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackAccountEvent
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.MatomoDrive.trackInAppReview
import com.infomaniak.drive.MatomoDrive.trackInAppUpdate
import com.infomaniak.drive.MatomoDrive.trackMyKSuiteEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController.TRASH_FILE_ID
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.DeepLinkType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.VisibilityType
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.BaseDownloadWorker.Companion.HAS_SPACE_LEFT_AFTER_DOWNLOAD_KEY
import com.infomaniak.drive.data.services.DownloadReceiver
import com.infomaniak.drive.databinding.ActivityMainBinding
import com.infomaniak.drive.extensions.addSentryBreadcrumb
import com.infomaniak.drive.extensions.onApplyWindowInsetsListener
import com.infomaniak.drive.extensions.trackDestination
import com.infomaniak.drive.ui.addFiles.AddFileBottomSheetDialogArgs
import com.infomaniak.drive.ui.bottomSheetDialogs.FileInfoActionsBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.FileListFragmentArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DownloadOfflineFileManager
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.NavigationUiUtils.setupWithNavControllerCustom
import com.infomaniak.drive.utils.SyncUtils.launchAllUpload
import com.infomaniak.drive.utils.SyncUtils.startContentObserverService
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.drive.utils.openSupport
import com.infomaniak.drive.utils.setColorNavigationBar
import com.infomaniak.drive.utils.setColorStatusBar
import com.infomaniak.drive.utils.showQuotasExceededSnackbar
import com.infomaniak.lib.applock.LockActivity
import com.infomaniak.lib.core.utils.CoilUtils.simpleImageLoader
import com.infomaniak.lib.core.utils.SnackbarUtils.showIndefiniteSnackbar
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.UtilsUi.generateInitialsAvatarDrawable
import com.infomaniak.lib.core.utils.UtilsUi.getBackgroundColorBasedOnId
import com.infomaniak.lib.core.utils.setMargins
import com.infomaniak.lib.core.utils.whenResultIsOk
import com.infomaniak.lib.stores.StoreUtils.checkUpdateIsRequired
import com.infomaniak.lib.stores.StoreUtils.launchInAppReview
import com.infomaniak.lib.stores.reviewmanagers.InAppReviewManager
import com.infomaniak.lib.stores.updatemanagers.InAppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val mainViewModel: MainViewModel by viewModels()
    private val myKSuiteViewModel: MyKSuiteViewModel by viewModels()
    private val navigationArgs: MainActivityArgs? by lazy { intent?.extras?.let { MainActivityArgs.fromBundle(it) } }
    private val uiSettings by lazy { UiSettings(this) }
    private val navController by lazy { setupNavController() }

    private lateinit var downloadReceiver: DownloadReceiver

    private var hasDisplayedInformationPanel: Boolean = false

    private lateinit var syncPermissions: DrivePermissions

    private var deleteLocalMediaRequestDialog: Dialog? = null
    private val pendingFilesUrisQueue = ArrayDeque<List<Uri>>()

    private val filesDeletionResult = registerForActivityResult(StartIntentSenderForResult()) {
        it.whenResultIsOk {
            val filesUris = pendingFilesUrisQueue.removeFirstOrNull() ?: return@whenResultIsOk
            lifecycleScope.launch(Dispatchers.IO) { UploadFile.deleteAllFromUris(filesUris) }
            if (pendingFilesUrisQueue.isNotEmpty()) launchNextDeleteRequest()
        }
    }

    private val fileObserver: FileObserver by lazy {
        fun onEvent() {
            mainViewModel.syncOfflineFiles()
        }

        val offlineFolder = File.getOfflineFolder(this)

        if (SDK_INT >= 29) {
            object : FileObserver(offlineFolder) {
                override fun onEvent(event: Int, path: String?) = onEvent()
            }
        } else {
            object : FileObserver(offlineFolder.path) {
                override fun onEvent(event: Int, path: String?) = onEvent()
            }
        }
    }

    private val scanFlowResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            activityResult.whenResultIsOk {
                it?.let { data -> scanResultProcessing(data, folder = null) }
            }
        }

    private val inAppUpdateManager by lazy { InAppUpdateManager(this, BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE) }
    private var inAppUpdateSnackbar: Snackbar? = null

    private val inAppReviewManager by lazy {
        InAppReviewManager(
            activity = this,
            reviewDialogTheme = R.style.DialogStyle,
            reviewDialogTitleResId = R.string.reviewAlertTitle,
            feedbackUrlResId = R.string.urlUserReportAndroid,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        mainViewModel.initUploadFilesHelper(fragmentActivity = this, navController)

        checkUpdateIsRequired(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, R.style.AppTheme)

        downloadReceiver = DownloadReceiver(mainViewModel)
        fileObserver.startWatching()

        setupBottomNavigation()

        navController.addOnDestinationChangedListener { _, dest, args -> onDestinationChanged(dest, args) }

        setupFabs()
        setupDrivePermissions()
        handleInAppReview()
        handleShortcuts()
        handleNavigateToDestinationFileId()

        LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver, IntentFilter(DownloadReceiver.TAG))

        initAppUpdateManager()
        initAppReviewManager()
        observeCurrentFolder()
        observeBulkDownloadRunning()
        observeFailureDownloadWorkerOffline()

        LockActivity.scheduleLockIfNeeded(
            targetActivity = this,
            isAppLockEnabled = { AppSettings.appSecurityLock }
        )

        binding.bottomNavigation.onApplyWindowInsetsListener { view, windowInsets ->
            view.setMargins(
                bottom = resources.getDimension(R.dimen.bottomNavigationMargin).toInt() + windowInsets.bottom,
            )
            binding.searchFab.setMargins(bottom = resources.getDimension(R.dimen.marginStandard).toInt() + windowInsets.bottom)
        }
        if (SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.loadRootFiles()
        myKSuiteViewModel.refreshMyKSuite()
        handleDeletionOfUploadedPhotos()
    }

    private fun getNavHostFragment() = supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment

    private fun setupNavController(): NavController {
        return getNavHostFragment().navController.apply {
            if (currentDestination == null) navigate(graph.startDestinationId)
        }
    }

    // We use this SuppressLint because we don't want to execute performClick on profileItem when double tapping.
    @SuppressLint("ClickableViewAccessibility")
    private fun setupBottomNavigation() = with(binding) {

        val gestureDetector = GestureDetector(this@MainActivity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                trackAccountEvent(MatomoName.SwitchDoubleTap)
                mainViewModel.switchToNextUser { navController.navigate(R.id.homeFragment) }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                trackAccountEvent(MatomoName.LongPressDirectAccess)
                navController.navigate(R.id.switchUserActivity)
            }
        })

        bottomNavigation.findViewById<View>(R.id.menuFragment).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }

        bottomNavigation.apply {
            setupWithNavControllerCustom(navController)
            itemIconTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.item_icon_tint_bottom)
            selectedItemId = uiSettings.bottomNavigationSelectedItem
            setOnItemReselectedListener { item ->
                navController.popBackStack(item.itemId, false)
            }
        }
    }

    private fun handleNavigateToDestinationFileId() {
        navigationArgs?.let {
            if (it.deepLinkFileNotFound) {
                binding.mainFab.apply {
                    post { showSnackbar(title = R.string.noRightsToOfficeLink, anchor = this) }
                }
            } else {
                if (it.destinationFileId > 0) {
                    navigateToDestinationFileId(it.destinationFileId, it.destinationUserDrive, subfolderId = null)
                } else {
                    when (val deepLinkType = it.deepLinkType) {
                        is DeepLinkType.SharedWithMe -> null//TODO()
                        is DeepLinkType.Trash -> {
                            navigateToDestinationFileId(
                                destinationFileId = TRASH_FILE_ID,
                                destinationUserDrive = UserDrive(driveId = deepLinkType.userDriveId),
                                deepLinkType.folderId?.toInt()
                            )
                        }
                        null -> null//TODO()
                    }
                }
            }
        }
    }

    private fun navigateToDestinationFileId(destinationFileId: Int, destinationUserDrive: UserDrive?, subfolderId: Int?) {
        clickOnBottomBarFolders()
        mainViewModel.navigateFileListTo(
            navController,
            destinationFileId,
            destinationUserDrive ?: UserDrive(),
            subfolderId
        )
    }

    private fun setupFabs() = with(binding) {
        setupFab(mainFab)
        setupFab(searchFab, shouldShowSmallFab = true)

        mainViewModel.currentFolder.observe(this@MainActivity) { file ->
            val canCreateFile = file?.rights?.canCreateFile == true
            mainFab.isEnabled = canCreateFile
            searchFab.isEnabled = canCreateFile
        }
    }

    private fun setupFab(fab: FloatingActionButton, shouldShowSmallFab: Boolean = false) {
        val args = AddFileBottomSheetDialogArgs(shouldShowSmallFab).toBundle()
        fab.setOnClickListener {
            val drive = AccountUtils.getCurrentDrive() ?: return@setOnClickListener
            if (drive.isDriveFull) {
                trackMyKSuiteEvent(MatomoName.TryAddingFileWithDriveFull.value)
                showQuotasExceededSnackbar(navController, drive)
            } else {
                navController.navigate(R.id.addFileBottomSheetDialog, args)
            }
        }
    }

    private fun setupDrivePermissions() {
        syncPermissions = DrivePermissions(DrivePermissions.Type.ReadingMediaForSync).apply {
            registerPermissions(this@MainActivity)
        }
    }

    private fun handleInAppReview() = with(AppSettings) {
        if (appLaunches == 20 || (appLaunches != 0 && appLaunches % 100 == 0)) launchInAppReview()
    }

    //region In-App Updates
    private fun initAppUpdateManager() {
        inAppUpdateManager.init(
            onUserChoice = { isWantingUpdate -> trackInAppUpdate(if (isWantingUpdate) MatomoName.DiscoverNow else MatomoName.DiscoverLater) },
            onInstallStart = { trackInAppUpdate(MatomoName.InstallUpdate) },
            onInstallFailure = { showSnackbar(title = R.string.errorUpdateInstall, anchor = getMainFab()) },
            onInAppUpdateUiChange = { isUpdateDownloaded ->
                if (isUpdateDownloaded && canDisplayInAppSnackbar()) {
                    inAppUpdateSnackbar = showIndefiniteSnackbar(
                        title = R.string.updateReadyTitle,
                        actionButtonTitle = R.string.updateInstallButton,
                        anchor = getMainFab(),
                        onActionClicked = inAppUpdateManager::installDownloadedUpdate,
                    )
                } else if (!isUpdateDownloaded) {
                    inAppUpdateSnackbar?.dismiss()
                }
            },
            onFDroidResult = { updateIsAvailable ->
                if (updateIsAvailable) navController.navigate(R.id.updateAvailableBottomSheetDialog)
            },
        )
    }

    private fun observeBulkDownloadRunning() {
        // We need to check if the bulk download is running to avoid any
        // conflicts between the two ways of downloading offline files
        mainViewModel.isBulkDownloadRunning.observe(this) { isRunning ->
            if (!isRunning) mainViewModel.syncOfflineFiles()
        }
    }

    private fun observeFailureDownloadWorkerOffline() {
        DownloadOfflineFileManager.getFailedDownloadWorkerOffline(this)
            .observe(this) { workInfoList ->
                workInfoList.firstOrNull { it.state == WorkInfo.State.FAILED }?.let { failedWork ->
                    val hasLeftSpaceAfterDownload = failedWork.outputData.getBoolean(HAS_SPACE_LEFT_AFTER_DOWNLOAD_KEY, true)
                    if (!hasLeftSpaceAfterDownload) showSnackbar(R.string.notEnoughSpaceAfterDownload)
                    // We have to clear previous works info because otherwise, next time we'll start the app,
                    // we'll still have the previously failed result here.
                    WorkManager.getInstance(this@MainActivity).pruneWork()
                }
            }
    }

    private fun canDisplayInAppSnackbar() = inAppUpdateSnackbar?.isShown != true && getMainFab().isShown
    //endregion

    //region In-App Review
    private fun initAppReviewManager() {
        inAppReviewManager.init(
            onDialogShown = { trackInAppReview(MatomoName.PresentAlert) },
            onUserWantToReview = { trackInAppReview(MatomoName.Like) },
            onUserWantToGiveFeedback = { trackInAppReview(MatomoName.Dislike) },
        )
    }
    //endregion

    override fun onResume() {
        super.onResume()

        launchAllUpload(syncPermissions)

        mainViewModel.checkBulkDownloadStatus()

        AppSettings.appLaunches++

        displayInformationPanel()

        setBottomNavigationUserAvatar(this)
        startContentObserverService()
    }

    private fun launchNextDeleteRequest() {
        val filesUris = pendingFilesUrisQueue.firstOrNull() ?: return
        if (SDK_INT >= 30) {
            val deletionRequest = MediaStore.createDeleteRequest(contentResolver, filesUris)
            filesDeletionResult.launch(IntentSenderRequest.Builder(deletionRequest.intentSender).build())
        }
    }

    private fun handleDeletionOfUploadedPhotos() {

        fun getFilesUriToDelete(uploadFiles: List<UploadFile>): List<Uri> {
            return uploadFiles
                .filter {
                    !it.getUriObject().scheme.equals(ContentResolver.SCHEME_FILE) &&
                            !DocumentsContract.isDocumentUri(this, it.getUriObject())
                }
                .map { it.getUriObject() }
        }

        fun onConfirmation(filesUploadedRecently: ArrayList<UploadFile>, filesUriToDelete: List<Uri>) {
            if (SDK_INT >= 30) {
                lifecycleScope.launch {
                    pendingFilesUrisQueue.clear()
                    pendingFilesUrisQueue.addAll(filesUriToDelete.chunked(MEDIASTORE_DELETE_BATCH_LIMIT))
                    launchNextDeleteRequest()
                }
            } else {
                mainViewModel.deleteSynchronizedFilesOnDevice(filesUploadedRecently)
            }
        }

        val syncSettings = UploadFile.getAppSyncSettings() ?: return
        if (!syncSettings.deleteAfterSync) return
        if (UploadFile.getCurrentUserPendingUploadsCount() != 0) return
        val filesUploadedRecently = UploadFile.getAllUploadedFiles() ?: return
        if (filesUploadedRecently.size < SYNCED_FILES_DELETION_FILES_AMOUNT) return
        // We check that the filtered list of URIs is not empty before showing the dialog
        // and sending the request to MediaStore; otherwise, it would cause a crash.
        val filesUriToDelete = getFilesUriToDelete(filesUploadedRecently).takeIf { it.isNotEmpty() } ?: return

        deleteLocalMediaRequestDialog = Utils.createConfirmation(
            context = this,
            title = getString(R.string.modalDeletePhotosTitle),
            message = getString(R.string.modalDeletePhotosNumericDescription, filesUploadedRecently.size),
            buttonText = getString(R.string.buttonDelete),
            isDeletion = true,
            onConfirmation = { onConfirmation(filesUploadedRecently, filesUriToDelete) }
        )
    }

    private fun onDestinationChanged(destination: NavDestination, navigationArgs: Bundle?) {
        destination.addSentryBreadcrumb()

        val shouldHideBottomNavigation =
            navigationArgs?.let(FileListFragmentArgs::fromBundle)?.shouldHideBottomNavigation == true
        val shouldShowSmallFab = (navigationArgs?.let(FileListFragmentArgs::fromBundle)?.shouldShowSmallFab
            ?: navigationArgs?.let(AddFileBottomSheetDialogArgs::fromBundle)?.shouldShowSmallFab
            ?: navigationArgs?.let(FileInfoActionsBottomSheetDialogArgs::fromBundle)?.shouldShowSmallFab) == true

        handleBottomNavigationVisibility(destination.id, shouldHideBottomNavigation, shouldShowSmallFab)

        when (destination.id) {
            R.id.favoritesFragment,
            R.id.homeFragment,
            R.id.menuFragment,
            R.id.menuGalleryFragment,
            R.id.mySharesFragment,
            R.id.offlineFileFragment,
            R.id.recentChangesFragment,
            R.id.rootFilesFragment,
            R.id.searchFragment,
            R.id.trashFragment -> {
                // Defining default root folder
                mainViewModel.setCurrentFolderAsRoot()
            }
        }

        when (destination.id) {
            R.id.myKSuiteDashboardFragment, R.id.kSuiteProBottomSheetDialog -> {
                setColorStatusBar(SystemBarsColorScheme.KSuite)
                setColorNavigationBar(SystemBarsColorScheme.KSuite)
            }
            R.id.fileDetailsFragment -> {
                setColorNavigationBar(SystemBarsColorScheme.AppBar)
            }
            R.id.fileShareLinkSettingsFragment -> {
                setColorStatusBar(SystemBarsColorScheme.AppBar)
                setColorNavigationBar(SystemBarsColorScheme.AppBar)
            }
            R.id.downloadProgressDialog, R.id.previewSliderFragment, R.id.selectPermissionBottomSheetDialog -> Unit
            else -> {
                setColorStatusBar()
                setColorNavigationBar()
            }
        }

        destination.trackDestination()
    }

    private fun handleBottomNavigationVisibility(
        destinationId: Int,
        shouldHideBottomNavigation: Boolean,
        shouldShowSmallFab: Boolean,
    ) = with(binding) {

        val isGone = when (destinationId) {
            R.id.addFileBottomSheetDialog,
            R.id.fileInfoActionsBottomSheetDialog,
            R.id.fileListFragment -> shouldHideBottomNavigation || shouldShowSmallFab
            R.id.favoritesFragment,
            R.id.homeFragment,
            R.id.menuFragment,
            R.id.menuGalleryFragment,
            R.id.mySharesFragment,
            R.id.offlineFileFragment,
            R.id.recentChangesFragment,
            R.id.rootFilesFragment,
            R.id.sharedWithMeFragment,
            R.id.trashFragment -> shouldHideBottomNavigation
            else -> true
        }

        mainFab.isGone = isGone
        bottomNavigation.isGone = isGone
        bottomNavigationBackgroundView.isGone = isGone

        searchFab.isVisible = shouldShowSmallFab
    }

    /**
     * Handle shortcuts, the [Shortcuts.UPLOAD] case is already handled in [observeCurrentFolder].
     */
    private fun handleShortcuts() {
        navigationArgs?.shortcutId?.let { shortcutId ->
            trackEvent(MatomoCategory.Shortcuts.value, name = shortcutId)

            when (shortcutId) {
                Shortcuts.SEARCH.id -> {
                    ShortcutManagerCompat.reportShortcutUsed(this@MainActivity, Shortcuts.SEARCH.id)
                    navController.navigate(R.id.searchFragment)
                }
                Shortcuts.SCAN.id -> startScanFlow(scanFlowResultLauncher)
                Shortcuts.FEEDBACK.id -> openSupport()
                Shortcuts.UPLOAD.id -> Unit // Already handled elsewhere, @see kdoc
            }
        }
    }

    private fun observeCurrentFolder() = with(mainViewModel) {
        currentFolder.observe(this@MainActivity) { parentFolder ->
            binding.mainFab.isEnabled = parentFolder?.rights?.canCreateFile == true

            // TODO : We need to find a way to handle the case where the app has never fetched the private folder and
            //  therefore can't find it in Realm
            if (navigationArgs?.shortcutId == Shortcuts.UPLOAD.id &&
                mustOpenUploadShortcut &&
                // We only want to allow upload at the private folder's root
                parentFolder?.getVisibilityType() == VisibilityType.IS_PRIVATE
            ) {
                mainViewModel.mustOpenUploadShortcut = false
                uploadFilesHelper?.apply {
                    setParentFolder(parentFolder)
                    uploadFiles()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        saveLastNavigationItemSelected()
        deleteLocalMediaRequestDialog?.dismiss()
        deleteLocalMediaRequestDialog = null
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

    fun getBottomNavigation() = binding.bottomNavigation

    fun clickOnBottomBarFolders() {
        binding.bottomNavigation.findViewById<View>(R.id.rootFilesFragment).performClick()
    }

    enum class SystemBarsColorScheme(@ColorRes val statusBarColor: Int, @ColorRes val navigationBarColor: Int = statusBarColor) {
        AppBar(R.color.appBar),
        Default(R.color.background),
        KSuite(
            statusBarColor = R.color.myKSuiteDashboardStatusBarBackground,
            navigationBarColor = R.color.myKSuiteDashboardNavigationBarBackground,
        ),
    }

    companion object {
        private const val SYNCED_FILES_DELETION_FILES_AMOUNT = 10

        // Maximum number of elements in the list supported by the mediastore when Uris are to be deleted.
        // When you exceed this value, the system may not propagate dialog to delete the images,
        // and when you exceed 10_000 you receive a `NullPointerException`.
        private const val MEDIASTORE_DELETE_BATCH_LIMIT = 5_000
    }
}
