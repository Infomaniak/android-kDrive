/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.DownloadManager
import android.app.KeyguardManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Size
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.Window
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.findNavController
import coil.ImageLoader
import coil.load
import coil.request.Disposable
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.ui.OnlyOfficeActivity
import com.infomaniak.drive.ui.bottomSheetDialogs.NotSupportedExtensionBottomSheetDialog.Companion.FILE_ID
import com.infomaniak.drive.ui.fileList.fileShare.AvailableShareableItemsAdapter
import com.infomaniak.drive.utils.SyncUtils.isSyncActive
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.lib.core.models.User
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.UtilsUi.generateInitialsAvatarDrawable
import com.infomaniak.lib.core.utils.UtilsUi.getBackgroundColorBasedOnId
import com.infomaniak.lib.core.utils.UtilsUi.getInitials
import com.infomaniak.lib.core.utils.format
import io.sentry.Sentry
import kotlinx.android.synthetic.main.cardview_file_grid.view.*
import kotlinx.android.synthetic.main.item_file.view.*
import kotlinx.android.synthetic.main.item_file.view.fileFavorite
import kotlinx.android.synthetic.main.item_file.view.fileName
import kotlinx.android.synthetic.main.item_file.view.fileOffline
import kotlinx.android.synthetic.main.item_file.view.fileOfflineProgression
import kotlinx.android.synthetic.main.item_file.view.filePreview
import kotlinx.android.synthetic.main.item_file.view.progressLayout
import kotlinx.android.synthetic.main.item_user.view.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.roundToInt

typealias FileId = Int
typealias IsOffline = Boolean
typealias IsComplete = Boolean

fun Intent.clearStack() = apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }

fun Context.getAvailableMemory(): ActivityManager.MemoryInfo {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return ActivityManager.MemoryInfo().also { memoryInfo ->
        activityManager.getMemoryInfo(memoryInfo)
    }
}

fun Context.isKeyguardSecure(): Boolean {
    return (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardSecure ?: false
}

fun ImageView.loadUrl(
    uri: String?,
    @DrawableRes placeholder: Int = R.drawable.failback_image
): Disposable {
    return load(uri) {
        error(placeholder)
        fallback(placeholder)
        placeholder(R.drawable.placeholder)
    }
}

fun ImageView.loadAvatar(driveUser: DriveUser): Disposable =
    loadAvatar(driveUser.id, driveUser.getUserAvatar(), driveUser.displayName.getInitials())

fun ImageView.loadAvatar(user: User): Disposable = loadAvatar(user.id, user.avatar, user.getInitials())

fun ImageView.loadAvatar(id: Int, avatarUrl: String?, initials: String): Disposable {
    val imageLoader = ImageLoader.Builder(context).build()
    val fallback =
        context.generateInitialsAvatarDrawable(initials = initials, background = context.getBackgroundColorBasedOnId(id))
    return load(avatarUrl, imageLoader) {
        error(fallback)
        fallback(fallback)
        placeholder(R.drawable.placeholder)
    }
}

fun TextInputEditText.showOrHideEmptyError(): Boolean {
    val parentLayout = parent.parent as TextInputLayout

    parentLayout.error = if (text.isNullOrBlank()) context.getString(R.string.allEmptyInputError) else null
    return parentLayout.error != null
}

fun Cursor.uri(contentUri: Uri): Uri {
    return ContentUris.withAppendedId(contentUri, getLong(getColumnIndexOrThrow(MediaStore.MediaColumns._ID)))
}

fun Number.isPositive(): Boolean {
    return toLong() > 0
}

fun Activity.setColorStatusBar(appBar: Boolean = false) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        window.statusBarColor = ContextCompat.getColor(this, if (appBar) R.color.appBar else R.color.background)
        when (resources.configuration.uiMode.and(Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> {
                window.lightStatusBar(false)
            }
            else -> {
                window.lightStatusBar(true)
            }
        }
    } else {
        window.statusBarColor = Color.BLACK
    }
}

