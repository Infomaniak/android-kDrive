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
package com.infomaniak.drive.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.Patterns
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import coil.ImageLoader
import coil.imageLoader
import coil.load
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.infomaniak.core.ksuite.myksuite.ui.utils.MatomoMyKSuite
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.BuildConfig.SUPPORT_URL
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackShareRightsEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileCategory
import com.infomaniak.drive.data.models.FileListNavigationType
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.databinding.ItemUserBinding
import com.infomaniak.drive.databinding.LayoutNoNetworkSmallBinding
import com.infomaniak.drive.databinding.LayoutSwitchDriveBinding
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.MainActivity.SystemBarsColorScheme
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.OnlyOfficeActivity
import com.infomaniak.drive.ui.bottomSheetDialogs.NotSupportedExtensionBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.FileListFragmentArgs
import com.infomaniak.drive.ui.fileList.FileListViewModel
import com.infomaniak.drive.ui.fileList.fileShare.AvailableShareableItemsAdapter
import com.infomaniak.drive.utils.FilePresenter.displayFile
import com.infomaniak.drive.utils.FilePresenter.openFolder
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.drive.views.PendingFilesView
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.isNightModeEnabled
import com.infomaniak.lib.core.utils.lightNavigationBar
import com.infomaniak.lib.core.utils.lightStatusBar
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.toggleEdgeToEdge
import com.infomaniak.lib.login.InfomaniakLogin
import handleActionDone
import io.realm.RealmList
import io.sentry.Sentry
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.infomaniak.core.auth.BuildConfig as AuthBuildConfig

typealias FileId = Int
typealias IOFile = java.io.File
typealias IsComplete = Boolean
typealias Position = Int

fun getAvailableStorageInBytes(path: String = Environment.getDataDirectory().path): Long = with(StatFs(path)) {
    return@with availableBlocksLong * blockSizeLong
}

fun Context.getAvailableMemory(): ActivityManager.MemoryInfo {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return ActivityManager.MemoryInfo().also { memoryInfo ->
        activityManager.getMemoryInfo(memoryInfo)
    }
}

fun ImageView.loadAny(
    data: Any?,
    @DrawableRes errorRes: Int = R.drawable.fallback_image,
    imageLoader: ImageLoader = context.imageLoader
) {
    load(data, imageLoader) {
        error(errorRes)
        fallback(errorRes)
        placeholder(R.drawable.placeholder)
    }
}

fun ImageView.loadAvatar(driveUser: DriveUser) {
    loadAvatar(driveUser.id, driveUser.avatar, driveUser.getInitials())
}

fun TextInputEditText.showOrHideEmptyError(): Boolean {
    val parentLayout = parent.parent as TextInputLayout
    parentLayout.error = if (text.isNullOrBlank()) context.getString(R.string.allEmptyInputError) else null
    return parentLayout.error != null
}

fun Cursor.uri(contentUri: Uri): Uri {
    return ContentUris.withAppendedId(contentUri, getLong(getColumnIndexOrThrow(MediaStore.MediaColumns._ID)))
}

fun Number.isPositive(): Boolean = toLong() > 0

fun Activity.setupStatusBarForPreview() {
    window?.apply {
        statusBarColor = ContextCompat.getColor(this@setupStatusBarForPreview, R.color.previewBackgroundTransparent)

        lightStatusBar(false)
        toggleEdgeToEdge(true)
    }
}

fun Activity.toggleSystemBar(show: Boolean) {
    ViewCompat.getWindowInsetsController(window.decorView)?.apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val systemBars = WindowInsetsCompat.Type.systemBars()
        if (show) show(systemBars) else hide(systemBars)
    }
}

fun Activity.setColorStatusBar(colorScheme: SystemBarsColorScheme = SystemBarsColorScheme.Default) = with(window) {
    statusBarColor = ContextCompat.getColor(this@setColorStatusBar, colorScheme.statusBarColor)
    lightStatusBar(!isNightModeEnabled())
}

