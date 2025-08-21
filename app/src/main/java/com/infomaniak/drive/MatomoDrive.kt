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
package com.infomaniak.drive

import androidx.fragment.app.Fragment
import com.infomaniak.core.ksuite.ksuitepro.utils.MatomoKSuitePro
import com.infomaniak.core.ksuite.myksuite.ui.utils.MatomoMyKSuite
import com.infomaniak.core.matomo.Matomo
import com.infomaniak.core.matomo.Matomo.TrackerAction
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.lib.core.utils.capitalizeFirstChar
import org.matomo.sdk.Tracker

object MatomoDrive : Matomo {

    override val tracker: Tracker by lazy(::buildTracker)
    override val siteId = 8

    enum class MatomoCategory(val value: String) {
        Account("account"),
        Categories("categories"),
        ColorFolder("colorFolder"),
        Comment("comment"),
        DeepLink("deepLink"),
        DisplayStyle("displayStyle"),
        Drive("drive"),
        Dropbox("dropbox"),
        FavoritesFileAction("favoritesFileAction"),
        FileAction("fileAction"),
        FileList("fileList"),
        FileListFileAction("fileListFileAction"),
        InAppReview("inAppReview"),
        InAppUpdate("inAppUpdate"),
        MediaPlayer("mediaPlayer"),
        MySharesFileAction("mySharesFileAction"),
        NewElement("newElement"),
        OfflineFileAction("offlineFileAction"),
        PdfActivityAction("pdfActivityAction"),
        PhotoSync("photoSync"),
        PicturesFileAction("picturesFileAction"),
        Preview("preview"),
        PublicShareAction("publicShareAction"),
        RecentChangesFileAction("recentChangesFileAction"),
        Search("search"),
        Settings("settings"),
        ShareAndRights("shareAndRights"),
        SharedWithMeFileAction("sharedWithMeFileAction"),
        Shortcuts("shortcuts"),
        SyncModal("syncModal"),
        Trash("trash"),
        TrashFileAction("trashFileAction"),
        SettingsDataManagement("settingsDataManagement")
    }

    enum class MatomoName(val value: String) {
        Add("add"),
        Assign("assign"),
        Bulk("bulk"),
        BulkDownload("bulkDownload"),
        BulkSaveToKDrive("bulkSaveToKDrive"),
        BulkSingle("bulkSingle"),
        CancelExternalImport("cancelExternalImport"),
        ChangeLimitStorage("changeLimitStorage"),
        Configure("configure"),
        ConvertToDropbox("convertToDropbox"),
        ConvertToFolder("convertToFolder"),
        Copy("copy"),
        CreateAccountAd("createAccountAd"),
        CreateCommonFolder("createCommonFolder"),
        CreateDatedFolders("createDatedFolders"),
        CreateDropbox("createDropbox"),
        CreateFolderOnTheFly("createFolderOnTheFly"),
        CreatePrivateFolder("createPrivateFolder"),
        Delete("delete"),
        DeleteAfterImport("deleteAfterImport"),
        DeleteFromTrash("deleteFromTrash"),
        DeleteUser("deleteUser"),
        Disabled("disabled"),
        DiscoverLater("discoverLater"),
        DiscoverNow("discoverNow"),
        Dislike("dislike"),
        Download("download"),
        DownloadAllFiles("downloadAllFiles"),
        DownloadFromLink("downloadFromLink"),
        Duration("duration"),
        Edit("edit"),
        EmptyTrash("emptyTrash"),
        Enabled("enabled"),
        ExpirationDateLink("expirationDateLink"),
        Favorite("favorite"),
        Feedback("feedback"),
        FilterCategory("filterCategory"),
        FilterDate("filterDate"),
        FilterFileType("filterFileType"),
        ImportVideo("importVideo"),
        InstallUpdate("installUpdate"),
        Internal("internal"),
        InviteUser("inviteUser"),
        Like("like"),
        LockApp("lockApp"),
        LoggedIn("loggedIn"),
        LongPressDirectAccess("longPressDirectAccess"),
        Move("move"),
        Offline("offline"),
        OnlyWifiTransfer("onlyWifiTransfer"),
        OpenBookmark("openBookmark"),
        OpenCreationWebview("openCreationWebview"),
        OpenInBrowser("openInBrowser"),
        OpenLoginWebview("openLoginWebview"),
        OpenWith("openWith"),
        Pause("pause"),
        Play("play"),
        PresentAlert("presentAlert"),
        PrintPdf("printPdf"),
        ProtectWithPassword("protectWithPassword"),
        PublicShare("publicShare"),
        PublicShareExpired("publicShareExpired"),
        PublicShareLink("publicShareLink"),
        PublicShareWithPassword("publicShareWithPassword"),
        PutInTrash("putInTrash"),
        Remove("remove"),
        Rename("rename"),
        RestoreGivenFolder("restoreGivenFolder"),
        RestoreOriginFolder("restoreOriginFolder"),
        RestrictedShareLink("restrictedShareLink"),
        SaveDropbox("saveDropbox"),
        SaveToKDrive("saveToKDrive"),
        SendFileCopy("sendFileCopy"),
        ShareButton("shareButton"),
        ShareLink("shareLink"),
        ShowSourceCode("showSourceCode"),
        StopShare("stopShare"),
        Switch("switch"),
        SwitchDoubleTap("switchDoubleTap"),
        SwitchEmailOnFileImport("switchEmailOnFileImport"),
        SwitchExpirationDate("switchExpirationDate"),
        SwitchLimitStorageSpace("switchLimitStorageSpace"),
        SwitchProtectWithPassword("switchProtectWithPassword"),
        SyncAll("syncAll"),
        SyncFromDate("syncFromDate"),
        SyncNew("syncNew"),
        ToggleFullScreen("toggleFullScreen"),
        TryAddingFileWithDriveFull("tryAddingFileWithDriveFull"),
        Update("update"),
        UploadFile("uploadFile"),
        ViewGrid("viewGrid"),
        ViewList("viewList"),
    }