fun Window.lightStatusBar(enabled: Boolean) {
// TODO DON'T WORK
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//        if (enabled) {
//            insetsController?.setSystemBarsAppearance(APPEARANCE_LIGHT_STATUS_BARS, APPEARANCE_LIGHT_STATUS_BARS)
//        } else {
//            insetsController?.setSystemBarsAppearance(0, APPEARANCE_LIGHT_STATUS_BARS)
//        }
//    } else
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (enabled) {
            decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }
}

fun Activity.setColorNavigationBar(appBar: Boolean = false) {
    val color = if (appBar) R.color.appBar else R.color.background
    when (resources.configuration.uiMode.and(Configuration.UI_MODE_NIGHT_MASK)) {
        Configuration.UI_MODE_NIGHT_YES -> {
            window.navigationBarColor = ContextCompat.getColor(this, color)
            window.lightNavigationBar(false)
        }
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.navigationBarColor = ContextCompat.getColor(this, color)
                window.lightNavigationBar(true)
            } else {
                window.navigationBarColor = Color.BLACK
            }
        }
    }
}

fun Window.lightNavigationBar(enabled: Boolean) {
    //TODO Android 11
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (enabled) {
            decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
    }
}

fun View.setFileItem(
    file: File,
    isGrid: Boolean = false
) {
    fileName.text = file.name
    fileFavorite.visibility = if (file.isFavorite) VISIBLE else GONE
    fileDate?.visibility = if (file.id != ROOT_ID) VISIBLE else GONE
    fileDate?.text =
        if (file.deletedAt.isPositive()) file.getDeletedAt().format(context.getString(R.string.allDeletedFilePattern))
        else file.getLastModifiedAt().format(context.getString(R.string.allLastModifiedFilePattern))
    file.size?.let {
        fileSize?.text = FormatterFileSize.formatShortFileSize(context, it)
        fileSeparator?.visibility = VISIBLE
    } ?: run {
        fileSize?.text = ""
        fileSeparator?.visibility = GONE
    }

    progressLayout.visibility = GONE

    filePreview.scaleType = ImageView.ScaleType.CENTER
    when {
        file.isFolder() -> {
            when (file.getVisibilityType()) {
                File.VisibilityType.IS_TEAM_SPACE -> filePreview.load(R.drawable.ic_folder_common_documents)
                File.VisibilityType.IS_SHARED_SPACE -> filePreview.load(R.drawable.ic_folder_shared)
                File.VisibilityType.IS_COLLABORATIVE_FOLDER -> filePreview.load(R.drawable.ic_folder_dropbox)
                else -> {
                    if (file.isDisabled()) {
                        filePreview.load(R.drawable.ic_folder_disable)
                    } else {
                        filePreview.load(R.drawable.ic_folder_filled)
                    }
                }
            }
        }
        file.isDrive() -> {
            filePreview.load(R.drawable.ic_drive)
            filePreview.setColorFilter(Color.parseColor(file.driveColor))
        }
        else -> {
            when {
                file.hasThumbnail && (isGrid || file.getFileType() == File.ConvertedType.IMAGE
                        || file.getFileType() == File.ConvertedType.VIDEO) -> {
                    filePreview.scaleType = ImageView.ScaleType.CENTER_CROP
                    filePreview.loadUrl(file.thumbnail(), file.getFileType().icon)
                }
                file.isPendingUploadFolder() -> {
                    if (file.id == ROOT_ID) {
                        filePreview.load(R.drawable.ic_drive)
                        filePreview.setColorFilter(Color.parseColor(file.driveColor))
                    } else {
                        filePreview.load(R.drawable.ic_folder_filled)
                    }
                }
                file.isFromUploads && (file.getMimeType().startsWith("image/") || file.getMimeType().startsWith("video/")) -> {
                    filePreview.scaleType = ImageView.ScaleType.CENTER_CROP
                    filePreview.load(context.getLocalThumbnail(file)) {
                        fallback(file.getFileType().icon)
                    }
                }
                else -> {
                    filePreview.load(file.getFileType().icon)
                }
            }
            filePreview2?.load(file.getFileType().icon)
            setupFileProgress(file)
        }
    }
}