fun Activity.setColorNavigationBar(colorScheme: SystemBarsColorScheme = SystemBarsColorScheme.Default) = with(window) {
    navigationBarColor = ContextCompat.getColor(this@setColorNavigationBar, colorScheme.navigationBarColor)
    lightNavigationBar(!isNightModeEnabled())
}

fun String.isValidUrl(): Boolean = Patterns.WEB_URL.matcher(this).matches()

fun ItemUserBinding.setUserView(
    user: User,
    showRightIndicator: Boolean = true,
    showCurrentUser: Boolean = false,
    withForceClick: Boolean = true,
    onItemClicked: (user: User) -> Unit,
) {
    val isCurrentUser = AccountUtils.currentUserId == user.id
    userName.text = user.displayName
    userEmail.text = user.email
    userAvatar.loadAvatar(user)

    fun getRightIcon(): Drawable? {
        return if (isCurrentUser && showCurrentUser) {
            ResourcesCompat.getDrawable(this.context.resources, R.drawable.ic_check, null)?.apply {
                setTint(ResourcesCompat.getColor(this@setUserView.context.resources, R.color.iconColor, null))
            }
        } else {
            ResourcesCompat.getDrawable(this.context.resources, R.drawable.ic_chevron_right, null)
        }
    }

    if (showRightIndicator) rightIndicator.setImageDrawable(getRightIcon())

    if (!isCurrentUser || withForceClick) {
        root.setOnClickListener { onItemClicked(user) }
    }
}

fun ImageView.animateRotation(isDeployed: Boolean = false) {
    val startDeg = if (isDeployed) 0.0f else 90.0f
    val endDeg = if (isDeployed) 90.0f else 0.0f
    this.startAnimation(
        RotateAnimation(startDeg, endDeg, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
            .apply {
                duration = 200
                fillAfter = true
                repeatCount = 0
            })
}

/**
 * Return the screen size in DPs
 */
fun Activity.getScreenSizeInDp(): Point {
    val point = Point()
    application.resources.displayMetrics.apply {
        point.x = (widthPixels / density).roundToInt()
        point.y = (heightPixels / density).roundToInt()
    }

    return point
}

/**
 * Get the nearest value of precised Int in a typed-array of Ints
 */
fun Array<Int>.getNearestValue(number: Int): Int {
    var finalIndex = 0
    var initialDistance: Int = abs(this[0] - number)
    for (value in 1 until size) {
        val currentDistance = abs(this[value] - number)
        if (currentDistance < initialDistance) {
            finalIndex = value
            initialDistance = currentDistance
        }
    }
    return this[finalIndex]
}

fun String.isEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()

fun MaterialAutoCompleteTextView.setupAvailableShareableItems(
    context: Context,
    itemList: List<Shareable>,
    notShareableIds: ArrayList<Int> = arrayListOf(),
    notShareableEmails: ArrayList<String> = arrayListOf(),
    onDataPassed: (item: Shareable) -> Unit,
): AvailableShareableItemsAdapter {
    setDropDownBackgroundResource(R.drawable.background_popup)
    val availableUsersAdapter = AvailableShareableItemsAdapter(
        context = context,
        itemList = ArrayList(itemList),
        notShareableIds = notShareableIds,
        notShareableEmails = notShareableEmails,
        getCurrentText = { text },
        onItemClick = onDataPassed,
    )

    setAdapter(availableUsersAdapter)
    handleActionDone { if (text.isNotBlank()) !availableUsersAdapter.addFirstAvailableItem() }

    return availableUsersAdapter
}

fun Collection<DriveUser>.removeCommonUsers(intersectedUsers: ArrayList<Int>): ArrayList<DriveUser> {
    return this.filterNot { availableUser ->
        intersectedUsers.any { it == availableUser.id }
    } as ArrayList<DriveUser>
}

fun Fragment.showSnackbar(
    titleId: Int,
    showAboveFab: Boolean = false,
    actionButtonTitle: Int = R.string.buttonCancel,
    onActionClicked: (() -> Unit)? = null
) {
    showSnackbar(getString(titleId), showAboveFab, actionButtonTitle, onActionClicked)
}

fun Fragment.showSnackbar(
    title: String,
    showAboveFab: Boolean = false,
    actionButtonTitle: Int = R.string.buttonCancel,
    onActionClicked: (() -> Unit)? = null
) {
    (activity as? MainActivity)?.let {
        it.showSnackbar(title, if (showAboveFab) it.getMainFab() else null, actionButtonTitle, onActionClicked)
    }
}

fun Fragment.openOnlyOfficeDocument(file: File, isInternetAvailable: Boolean) {
    if (isInternetAvailable) {
        if (file.conversion?.whenOnlyoffice == true) {
            findNavController().navigate(
                R.id.notSupportedExtensionBottomSheetDialog,
                NotSupportedExtensionBottomSheetDialogArgs(file.id).toBundle()
            )
        } else {
            requireContext().openOnlyOfficeActivity(file)
        }
    } else {
        Toast.makeText(requireContext(), getString(R.string.noConnection), Toast.LENGTH_LONG).show()
    }
}

fun Context.openOnlyOfficeActivity(file: File) {
    startActivity(Intent(this, OnlyOfficeActivity::class.java).apply {
        putExtra(OnlyOfficeActivity.ONLYOFFICE_URL_TAG, ApiRoutes.getOnlyOfficeUrl(file))
        putExtra(OnlyOfficeActivity.ONLYOFFICE_FILENAME_TAG, file.name)
    })
}

fun Fragment.navigateToParentFolder(folderId: Int, mainViewModel: MainViewModel) {
    with(findNavController()) {
        popBackStack(R.id.homeFragment, false)
        (requireActivity() as MainActivity).clickOnBottomBarFolders()
        val userDrive = UserDrive(sharedWithMe = false)
        mainViewModel.navigateFileListTo(this, folderId, userDrive, null)
    }
}

fun Fragment.navigateToUploadView(folderId: Int, folderName: String? = null) {
    safeNavigate(
        R.id.uploadInProgressFragment,
        FileListFragmentArgs(
            folderId = folderId,
            folderName = folderName ?: getString(R.string.uploadInProgressTitle),
        ).toBundle(),
    )
}

fun Drive?.getDriveUsers(): List<DriveUser> = this?.users?.let { categories ->
    return@let DriveInfosController.getUsers(ArrayList(categories.drive + categories.account))
} ?: listOf()

fun Context.shareText(text: String, title: String? = null) {
    trackShareRightsEvent(MatomoName.ShareButton)
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        title?.let { putExtra(Intent.EXTRA_TITLE, it) }
        type = "text/plain"
    }
    ContextCompat.startActivity(this, Intent.createChooser(intent, null), null)
}

