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
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Patterns
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.forEachIndexed
import androidx.core.view.isGone
import androidx.core.view.isVisible
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
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.models.File.Companion.getFileTypeFromExtension
import com.infomaniak.drive.data.models.File.VisibilityType
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.ui.OnlyOfficeActivity
import com.infomaniak.drive.ui.bottomSheetDialogs.NotSupportedExtensionBottomSheetDialog.Companion.FILE_ID
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.MAX_DISPLAYED_CATEGORIES
import com.infomaniak.drive.ui.fileList.UploadInProgressFragmentArgs
import com.infomaniak.drive.ui.fileList.fileShare.AvailableShareableItemsAdapter
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.views.CategoryIconView
import com.infomaniak.lib.core.models.User
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.UtilsUi.generateInitialsAvatarDrawable
import com.infomaniak.lib.core.utils.UtilsUi.getBackgroundColorBasedOnId
import com.infomaniak.lib.core.utils.UtilsUi.getInitials
import com.infomaniak.lib.core.utils.format
import io.realm.RealmList
import io.sentry.Sentry
import kotlinx.android.synthetic.main.cardview_file_grid.view.*
import kotlinx.android.synthetic.main.item_file.view.*
import kotlinx.android.synthetic.main.item_file.view.categoriesLayout
import kotlinx.android.synthetic.main.item_file.view.fileFavorite
import kotlinx.android.synthetic.main.item_file.view.fileName
import kotlinx.android.synthetic.main.item_file.view.fileOffline
import kotlinx.android.synthetic.main.item_file.view.fileOfflineProgression
import kotlinx.android.synthetic.main.item_file.view.filePreview
import kotlinx.android.synthetic.main.item_file.view.progressLayout
import kotlinx.android.synthetic.main.item_user.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

typealias FileId = Int
typealias IsComplete = Boolean
typealias Position = Int

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

fun ImageView.loadGlide(@DrawableRes drawable: Int) {
    Glide.with(this).load(drawable).into(this)
}

fun ImageView.loadGlide(drawable: Drawable?) {
    Glide.with(this).load(drawable).into(this)
}

fun ImageView.loadGlide(bitmap: Bitmap?, @DrawableRes errorRes: Int) {
    Glide.with(this)
        .load(bitmap)
        .transition(Utils.CROSS_FADE_TRANSITION)
        .placeholder(R.drawable.placeholder)
        .error(errorRes)
        .centerCrop()
        .into(this)
}