fun View.setupFileProgress(file: File) {
    val progress = file.currentProgress
    val isPendingOffline = file.isPendingOffline(context)
    val isSyncActive = context.isSyncActive()
    when {
        (isPendingOffline || isSyncActive) && progress in 0..99 -> {
            fileOffline.visibility = GONE
            if (fileOfflineProgression.isIndeterminate) {
                fileOfflineProgression.visibility = GONE
                fileOfflineProgression.isIndeterminate = false
            }
            fileOfflineProgression.progress = progress
            fileOfflineProgression.visibility = VISIBLE
            progressLayout.visibility = VISIBLE
        }
        isPendingOffline && progress == Utils.INDETERMINATE_PROGRESS -> {
            fileOffline.visibility = GONE
            fileOfflineProgression.visibility = GONE
            fileOfflineProgression.isIndeterminate = true
            fileOfflineProgression.visibility = VISIBLE
            progressLayout.visibility = VISIBLE
        }
        file.isOfflineFile(context) -> {
            fileOffline.visibility = VISIBLE
            fileOfflineProgression.visibility = GONE
            progressLayout.visibility = VISIBLE
        }
        else -> progressLayout.visibility = GONE
    }
}

fun View.setUserView(user: User, showChevron: Boolean = true, onItemClicked: (user: User) -> Unit) {
    userName.text = user.displayName
    userEmail.text = user.email
    userAvatar.loadAvatar(user)
    chevron.visibility = if (showChevron) VISIBLE else GONE
    setOnClickListener { onItemClicked(user) }
}

fun Long.toApiDate(): Date {
    return Date(this / 1000)
}

fun ImageView.animateRotation(isDeployed: Boolean = false) {
    val startDeg = if (isDeployed) 0F else 90F
    val endDeg = if (isDeployed) 90F else 0F
    this.startAnimation(
        RotateAnimation(startDeg, endDeg, Animation.RELATIVE_TO_SELF, 0.5F, Animation.RELATIVE_TO_SELF, 0.5F)
            .apply {
                duration = 200
                fillAfter = true
                repeatCount = 0
            })
}

fun View.setMargin(left: Int? = null, top: Int? = null, right: Int? = null, bottom: Int? = null) {
    val params = (layoutParams as? ViewGroup.MarginLayoutParams)
    params?.setMargins(
        left ?: params.leftMargin,
        top ?: params.topMargin,
        right ?: params.rightMargin,
        bottom ?: params.bottomMargin
    )
    layoutParams = params
}

/**
 * Send a value to the previous navigation
 */
fun <T> Fragment.setBackNavigationResult(key: String, value: T, popBackStack: Boolean = true) {
    findNavController().previousBackStackEntry?.savedStateHandle?.set(key, value)

    if (popBackStack) {
        findNavController().popBackStack()
    }
}

/**
 * Get the value sent by navigation popbackStack in the current navigation
 */
fun <T> Fragment.getBackNavigationResult(key: String, onResult: (result: T) -> Unit) {
    val backStackEntry = findNavController().currentBackStackEntry
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME && backStackEntry?.savedStateHandle?.contains(key) == true) {
            backStackEntry.savedStateHandle.get<T>(key)?.let(onResult)
            backStackEntry.savedStateHandle.remove<T>(key)
        }
    }

    backStackEntry?.lifecycle?.addObserver(observer)
    viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) backStackEntry?.lifecycle?.removeObserver(observer)
    })
}

/**
 * Return the screen size in DPs
 */
fun Activity.getScreenSizeInDp(): Point {
    val displayMetrics = DisplayMetrics()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.apply {
            getRealMetrics(displayMetrics)
        }
    } else {
        windowManager.defaultDisplay.getMetrics(displayMetrics)
    }

    val point = Point()
    displayMetrics.apply {
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

fun String.isEmail(): Boolean = android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()

fun MaterialAutoCompleteTextView.setupAvailableShareableItems(
    context: Context,
    itemList: List<Shareable>,
    notShareableUserIds: ArrayList<Int> = arrayListOf(),
    notShareableEmails: ArrayList<String> = arrayListOf(),
    notShareableTeamIds: ArrayList<Int> = arrayListOf(),
    onDataPassed: (item: Shareable) -> Unit
): AvailableShareableItemsAdapter {
    setDropDownBackgroundResource(R.drawable.background_popup)
    val availableUsersAdapter = AvailableShareableItemsAdapter(
        context = context,
        itemList = ArrayList(itemList),
        notShareableUserIds = notShareableUserIds,
        notShareableEmails = notShareableEmails,
        notShareableTeamIds = notShareableTeamIds
    ) { item ->
        onDataPassed(item)
    }
    setAdapter(availableUsersAdapter)
    setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) !availableUsersAdapter.addFirstAvailableItem() // if success -> close keyboard (false)
        true
    }

    return availableUsersAdapter
}

