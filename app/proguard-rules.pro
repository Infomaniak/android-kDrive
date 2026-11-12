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

# Realm instantiates modules and their generated mediators through reflection.
-keep @io.realm.annotations.RealmModule class * {
    <init>();
}
-keep class io.realm.* extends io.realm.internal.RealmProxyMediator {
    <init>();
}

# DynamicRealm migrations use model simple names to look up persisted tables.
-keepnames class com.infomaniak.drive.** extends io.realm.RealmObject

# Core's Room converters use Gson, including fields without @SerializedName.
-keepclassmembers class com.infomaniak.core.auth.models.OrganizationAccount {
    !static !transient <fields>;
}
-keepclassmembers class com.infomaniak.core.auth.models.user.Card {
    !static !transient <fields>;
}
-keepclassmembers class com.infomaniak.core.auth.models.user.CardLink {
    !static !transient <fields>;
}
-keepclassmembers class com.infomaniak.core.auth.models.user.ContactMethod {
    !static !transient <fields>;
}
-keepclassmembers class com.infomaniak.core.auth.models.user.Phone {
    !static !transient <fields>;
}

# Core's token refresh also deserializes ApiToken with Gson.
-keepclassmembers class com.infomaniak.core.login.ApiToken {
    !static !transient <fields>;
}

# Core/Sentry/consumer-rules.pro is not exported by the Core convention plugin.
-keep class com.infomaniak.drive.BuildConfig {
    public static final java.lang.String FLAVOR;
}
-keep class com.google.android.gms.common.GoogleApiAvailability {
    public static com.google.android.gms.common.GoogleApiAvailability getInstance();
    public int isGooglePlayServicesAvailable(android.content.Context);
}

# Paho's LoggerFactory instantiates its logger using a dynamically computed name.
-keep class org.eclipse.paho.client.mqttv3.logging.JSR47Logger {
    public <init>();
}

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
