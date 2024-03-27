/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Point
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Patterns
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import coil.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.BuildConfig.SUPPORT_URL
import com.infomaniak.drive.MatomoDrive.trackFileActionEvent
import com.infomaniak.drive.MatomoDrive.trackShareRightsEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileCategory
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.databinding.CardviewFileListBinding
import com.infomaniak.drive.databinding.ItemUserBinding
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.OnlyOfficeActivity
import com.infomaniak.drive.ui.bottomSheetDialogs.NotSupportedExtensionBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.FileListFragmentArgs
import com.infomaniak.drive.ui.fileList.fileShare.AvailableShareableItemsAdapter
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.login.InfomaniakLogin
import handleActionDone
import io.realm.RealmList
import io.sentry.Sentry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

typealias FileId = Int
typealias IOFile = java.io.File
typealias IsComplete = Boolean
typealias Position = Int

fun Context.getAvailableMemory(): ActivityManager.MemoryInfo {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return ActivityManager.MemoryInfo().also { memoryInfo ->
        activityManager.getMemoryInfo(memoryInfo)
    }
}

fun ImageView.loadAny(data: Any?, @DrawableRes errorRes: Int = R.drawable.fallback_image) {
    load(data) {
        error(errorRes)
        fallback(errorRes)
        placeholder(R.drawable.placeholder)
    }
}

