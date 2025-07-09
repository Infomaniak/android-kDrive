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

    enum class MatomoCategory(name: String) {
        Account("account"),
        Categories("categories"),
        FileAction("fileAction"),
        PublicShareAction("publicShareAction"),
        PdfActivityAction("pdfActivityAction"),
        ShareAndRights("shareAndRights"),
        NewElement("newElement"),
        Trash("trash"),
        InAppUpdate("inAppUpdate"),
        InAppReview("inAppReview"),
        DeepLink("deepLink"),
        MyKSuite("myKSuite"),
        MyKSuiteUpgradeBottomSheet("myKSuiteUpgradeBottomSheet"),
        Drive("drive"),
        MediaPlayer("mediaPlayer"),
        SyncModal("syncModal"),
        Dropbox("dropbox"),
        DisplayStyle("displayStyle"),
        Search("search"),
        Comment("comment"),
        PicturesFileAction("picturesFileAction"),
        FileListFileAction("fileListFileAction"),
        TrashFileAction("trashFileAction"),
        FavoritesFileAction("favoritesFileAction"),
        MySharesFileAction("mySharesFileAction"),
        OfflineFileAction("offlineFileAction"),
        TrashFilerecentChangesFileActionAction("trashFilerecentChangesFileActionAction"),
        Settings("settings"),
        PhotoSync("photoSync"),
        Preview("preview"),
    }

    enum class MatomoName(name: String) {
        OpenLoginWebview("openLoginWebview"),
        OpenCreationWebview("openCreationWebview"),
        LoggedIn("loggedIn"),
        Switch("switch"),
        Add("add"),
        LogOut("logOut"),
        LogOutConfirm("logOutConfirm"),
        DeleteAccount("deleteAccount"),
        SwitchDoubleTap("switchDoubleTap"),
        Edit("edit"),
        Delete("delete"),
        Assign("assign"),
        Remove("remove"),
        PrintPdf("printPdf"),
        OpenWith("openWith"),
        OpenBookmark("openBookmark"),
        SendFileCopy("sendFileCopy"),
        Download("download"),
        Favorite("favorite"),
        CancelExternalImport("cancelExternalImport"),
        ConvertToDropbox("convertToDropbox"),
        ShareLink("shareLink"),
        Offline("offline"),
        Move("move"),
        Copy("copy"),
        StopShare("stopShare"),
        Rename("rename"),
        PutInTrash("putInTrash"),
        SaveToKDrive("saveToKDrive"),
        DownloadAllFiles("downloadAllFiles"),
        CreateAccountAd("createAccountAd"),
        BulkSaveToKDrive("bulkSaveToKDrive"),
        OpenInBrowser("openInBrowser"),
        InviteUser("inviteUser"),
        DeleteUser("deleteUser"),
        ProtectWithPassword("protectWithPassword"),
        ExpirationDateLink("expirationDateLink"),
        DownloadFromLink("downloadFromLink"),
        ShareButton("shareButton"),
        RestrictedShareLink("restrictedShareLink"),
        PublicShareLink("publicShareLink"),
        CreateDropbox("createDropbox"),
        UploadFile("uploadFile"),
        CreateCommonFolder("createCommonFolder"),
        CreatePrivateFolder("createPrivateFolder"),
        CreateFolderOnTheFly("createFolderOnTheFly"),
        RestoreGivenFolder("restoreGivenFolder"),
        RestoreOriginFolder("restoreOriginFolder"),
        DeleteFromTrash("deleteFromTrash"),
        EmptyTrash("emptyTrash"),
        DiscoverNow("discoverNow"),
        DiscoverLater("discoverLater"),
        InstallUpdate("installUpdate"),
        PresentAlert("presentAlert"),
        Like("like"),
        Dislike("dislike"),
        Internal("internal"),
        PublicShareWithPassword("publicShareWithPassword"),
        PublicShareExpired("publicShareExpired"),
        PublicShare("publicShare"),
        TryAddingFileWithDriveFull("tryAddingFileWithDriveFull"),
        OpenDashboard("openDashboard"),
        NotEnoughStorageUpgrade("notEnoughStorageUpgrade"),
        ToggleFullScreen("toggleFullScreen"),
        Duration("duration"),
        Play("play"),
        Pause("pause"),
        Configure("configure"),
        ConvertToFolder("convertToFolder"),
        SaveDropbox("saveDropbox"),
        ChangeLimitStorage("changeLimitStorage"),
        SwitchEmailOnFileImport("switchEmailOnFileImport"),
        SwitchProtectWithPassword("switchProtectWithPassword"),
        SwitchExpirationDate("switchExpirationDate"),
        SwitchLimitStorageSpace("switchLimitStorageSpace"),
        ViewList("viewList"),
        ViewGrid("viewGrid"),
        FilterDate("filterDate"),
        FilterFileType("filterFileType"),
        FilterCategory("filterCategory"),
        Update("update"),
        Bulk("bulk"),
        BulkDownload("bulkDownload"),
        BulkSingle("bulkSingle"),
        OnlyWifiTransfer("onlyWifiTransfer"),
        LockApp("lockApp"),
        Theme("theme"),
        Feedback("feedback"),
        DeleteAfterImport("deleteAfterImport"),
        CreateDatedFolders("createDatedFolders"),
        ImportVideo("importVideo"),
        SyncNew("syncNew"),
        SyncAll("syncAll"),
        SyncFromDate("syncFromDate"),
        Enabled("enabled"),
        Disabled("disabled"),
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
        trackEvent(category.toString(), name.toString(), action, value)
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
        context?.trackEvent(MatomoCategory.ShareAndRights.toString(), name, action, value)
    }

    fun Context.trackShareRightsEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.ShareAndRights, name, action, value)
    }

    fun Fragment.trackNewElementEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        context?.trackNewElementEvent(name, action, value)
    }

    fun Fragment.trackNewElementEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        context?.trackEvent(MatomoCategory.NewElement.toString(), name, action, value)
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
            (if (modifiedFileNumber == 1) MatomoName.BulkSingle.toString() else MatomoName.Bulk.toString()) + action.toMatomoString()
        trackEvent(category, name, value = modifiedFileNumber.toFloat())
    }

    fun Fragment.trackMediaPlayerEvent(name: MatomoName, value: Float? = null) {
        trackEvent(MatomoCategory.MediaPlayer, name, value = value)
    }

    fun Fragment.trackSettingsEvent(name: MatomoName, value: Boolean? = null) {
        trackEvent(MatomoCategory.Settings, name, value = value?.toFloat())
    }

    fun Fragment.trackSettingsEvent(name: String, value: Boolean? = null) {
        trackEvent(MatomoCategory.Settings.toString(), name, value = value?.toFloat())
    }

    fun Context.trackPhotoSyncEvent(name: MatomoName, value: Boolean? = null) {
        trackEvent(MatomoCategory.PhotoSync, name, value = value?.toFloat())
    }
}