fun Category.getName(context: Context): String = when (name) {
    "PREDEF_CAT_BANKING" -> context.getString(R.string.categoryBanking)
    "PREDEF_CAT_BILL" -> context.getString(R.string.categoryBill)
    "PREDEF_CAT_CONTRACT" -> context.getString(R.string.categoryContract)
    "PREDEF_CAT_FORM" -> context.getString(R.string.categoryForm)
    "PREDEF_CAT_HOBBIES" -> context.getString(R.string.categoryHobbies)
    "PREDEF_CAT_ID" -> context.getString(R.string.categoryID)
    "PREDEF_CAT_INSURANCE" -> context.getString(R.string.categoryInsurance)
    "PREDEF_CAT_RESUME" -> context.getString(R.string.categoryResume)
    "PREDEF_CAT_QUOTATION" -> context.getString(R.string.categoryQuotation)
    "PREDEF_CAT_TAXATION" -> context.getString(R.string.categoryTaxation)
    "PREDEF_CAT_TRANSPORTATION" -> context.getString(R.string.categoryTransportation)
    "PREDEF_CAT_WARRANTY" -> context.getString(R.string.categoryWarranty)
    "PREDEF_CAT_WORK" -> context.getString(R.string.categoryWork)
    else -> name
}

fun RealmList<Category>.find(id: Int): Category? {
    return where().equalTo(Category::id.name, id).findFirst()
}

fun RealmList<FileCategory>.find(id: Int): FileCategory? {
    return where().equalTo(FileCategory::categoryId.name, id).findFirst()
}