    //region Track global events
    fun trackEvent(
        category: MatomoCategory,
        name: MatomoName,
        action: TrackerAction = TrackerAction.CLICK,
        value: Float? = null,
    ) {
        trackEvent(category.value, name.value, action, value)
    }
    //endregion

    //region Track specific events
    fun trackAccountEvent(name: MatomoName) {
        trackEvent(MatomoCategory.Account, name)
    }

    fun trackCategoriesEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.Categories, name, action, value)
    }

    fun trackFileActionEvent(name: MatomoName, value: Boolean? = null) {
        trackEvent(MatomoCategory.FileAction, name, value = value?.toFloat())
    }

    fun trackPublicShareActionEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.PublicShareAction, name, action, value)
    }

    fun trackPdfActivityActionEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.PdfActivityAction, name, action, value)
    }

    fun trackShareRightsEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackShareRightsEvent(name.value, action, value)
    }

    fun trackShareRightsEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.ShareAndRights.value, name, action, value)
    }

    fun trackNewElementEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackNewElementEvent(name.value, action, value)
    }

    fun trackNewElementEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.NewElement.value, name, action, value)
    }

    fun trackTrashEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.Trash, name, action, value)
    }

    fun trackInAppUpdate(name: MatomoName) {
        trackEvent(MatomoCategory.InAppUpdate, name)
    }

    fun trackInAppReview(name: MatomoName) {
        trackEvent(MatomoCategory.InAppReview, name)
    }

    fun trackDeepLink(name: MatomoName) {
        trackEvent(MatomoCategory.DeepLink, name)
    }

    fun trackMyKSuiteEvent(name: String) {
        trackEvent(MatomoMyKSuite.CATEGORY_MY_KSUITE, name)
    }

    fun trackKSuiteProBottomSheetEvent(name: String) {
        trackEvent(MatomoKSuitePro.CATEGORY_KSUITE_PRO_BOTTOMSHEET, name)
    }

    fun trackDropboxEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.Dropbox, name, action, value)
    }

    fun trackSearchEvent(name: MatomoName) {
        trackEvent(MatomoCategory.Search, name)
    }

    fun trackCommentEvent(name: MatomoName) {
        trackEvent(MatomoCategory.Comment, name)
    }

    fun trackEventDataManagement(name: MatomoName) {
        trackEvent(MatomoCategory.SettingsDataManagement, name)
    }

    fun trackBulkActionEvent(category: MatomoCategory, action: BulkOperationType, fileCount: Int) {

        fun BulkOperationType.toMatomoString(): String = name.lowercase().capitalizeFirstChar()

        val matomoName = if (fileCount == 1) MatomoName.BulkSingle else MatomoName.Bulk
        val name = matomoName.value + action.toMatomoString()

        trackEvent(category.value, name, value = fileCount.toFloat())
    }

    fun trackMediaPlayerEvent(name: MatomoName, value: Float? = null) {
        trackEvent(MatomoCategory.MediaPlayer, name, value = value)
    }

    fun trackSettingsEvent(name: MatomoName, value: Boolean? = null) {
        trackSettingsEvent(name.value, value = value)
    }

    fun trackSettingsEvent(name: String, value: Boolean? = null) {
        trackEvent(MatomoCategory.Settings.value, name, value = value?.toFloat())
    }

    fun trackPhotoSyncEvent(name: MatomoName, value: Boolean? = null) {
        trackEvent(MatomoCategory.PhotoSync, name, value = value?.toFloat())
    }
    //endregion

    //region Track screens
    fun Fragment.trackScreen() {
        trackScreen(path = this::class.java.name, title = this::class.java.simpleName)
    }
    //endregion

    fun shouldOptOut(shouldOptOut: Boolean) {
        tracker.isOptOut = shouldOptOut
    }
}
