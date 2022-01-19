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
package com.infomaniak.drive.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.utils.shareText
import com.infomaniak.lib.core.utils.format
import kotlinx.android.synthetic.main.view_share_link_container.view.*

class ShareLinkContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var shareLink: ShareLink? = null
    private var urlValue: String = ""
    private lateinit var currentFile: File

    init {
        inflate(context, R.layout.view_share_link_container, this)
    }

    fun setup(
        file: File,
        shareLink: ShareLink? = null,
        onTitleClicked: ((shareLink: ShareLink?, currentFileId: Int) -> Unit)? = null,
        onSettingsClicked: ((shareLink: ShareLink, currentFile: File) -> Unit)? = null,
    ) {
        currentFile = file
        this.shareLink = shareLink
        isVisible = true
        urlValue = file.shareLink ?: ""

        if (file.isDropBox()) {
            setDropBoxUi()
            return
        }

        updateUi()

        titleContainer.setOnClickListener { onTitleClicked?.invoke(this.shareLink, currentFile.id) }
        // cannot be null, if null, settings will not appear
        shareLinkSettings.setOnClickListener { onSettingsClicked?.invoke(this.shareLink!!, currentFile) }
        shareLinkButton.setOnClickListener { this.shareLink?.url?.let(context::shareText) }
    }

    private fun setDropBoxUi() {
        shareLinkIcon.setImageResource(R.drawable.ic_folder_dropbox)
        shareLinkTitle.setText(R.string.dropboxSharedLinkTitle)
        shareLinkSwitch.isGone = true
        shareLinkBottomContainer.isGone = true
        shareLinkStatus.setText(R.string.dropboxSharedLinkDescription)
    }

    fun update(shareLink: ShareLink? = null) {
        this.shareLink = shareLink
        if (shareLink == null) urlValue = ""
        updateUi()
    }

    private fun updateUi() {
        if (shareLink == null && urlValue.isBlank()) {
            shareLinkIcon.setImageResource(R.drawable.ic_lock)
            shareLinkTitle.setText(R.string.restrictedSharedLinkTitle)
            shareLinkBottomContainer.isGone = true
            shareLinkStatus.text =
                context.getString(R.string.shareLinkRestrictedRightDescription, currentFile.getTypeName(context))
        } else {
            shareLinkIcon.setImageResource(R.drawable.ic_unlock)
            shareLinkTitle.setText(R.string.publicSharedLinkTitle)
            shareLinkBottomContainer.isVisible = true
            shareLinkStatus.text = getShareLinkPublicRightDescription()
        }
    }

    private fun getShareLinkPublicRightDescription(): String {

        val resId = R.string.shareLinkPublicRightDescription

        val permission = context.getString(
            if (shareLink?.canEdit == true) R.string.shareLinkOfficePermissionWriteTitle
            else R.string.shareLinkOfficePermissionReadTitle
        ).lowercase()

        val fileName = currentFile.getTypeName(context)

        val password = if (shareLink?.permission == ShareLink.ShareLinkFilePermission.PASSWORD) {
            context.getString(R.string.shareLinkPublicRightDescriptionPassword)
        } else {
            ""
        }

        val validityDate = if (shareLink?.validUntil != null) {
            context.getString(R.string.shareLinkPublicRightDescriptionDate, shareLink?.validUntil?.format(formatFullDate))
        } else {
            ""
        }

        return context.getString(resId, permission, fileName, password, validityDate)
    }

    private fun File.getTypeName(context: Context): String = context.getString(getTypeName(isFolder(), onlyoffice))

    companion object {

        const val formatFullDate = "dd MMM yyyy - HH:mm"

        fun getTypeName(isFolder: Boolean, isOnlyOffice: Boolean): Int {
            return when {
                isFolder -> R.string.shareLinkTypeFolder
                isOnlyOffice -> R.string.shareLinkTypeDocument
                else -> R.string.shareLinkTypeFile
            }
        }
    }
}