fun Collection<DriveUser>.removeCommonUsers(intersectedUsers: ArrayList<Int>): ArrayList<DriveUser> {
    return this.filterNot { availableUser ->
        intersectedUsers.any { it == availableUser.id }
    } as ArrayList<DriveUser>
}

fun Activity.showSnackbar(
    title: Int,
    anchorView: View? = null,
    actionButtonTitle: Int = R.string.buttonCancel,
    onActionClicked: (() -> Unit)? = null
) {
    Utils.showSnackbar(
        view = window.decorView.findViewById(android.R.id.content),
        title = title,
        anchorView = anchorView,
        actionButtonTitle = actionButtonTitle,
        onActionClicked = onActionClicked
    )
}

fun Activity.showSnackbar(
    title: String,
    anchorView: View? = null,
    actionButtonTitle: Int = R.string.buttonCancel,
    onActionClicked: (() -> Unit)? = null
) {
    Utils.showSnackbar(
        view = window.decorView.findViewById(android.R.id.content),
        title = title,
        anchorView = anchorView,
        actionButtonTitle = actionButtonTitle,
        onActionClicked = onActionClicked
    )
}

@SuppressLint("NewApi")
fun FragmentActivity.requestCredentials(onSuccess: () -> Unit) {
    val biometricPrompt = BiometricPrompt(this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.app_name))
        .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()

    biometricPrompt.authenticate(promptInfo)
}

fun View.hideKeyboard() {
    (context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager)
        .hideSoftInputFromWindow(this.windowToken, 0)
}

fun Fragment.openOnlyOfficeDocument(file: File) {
    if (file.onlyofficeConvertExtension?.isNotBlank() == true) {
        findNavController().navigate(R.id.notSupportedExtensionBottomSheetDialog, bundleOf(FILE_ID to file.id))
    } else {
        requireContext().openOnlyOfficeActivity(file)
    }
}

fun Context.openOnlyOfficeActivity(file: File) {
    startActivity(Intent(this, OnlyOfficeActivity::class.java).apply {
        putExtra(OnlyOfficeActivity.ONLYOFFICE_URL_TAG, file.onlyOfficeUrl())
    })
}

private fun Fragment.canNavigate(): Boolean {
    val className = when (val currentDestination = findNavController().currentDestination) {
        is FragmentNavigator.Destination -> currentDestination.className
        is DialogFragmentNavigator.Destination -> currentDestination.className
        else -> null
    }
    return javaClass.name == className
}

fun Fragment.safeNavigate(directions: NavDirections) {
    if (canNavigate()) findNavController().navigate(directions)
}

fun Fragment.safeNavigate(
    @IdRes resId: Int,
    args: Bundle? = null,
    navOptions: NavOptions? = null,
    navigatorExtras: Navigator.Extras? = null
) {
    if (canNavigate()) findNavController().navigate(resId, args, navOptions, navigatorExtras)
}

fun Fragment.navigateToUploadView(folderId: Int) {
    safeNavigate(
        R.id.uploadInProgressFragment, bundleOf(
            "folderID" to folderId,
            "folderName" to getString(R.string.uploadInProgressTitle)
        )
    )
}

fun Drive?.getDriveUsers(): List<DriveUser> = this?.users?.let { categories ->
    return@let DriveInfosController.getUsers(ArrayList(categories.drive + categories.account))
} ?: listOf()

