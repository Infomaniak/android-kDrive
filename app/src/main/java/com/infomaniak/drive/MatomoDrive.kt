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

import android.app.Activity
import android.content.Context
import androidx.fragment.app.Fragment
import com.infomaniak.core.myksuite.ui.utils.MatomoMyKSuite
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.lib.core.MatomoCore
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.capitalizeFirstChar
import org.matomo.sdk.Tracker

object MatomoDrive : MatomoCore {

    override val Context.tracker: Tracker get() = (this as MainApplication).matomoTracker
    override val siteId = 8

    enum class MatomoCategory(val categoryName: String) {
        Account("account"),
        Categories("categories"),
        Comment("comment"),
        DeepLink("deepLink"),
        DisplayStyle("displayStyle"),
        Drive("drive"),
        Dropbox("dropbox"),
        FavoritesFileAction("favoritesFileAction"),
        FileAction("fileAction"),
        FileListFileAction("fileListFileAction"),
        InAppReview("inAppReview"),
        InAppUpdate("inAppUpdate"),
        MediaPlayer("mediaPlayer"),
        MyKSuite("myKSuite"),
        MyKSuiteUpgradeBottomSheet("myKSuiteUpgradeBottomSheet"),
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
        SyncModal("syncModal"),
        Trash("trash"),
        TrashFileAction("trashFileAction"),
    }

    enum class MatomoName(val eventName: String) {
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
        DeleteAccount("deleteAccount"),
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
        LogOut("logOut"),
        LogOutConfirm("logOutConfirm"),
        LoggedIn("loggedIn"),
        Move("move"),
        NotEnoughStorageUpgrade("notEnoughStorageUpgrade"),
        Offline("offline"),
        OnlyWifiTransfer("onlyWifiTransfer"),
        OpenBookmark("openBookmark"),
        OpenCreationWebview("openCreationWebview"),
        OpenDashboard("openDashboard"),
        OpenInBrowser("openInBrowser"),
        OpenLoginWebview("openLoginWebview"),
        OpenWith("openWith"),
        Pause("pause"),
        Play("play"),
        PresentAlert("presentAlert"),
        PreviewArchive("previewArchive"),
        PreviewAudio("previewAudio"),
        PreviewCode("previewCode"),
        PreviewDir("previewDir"),
        PreviewFont("previewFont"),
        PreviewForm("previewForm"),
        PreviewImage("previewImage"),
        PreviewMail("previewMail"),
        PreviewModel("previewModel"),
        PreviewPdf("previewPdf"),
        PreviewPresentation("previewPresentation"),
        PreviewSpreadsheet("previewSpreadsheet"),
        PreviewText("previewText"),
        PreviewUnknown("previewUnknown"),
        PreviewUrl("previewUrl"),
        PreviewVideo("previewVideo"),
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
        Theme("theme"),
        ToggleFullScreen("toggleFullScreen"),
        TryAddingFileWithDriveFull("tryAddingFileWithDriveFull"),
        Update("update"),
        UploadFile("uploadFile"),
        ViewGrid("viewGrid"),
        ViewList("viewList"),
    }

    fun Fragment.trackEvent(
        category: MatomoCategory,
        name: MatomoName,
        action: TrackerAction = TrackerAction.CLICK,
        value: Float? = null
    ) {
        context?.trackEvent(category, name, action, value)
    }

    fun Context.trackEvent(
        category: MatomoCategory,
        name: MatomoName,
        action: TrackerAction = TrackerAction.CLICK,
        value: Float? = null
    ) {
        trackEvent(category.categoryName, name.eventName, action, value)
    }


    fun Fragment.trackCategoriesEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.Categories, name, action, value)
    }

    fun Context.trackFileActionEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.FileAction, name, action, value)
    }

    fun Context.trackPublicShareActionEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.PublicShareAction, name, action, value)
    }

    fun Context.trackPdfActivityActionEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.PdfActivityAction, name, action, value)
    }

    fun Fragment.trackShareRightsEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        context?.trackShareRightsEvent(name, action, value)
    }

    fun Fragment.trackShareRightsEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        context?.trackEvent(MatomoCategory.ShareAndRights.categoryName, name, action, value)
    }

    fun Context.trackShareRightsEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.ShareAndRights, name, action, value)
    }

    fun Fragment.trackNewElementEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        context?.trackNewElementEvent(name, action, value)
    }

    fun Fragment.trackNewElementEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        context?.trackEvent(MatomoCategory.NewElement.categoryName, name, action, value)
    }

    fun Context.trackNewElementEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.NewElement, name, action, value)
    }

    fun Fragment.trackTrashEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.Trash, name, action, value)
    }

    fun Activity.trackInAppUpdate(name: MatomoName) {
        trackEvent(MatomoCategory.InAppUpdate, name)
    }

    fun Activity.trackInAppReview(name: MatomoName) {
        trackEvent(MatomoCategory.InAppReview, name)
    }

    fun Activity.trackDeepLink(name: MatomoName) {
        trackEvent(MatomoCategory.DeepLink, name)
    }

    fun Fragment.trackMyKSuiteEvent(name: String) {
        trackEvent(MatomoMyKSuite.CATEGORY_MY_KSUITE, name)
    }

    fun Context.trackMyKSuiteEvent(name: String) {
        trackEvent(MatomoMyKSuite.CATEGORY_MY_KSUITE, name)
    }

    fun Context.trackMyKSuiteUpgradeBottomSheetEvent(name: String) {
        trackEvent(MatomoMyKSuite.CATEGORY_MY_KSUITE_UPGRADE_BOTTOMSHEET, name)
    }

    fun Fragment.trackDropboxEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.Dropbox, name, action, value)
    }

    fun Fragment.trackSearchEvent(name: MatomoName) {
        trackEvent(MatomoCategory.Search, name)
    }

    fun Fragment.trackCommentEvent(name: MatomoName) {
        trackEvent(MatomoCategory.Comment, name)
    }

    fun Fragment.trackBulkActionEvent(category: String, action: BulkOperationType, modifiedFileNumber: Int) {

        fun BulkOperationType.toMatomoString(): String = name.lowercase().capitalizeFirstChar()

        val name =
            (if (modifiedFileNumber == 1) MatomoName.BulkSingle.eventName else MatomoName.Bulk.eventName) + action.toMatomoString()
        trackEvent(category, name, value = modifiedFileNumber.toFloat())
    }

    fun Fragment.trackMediaPlayerEvent(name: MatomoName, value: Float? = null) {
        trackEvent(MatomoCategory.MediaPlayer, name, value = value)
    }

    fun Fragment.trackSettingsEvent(name: MatomoName, value: Boolean? = null) {
        trackEvent(MatomoCategory.Settings, name, value = value?.toFloat())
    }

    fun Fragment.trackSettingsEvent(name: String, value: Boolean? = null) {
        trackEvent(MatomoCategory.Settings.categoryName, name, value = value?.toFloat())
    }

    fun Context.trackPhotoSyncEvent(name: MatomoName, value: Boolean? = null) {
        trackEvent(MatomoCategory.PhotoSync, name, value = value?.toFloat())
    }
}