fun ImageView.loadGlideUrl(
    url: String?,
    @DrawableRes errorRes: Int = R.drawable.fallback_image,
    errorDrawable: Drawable? = null
) {
    Glide.with(this)
        .load(OkHttpLibraryGlideModule.GlideAuthUrl(url))
        .transition(Utils.CROSS_FADE_TRANSITION)
        .placeholder(R.drawable.placeholder)
        .let { if (errorDrawable == null) it.error(errorRes) else it.error(errorDrawable) }
        .centerCrop()
        .into(this)
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

fun Resources.isNightModeEnabled() = configuration.uiMode.and(Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

fun Activity.setColorStatusBar(appBar: Boolean = false) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        window.statusBarColor = ContextCompat.getColor(this, if (appBar) R.color.appBar else R.color.background)
        window.lightStatusBar(!resources.isNightModeEnabled())
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

fun View.setFileItem(file: File, isGrid: Boolean = false) {

    fileName.text = file.name
    fileFavorite.isVisible = file.isFavorite
    fileDate?.isVisible = file.id != ROOT_ID
    fileDate?.text =
        if (file.deletedAt.isPositive()) {
            file.getDeletedAt().format(context.getString(R.string.allDeletedFilePattern))
        } else {
            file.getLastModifiedAt().format(context.getString(R.string.allLastModifiedFilePattern))
        }

    file.size?.let {
        fileSize?.text = FormatterFileSize.formatShortFileSize(context, it)
        fileSeparator?.isVisible = true
    } ?: run {
        fileSize?.text = ""
        fileSeparator?.isGone = true
    }

    progressLayout.isGone = true

    filePreview.scaleType = ImageView.ScaleType.CENTER

    when {
        file.isFolder() -> {
            val (icon, tint) = file.getFolderIcon()
            if (tint == null) {
                filePreview.loadGlide(icon)
            } else {
                filePreview.loadGlide(context.getTintedDrawable(icon, tint))
            }
        }
        file.isDrive() -> filePreview.loadGlide(context.getTintedDrawable(R.drawable.ic_drive, file.driveColor))
        else -> {
            val fileType = if (file.isFromUploads) file.getFileTypeFromExtension() else file.getFileType()
            val isGraphic = fileType == ConvertedType.IMAGE || fileType == ConvertedType.VIDEO
            when {
                file.hasThumbnail && (isGrid || isGraphic) -> {
                    filePreview.loadGlideUrl(file.thumbnail(), fileType.icon)
                }
                file.isFromUploads && isGraphic -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val bitmap = context.getLocalThumbnail(file)
                        withContext(Dispatchers.Main) {
                            if (filePreview?.isVisible == true && context != null) {
                                filePreview.loadGlide(bitmap, fileType.icon)
                            }
                        }
                    }
                }
                else -> filePreview.loadGlide(fileType.icon)
            }
            filePreview2?.loadGlide(fileType.icon)
            setupFileProgress(file)
        }
    }

    val canReadCategoryOnFile = DriveInfosController.getCategoryRights().canReadCategoryOnFile
    val categories = file.getCategories()
    (categoriesLayout as LinearLayout).apply {
        if (!canReadCategoryOnFile || categories.isEmpty()) {
            isGone = true
        } else {
            forEachIndexed { index, view ->
                with(view as CategoryIconView) {
                    val category = categories.getOrNull(index)
                    if (index < MAX_DISPLAYED_CATEGORIES - 1) {
                        setCategoryIconOrHide(category)
                    } else {
                        setRemainingCategoriesNumber(category, categories.size - MAX_DISPLAYED_CATEGORIES)
                    }
                }
            }
            isVisible = true
        }
    }
}

fun String.isValidUrl(): Boolean = Patterns.WEB_URL.matcher(this).matches()

fun View.setupFileProgress(file: File, containsProgress: Boolean = false) {
    val progress = file.currentProgress

    when {
        !containsProgress && progress == Utils.INDETERMINATE_PROGRESS && file.isPendingOffline(context) -> {
            fileOffline.isGone = true
            fileOfflineProgression.isGone = true
            fileOfflineProgression.isIndeterminate = true
            fileOfflineProgression.isVisible = true
            progressLayout.isVisible = true
        }
        containsProgress && progress in 0..99 -> {
            fileOffline.isGone = true
            if (fileOfflineProgression.isIndeterminate) {
                fileOfflineProgression.isGone = true
                fileOfflineProgression.isIndeterminate = false
            }
            fileOfflineProgression.progress = progress
            fileOfflineProgression.isVisible = true
            progressLayout.isVisible = true
        }
        file.isOfflineFile(context, checkLocalFile = false) -> {
            fileOffline.isVisible = true
            fileOfflineProgression.isGone = true
            progressLayout.isVisible = true
        }
        else -> progressLayout.isGone = true
    }
}

fun View.setUserView(user: User, showChevron: Boolean = true, onItemClicked: (user: User) -> Unit) {
    userName.text = user.displayName
    userEmail.text = user.email
    userAvatar.loadAvatar(user)
    chevron.isVisible = showChevron
    setOnClickListener { onItemClicked(user) }
}

fun Date.startOfTheDay(): Date =
    Calendar.getInstance().apply {
        time = this@startOfTheDay
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }.time

fun Date.endOfTheDay(): Date =
    Calendar.getInstance().apply {
        time = this@endOfTheDay
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
    }.time

fun Date.year(): Int =
    Calendar.getInstance().apply {
        time = this@year
    }.get(Calendar.YEAR)

fun Date.month(): Int =
    Calendar.getInstance().apply {
        time = this@month
    }.get(Calendar.MONTH)

fun Date.day(): Int =
    Calendar.getInstance().apply {
        time = this@day
    }.get(Calendar.DAY_OF_MONTH)

fun Date.hours(): Int =
    Calendar.getInstance().apply {
        time = this@hours
    }.get(Calendar.HOUR_OF_DAY)