fun Context.getLocalThumbnail(file: File): Bitmap? {
    val fileUri = file.path.toUri()
    val thumbnailSize = 100
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val size = Size(thumbnailSize, thumbnailSize)
        try {
            if (fileUri.scheme.equals(ContentResolver.SCHEME_FILE)) {
                if (file.getMimeType().contains("video")) {
                    ThumbnailUtils.createVideoThumbnail(fileUri.toFile(), size, null)
                } else {
                    ThumbnailUtils.createImageThumbnail(fileUri.toFile(), size, null)
                }
            } else {
                contentResolver.loadThumbnail(fileUri, size, null)
            }
        } catch (e: Exception) {
            null
        }
    } else {

        val localFile = fileUri.lastPathSegment?.split(":")?.let { list ->
            list.getOrNull(1)?.let { path -> java.io.File(path) }
        }
        val isSchemeFile = fileUri.scheme.equals(ContentResolver.SCHEME_FILE)

        val externalRealPath = when {
            !isSchemeFile && localFile?.exists() == true -> {
                Sentry.withScope { scope -> // Get more information in uri with absolute path
                    scope.setExtra("uri", "$fileUri")
                    Sentry.captureMessage("Uri contains absolute path")
                }
                localFile.absolutePath
            }
            "com.android.externalstorage.documents" == fileUri.authority -> {
                Utils.getRealPathFromExternalStorage(this, fileUri)
            }
            else -> ""
        }

        if (isSchemeFile || externalRealPath.isNotBlank()) {
            val path = if (externalRealPath.isNotBlank()) externalRealPath else fileUri.path
            path?.let {
                if (file.getMimeType().contains("video")) {
                    ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MICRO_KIND)
                } else {
                    Utils.extractThumbnail(path, thumbnailSize, thumbnailSize)
                }
            }
        } else {
            try {
                ContentUris.parseId(fileUri)
            } catch (e: Exception) {
                fileUri.lastPathSegment?.split(":")?.let {
                    it.getOrNull(1)?.toLong()
                }
            }?.let { fileId ->
                val options = BitmapFactory.Options().apply {
                    outWidth = thumbnailSize
                    outHeight = thumbnailSize
                }
                if (contentResolver.getType(fileUri)?.contains("video") == true) {
                    MediaStore.Video.Thumbnails.getThumbnail(
                        contentResolver,
                        fileId,
                        MediaStore.Video.Thumbnails.MICRO_KIND,
                        options
                    )
                } else {
                    MediaStore.Images.Thumbnails.getThumbnail(
                        contentResolver,
                        fileId,
                        MediaStore.Images.Thumbnails.MICRO_KIND,
                        options
                    )
                }
            }
        }
    }
}

fun Context.startDownloadFile(downloadURL: Uri, fileName: String) {
    var formattedFileName = fileName.replace(Regex("[\\\\/:*?\"<>|%]"), "_")

    // fix IllegalArgumentException only on Android 10 if multi dot
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
        formattedFileName = formattedFileName.replace(regex = "\\.{2,}".toRegex(), replacement = ".")
    }
    val request = DownloadManager.Request(downloadURL).apply {
        setTitle(formattedFileName)
        setDescription(getString(R.string.app_name))
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, formattedFileName)
        HttpUtils.getHeaders(contentType = null).toMap().forEach { addRequestHeader(it.key, it.value) }
        setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) setVisibleInDownloadsUi(true)
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    }

    (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
}

fun View.setUploadFileInProgress(title: Int, onClickListener: () -> Unit) {
    val radius = resources.getDimension(R.dimen.cardViewRadius)
    (this as MaterialCardView).shapeAppearanceModel = shapeAppearanceModel.toBuilder()
        .setTopLeftCorner(CornerFamily.ROUNDED, radius)
        .setTopRightCorner(CornerFamily.ROUNDED, radius)
        .setBottomLeftCorner(CornerFamily.ROUNDED, radius)
        .setBottomRightCorner(CornerFamily.ROUNDED, radius)
        .build()

    fileName.setText(title)

    setOnClickListener { onClickListener() }
}

fun View.updateUploadFileInProgress(pendingFilesCount: Int) {
    if (pendingFilesCount > 0) {
        fileSize.text = resources.getQuantityString(
            R.plurals.uploadInProgressNumberFile,
            pendingFilesCount,
            pendingFilesCount
        )
        filePreview.visibility = GONE
        fileProgression.visibility = VISIBLE
        visibility = VISIBLE
    } else {
        visibility = GONE
    }
}