fun MaterialCardView.setCornersRadius(topCornerRadius: Float, bottomCornerRadius: Float) {
    shapeAppearanceModel = shapeAppearanceModel
        .toBuilder()
        .setTopLeftCorner(CornerFamily.ROUNDED, topCornerRadius)
        .setTopRightCorner(CornerFamily.ROUNDED, topCornerRadius)
        .setBottomLeftCorner(CornerFamily.ROUNDED, bottomCornerRadius)
        .setBottomRightCorner(CornerFamily.ROUNDED, bottomCornerRadius)
        .build()
}

fun Activity.getAdjustedColumnNumber(expectedItemSize: Int, minColumns: Int = 2, maxColumns: Int = 5): Int {
    val screenWidth = getScreenSizeInDp().x
    return min(max(minColumns, screenWidth / expectedItemSize), maxColumns)
}

fun <T> ApiResponse<ArrayList<T>>.isLastPage() = (data?.size ?: 0) < itemsPerPage

fun Context.getInfomaniakLogin() = InfomaniakLogin(
    context = this,
    loginUrl = AuthBuildConfig.LOGIN_ENDPOINT_URL,
    appUID = BuildConfig.APPLICATION_ID,
    clientID = BuildConfig.CLIENT_ID,
    accessType = null,
    sentryCallback = { error -> SentryLog.e(tag = "WebViewLogin", error) }
)

//region Worker
fun OneTimeWorkRequest.Builder.setExpeditedIfAvailable() = apply {
    if (SDK_INT >= 31) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
}
//endregion

fun Context.openSupport() {
    ShortcutManagerCompat.reportShortcutUsed(this, Shortcuts.FEEDBACK.id)
    openUrl(SUPPORT_URL)
}

fun Context.shareFile(getUriToShare: () -> Uri?) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val uriToShare = getUriToShare()
        putExtra(Intent.EXTRA_STREAM, uriToShare)
        clipData = ClipData.newUri(contentResolver, "", uriToShare)
        type = "*/*"
    }

    runCatching {
        startActivity(Intent.createChooser(shareIntent, getString(R.string.buttonSendCopy)))
    }.onFailure {
        Sentry.captureException(it)
    }
}

fun LayoutSwitchDriveBinding.setDriveHeader(currentDrive: Drive) {
    switchDriveButton.text = currentDrive.name
}

private fun LayoutSwitchDriveBinding.setupSwitchDriveButton(fragment: Fragment) {
    AccountUtils.getCurrentDrive()?.let(::setDriveHeader)

    if (DriveInfosController.hasSingleDrive(AccountUtils.currentUserId)) {
        switchDriveButton.apply {
            icon = null
            isEnabled = false
        }
    } else {
        offsetOverlayedRipple.setOnClickListener { fragment.safeNavigate(R.id.switchDriveDialog) }
    }

    fragment.viewLifecycleOwner.lifecycle.addObserver(
        object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Event) {
                if (event == Event.ON_RESUME) AccountUtils.getCurrentDrive()?.let(::setDriveHeader)
            }
        },
    )
}

fun Fragment.setupDriveToolbar(
    collapsingToolbarLayout: CollapsingToolbarLayout,
    switchDriveLayout: LayoutSwitchDriveBinding,
    appBar: AppBarLayout,
) {
    val currentDrive = AccountUtils.getCurrentDrive(forceRefresh = true)
    if (currentDrive == null) {
        switchDriveLayout.root.isGone = true
        addNoDrivesBreadcrumbsToSentry()
        return
    }

    collapsingToolbarLayout.title = currentDrive.name
    switchDriveLayout.setupSwitchDriveButton(this)

    appBar.addOnOffsetChangedListener { _, verticalOffset ->
        val fullyExpanded = verticalOffset == 0
        switchDriveLayout.root.isVisible = fullyExpanded

        if (fullyExpanded) {
            collapsingToolbarLayout.setExpandedTitleTextColor(ColorStateList.valueOf(Color.TRANSPARENT))
        } else {
            collapsingToolbarLayout.setExpandedTitleTextAppearance(R.style.CollapsingToolbarExpandedTitleTextAppearance)
        }
    }
}