fun Date.minutes(): Int =
    Calendar.getInstance().apply {
        time = this@minutes
    }.get(Calendar.MINUTE)

fun ImageView.animateRotation(isDeployed: Boolean = false) {
    val startDeg = if (isDeployed) 0.0f else 90.0f
    val endDeg = if (isDeployed) 90.0f else 0.0f
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
fun <T> Fragment.setBackNavigationResult(key: String, value: T) {
    findNavController().apply {
        previousBackStackEntry?.savedStateHandle?.set(key, value)
        popBackStack()
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

fun String.isEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()

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
        putExtra(OnlyOfficeActivity.ONLYOFFICE_FILENAME_TAG, file.name)
    })
}

private fun Fragment.canNavigate(currentClassName: String? = null): Boolean {
    val className = currentClassName ?: when (val currentDestination = findNavController().currentDestination) {
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
    navigatorExtras: Navigator.Extras? = null,
    currentClassName: String? = null
) {
    if (canNavigate(currentClassName)) findNavController().navigate(resId, args, navOptions, navigatorExtras)
}

fun Fragment.navigateToUploadView(folderId: Int, folderName: String? = null) {
    safeNavigate(
        R.id.uploadInProgressFragment,
        UploadInProgressFragmentArgs(
            folderId = folderId,
            folderName = folderName ?: getString(R.string.uploadInProgressTitle),
        ).toBundle(),
    )
}

fun Drive?.getDriveUsers(): List<DriveUser> = this?.users?.let { categories ->
    return@let DriveInfosController.getUsers(ArrayList(categories.drive + categories.account))
} ?: listOf()

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun Context.getLocalThumbnail(file: File): Bitmap? = withContext(Dispatchers.IO) {
    val fileUri = file.path.toUri()
    val thumbnailSize = 100
    return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                Utils.getRealPathFromExternalStorage(this@getLocalThumbnail, fileUri)
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
                    it.getOrNull(1)?.toLongOrNull()
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
        filePreview.isGone = true
        fileProgression.isVisible = true
        isVisible = true
    } else {
        isGone = true
    }
}

fun Context.shareText(text: String) {
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    ContextCompat.startActivity(this, Intent.createChooser(intent, null), null)
}

fun Context.getTintedDrawable(drawableId: Int, colorString: String): Drawable? {
    return getTintedDrawable(drawableId, colorString.toColorInt())
}

fun Context.getTintedDrawable(drawableId: Int, colorInt: Int): Drawable? {
    return ContextCompat.getDrawable(this, drawableId)
        ?.apply { setTint(colorInt) }
}

fun Category.getName(context: Context): String = when (name) {
    "PREDEF_CAT_BANKING" -> context.getString(R.string.categoryBanking)
    "PREDEF_CAT_BILL" -> context.getString(R.string.categoryBill)
    "PREDEF_CAT_CONTRACT" -> context.getString(R.string.categoryContract)
    "PREDEF_CAT_FORM" -> context.getString(R.string.categoryForm)
    "PREDEF_CAT_HOBBIES" -> context.getString(R.string.categoryHobbies)
    "PREDEF_CAT_ID" -> context.getString(R.string.categoryID)
    "PREDEF_CAT_INSURANCE" -> context.getString(R.string.categoryInsurance)
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
    return where().equalTo(FileCategory::id.name, id).findFirst()
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

/**
 * This method is here, and not directly a class method in the File class, because of a supposed Realm bug.
 * When we try to put it in the File class, the app doesn't build anymore, because of a "broken method".
 * This is not the only method in this case, search this comment in the project, and you'll see.
 * Realm's Github issue: https://github.com/realm/realm-java/issues/7637
 */
fun File.getFolderIcon(): Pair<Int, String?> {
    return when (getVisibilityType()) {
        VisibilityType.IS_TEAM_SPACE -> R.drawable.ic_folder_common_documents to null
        VisibilityType.IS_SHARED_SPACE -> R.drawable.ic_folder_shared to null
        VisibilityType.IS_COLLABORATIVE_FOLDER -> R.drawable.ic_folder_dropbox_tintable to color
        else -> {
            if (isDisabled()) {
                R.drawable.ic_folder_disable to null
            } else {
                R.drawable.ic_folder_filled_tintable to color
            }
        }
    }
}
