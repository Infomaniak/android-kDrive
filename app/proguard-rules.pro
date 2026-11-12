# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Navigation resolves custom app:argType values by class name at runtime.
-keepnames class com.infomaniak.core.ksuite.data.KSuite
-keepnames class com.infomaniak.core.ksuite.myksuite.ui.screens.MyKSuiteDashboardScreenData
-keepnames class com.infomaniak.drive.data.models.DriveUser
-keepnames class com.infomaniak.drive.data.models.ExtensionType
-keepnames class com.infomaniak.drive.data.models.File$SortType
-keepnames class com.infomaniak.drive.data.models.File$SortTypeUsage
-keepnames class com.infomaniak.drive.data.models.Permission
-keepnames class com.infomaniak.drive.data.models.SearchDateFilter
-keepnames class com.infomaniak.drive.data.models.ShareLink
-keepnames class com.infomaniak.drive.data.models.Shareable
-keepnames class com.infomaniak.drive.data.models.UserDrive
-keepnames class com.infomaniak.drive.data.models.deeplink.DeeplinkExternalFilePath
-keepnames class com.infomaniak.drive.data.models.deeplink.DeeplinkFilePath
-keepnames class com.infomaniak.drive.data.models.deeplink.DeeplinkType
-keepnames class com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog$DownloadAction
-keepnames class com.infomaniak.drive.ui.fileList.FolderNavigationRestrictions
-keepnames class com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode
-keepnames class com.infomaniak.drive.ui.menu.GalleryPeriod
-keepnames class com.infomaniak.drive.ui.selectPermission.SelectPermissionBottomSheetDialog$PermissionsGroup

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