fun Fragment.observeNavigateFileListTo(mainViewModel: MainViewModel, fileListViewModel: FileListViewModel) {
    mainViewModel.navigateFileListTo.observe(viewLifecycleOwner) { file ->
        when (file) {
            is FileListNavigationType.Folder -> {
                if (file.file.isFolder()) {
                    openFolder(
                        navigationType = file,
                        shouldHideBottomNavigation = false,
                        shouldShowSmallFab = false,
                        fileListViewModel = fileListViewModel,
                    )
                } else {
                    displayFile(file.file, mainViewModel, fileAdapter = null)
                }
            }
            is FileListNavigationType.Subfolder -> {
                openFolder(
                    navigationType = file,
                    shouldHideBottomNavigation = false,
                    shouldShowSmallFab = false,
                    fileListViewModel = fileListViewModel,
                )

            }
        }
    }
}

fun Fragment.observeAndDisplayNetworkAvailability(
    mainViewModel: MainViewModel,
    noNetworkBinding: LayoutNoNetworkSmallBinding,
    noNetworkBindingDirectParent: ViewGroup,
    additionalChanges: ((isInternetAvailable: Boolean) -> Unit)? = null,
) {
    viewLifecycleOwner.lifecycleScope.launch {
        mainViewModel.isNetworkAvailable.collect { isNetworkAvailable ->
            val togetherAutoTransition = AutoTransition().apply { ordering = TransitionSet.ORDERING_TOGETHER }
            with(togetherAutoTransition) {
                noNetworkBindingDirectParent.children.forEach { child -> addTarget(child) }
                TransitionManager.beginDelayedTransition(noNetworkBindingDirectParent, this)
            }

            noNetworkBinding.noNetwork.isGone = isNetworkAvailable != false
            additionalChanges?.invoke(isNetworkAvailable != false)
        }
    }
}

fun Fragment.setupRootPendingFilesIndicator(countLiveData: LiveData<Int>, pendingFilesView: PendingFilesView) {
    pendingFilesView.setUploadFileInProgress(this, OTHER_ROOT_ID)
    countLiveData.observe(viewLifecycleOwner, pendingFilesView::updateUploadFileInProgress)
}

fun MainActivity.showQuotasExceededSnackbar(navController: NavController, drive: Drive?) {
    showSnackbar(
        title = R.string.errorQuotaExceeded,
        anchor = getMainFab(),
        actionButtonTitle = R.string.buttonUpgrade,
        onActionClicked = {
            val matomoName = MatomoMyKSuite.NOT_ENOUGH_STORAGE_UPGRADE_NAME
            if (drive?.isKSuiteProUpgradable == true) {
                openKSuiteProBottomSheet(navController, drive.kSuite!!, drive.isAdmin, matomoName)
            } else {
                openMyKSuiteUpgradeBottomSheet(navController, matomoName)
            }
        },
    )
}

fun String.isUrlFile() = endsWith(".url", ignoreCase = true)

fun String.isWeblocFile() = endsWith(".webloc", ignoreCase = true)

//region private
private fun addNoDrivesBreadcrumbsToSentry() {
    Sentry.captureMessage("Current drive is null, it should not happen") { scope ->
        scope.setExtra("CurrentDriveId", AccountUtils.currentDriveId.toString())
        scope.setExtra("CurrentUserId", AccountUtils.currentUserId.toString())

        val driveCount = runCatching {
            DriveInfosController.getDrivesCount(AccountUtils.currentUserId, AccountUtils.currentDriveId)
        }.getOrNull()
        scope.setTag("Number of drive in realm", driveCount.toString())

        AccountUtils.currentUser?.apiToken?.let { apiToken ->
            val scrubbedAccessToken = apiToken.accessToken.replaceRange(2..apiToken.accessToken.length - 2, "*")
            scope.setExtra("Access token", scrubbedAccessToken)
            val scrubbedRefreshToken = apiToken.refreshToken?.let { refreshToken ->
                refreshToken.replaceRange(2..refreshToken.length - 2, "*")
            }
            scope.setExtra("Refresh token", scrubbedRefreshToken.toString())
        }
    }
}
//endregion