fun ImageView.loadAvatar(driveUser: DriveUser) {
    loadAvatar(driveUser.id, driveUser.getUserAvatar(), driveUser.getInitials())
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

fun Activity.clearEdgeToEdge() {
    toggleSystemBar(true)
    window.toggleEdgeToEdge(false)
}

fun Activity.setupTransparentStatusBar() {
    window?.apply {
        statusBarColor = ContextCompat.getColor(this@setupTransparentStatusBar, R.color.previewBackgroundTransparent)

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

fun Activity.setColorStatusBar(appBar: Boolean = false) = with(window) {
    if (VERSION.SDK_INT >= VERSION_CODES.M) {
        statusBarColor = ContextCompat.getColor(this@setColorStatusBar, if (appBar) R.color.appBar else R.color.background)
        lightStatusBar(!isNightModeEnabled())
    } else {
        statusBarColor = Color.BLACK
    }
}

fun Activity.setColorNavigationBar(appBar: Boolean = false) = with(window) {
    val nightModeEnabled = isNightModeEnabled()
    if (nightModeEnabled || VERSION.SDK_INT >= VERSION_CODES.O) {
        val color = if (appBar) R.color.appBar else R.color.background
        navigationBarColor = ContextCompat.getColor(this@setColorNavigationBar, color)
        lightNavigationBar(!nightModeEnabled)
    } else {
        navigationBarColor = Color.BLACK
    }
}

fun String.isValidUrl(): Boolean = Patterns.WEB_URL.matcher(this).matches()

fun ItemUserBinding.setUserView(user: User, showChevron: Boolean = true, onItemClicked: (user: User) -> Unit) {
    userName.text = user.displayName
    userEmail.text = user.email
    userAvatar.loadAvatar(user)
    chevron.isVisible = showChevron
    root.setOnClickListener { onItemClicked(user) }
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
    onDataPassed: (item: Shareable) -> Unit
): AvailableShareableItemsAdapter {
    setDropDownBackgroundResource(R.drawable.background_popup)
    val availableUsersAdapter = AvailableShareableItemsAdapter(
        context = context,
        itemList = ArrayList(itemList),
        notShareableIds = notShareableIds,
        notShareableEmails = notShareableEmails
    ) { item ->
        onDataPassed(item)
    }
    setAdapter(availableUsersAdapter)
    handleActionDone { !availableUsersAdapter.addFirstAvailableItem() }

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

fun Fragment.openOnlyOfficeDocument(file: File) {
    if (file.conversion?.whenOnlyoffice == true) {
        findNavController().navigate(
            R.id.notSupportedExtensionBottomSheetDialog,
            NotSupportedExtensionBottomSheetDialogArgs(file.id).toBundle()
        )
    } else {
        requireContext().openOnlyOfficeActivity(file)
    }
}

fun Context.openOnlyOfficeActivity(file: File) {
    startActivity(Intent(this, OnlyOfficeActivity::class.java).apply {
        putExtra(OnlyOfficeActivity.ONLYOFFICE_URL_TAG, file.onlyOfficeUrl())
        putExtra(OnlyOfficeActivity.ONLYOFFICE_FILENAME_TAG, file.name)
    })
}

fun Fragment.navigateToParentFolder(folderId: Int, mainViewModel: MainViewModel) {
    with(findNavController()) {
        popBackStack(R.id.homeFragment, false)
        (requireActivity() as MainActivity).getBottomNavigation().findViewById<View>(R.id.fileListFragment).performClick()
        mainViewModel.navigateFileListTo(this, folderId)
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

fun CardviewFileListBinding.setUploadFileInProgress(title: Int, onClickListener: () -> Unit) {
    val radius = context.resources.getDimension(R.dimen.cardViewRadius)
    root.shapeAppearanceModel = root.shapeAppearanceModel.toBuilder()
        .setTopLeftCorner(CornerFamily.ROUNDED, radius)
        .setTopRightCorner(CornerFamily.ROUNDED, radius)
        .setBottomLeftCorner(CornerFamily.ROUNDED, radius)
        .setBottomRightCorner(CornerFamily.ROUNDED, radius)
        .build()

    itemViewFile.fileName.setText(title)

    root.setOnClickListener { onClickListener() }
}

fun CardviewFileListBinding.updateUploadFileInProgress(pendingFilesCount: Int, parentLayout: ViewGroup) = with(itemViewFile) {
    if (pendingFilesCount > 0) {
        fileSize.text = context.resources.getQuantityString(
            R.plurals.uploadInProgressNumberFile,
            pendingFilesCount,
            pendingFilesCount
        )
        filePreview.isGone = true
        fileProgression.isVisible = true
        parentLayout.isVisible = true
    } else {
        parentLayout.isGone = true
    }
}

fun Context.shareText(text: String) {
    trackShareRightsEvent("shareButton")
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
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
    appUID = BuildConfig.APPLICATION_ID,
    clientID = BuildConfig.CLIENT_ID,
    accessType = null,
)

//region Worker
fun OneTimeWorkRequest.Builder.setExpeditedIfAvailable() = apply {
    if (VERSION.SDK_INT >= VERSION_CODES.S) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
}
//endregion

fun Context.openSupport() {
    ShortcutManagerCompat.reportShortcutUsed(this, Shortcuts.FEEDBACK.id)
    openUrl(SUPPORT_URL)
}

fun Context.formatShortBinarySize(size: Long, valueOnly: Boolean = false): String {

    fun Long.binaryToDecimal(): Long {

        val binaryUnit = 1_024.0f
        val decimalUnit = 1_000.0f
        var units = 0 // BYTE
        val maxUnits = 5 // KILOBYTE, MEGABYTE, GIGABYTE, TERABYTE, PETABYTE
        var result = abs(this).toFloat()

        while (result > 900 && units < maxUnits) {
            units++
            result /= binaryUnit
        }

        if (valueOnly) return result.toLong()

        repeat(units) {
            result *= decimalUnit
        }

        return result.toLong()
    }

    val decimalSize = when {
        VERSION.SDK_INT >= VERSION_CODES.O -> size.binaryToDecimal()
        valueOnly -> size.binaryToDecimal()
        else -> size
    }

    return if (valueOnly) {
        "$decimalSize"
    } else {
        Formatter.formatShortFileSize(this, decimalSize)
    }
}

fun Context.shareFile(getUriToShare: () -> Uri?) {
    trackFileActionEvent("sendFileCopy")

    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_STREAM, getUriToShare())
        type = "*/*"
    }

    runCatching {
        startActivity(Intent.createChooser(shareIntent, getString(R.string.buttonSendCopy)))
    }.onFailure {
        Sentry.captureException(it)
    }
}
