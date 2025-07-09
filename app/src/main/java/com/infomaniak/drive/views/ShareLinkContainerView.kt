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
package com.infomaniak.drive.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.infomaniak.core.utils.format
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackShareRightsEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes.restrictedShareLink
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.databinding.ViewShareLinkContainerBinding
import com.infomaniak.drive.utils.shareText

class ShareLinkContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var shareLink: ShareLink? = null
    private var urlValue: String = ""
    private lateinit var currentFile: File

    val binding by lazy { ViewShareLinkContainerBinding.inflate(LayoutInflater.from(context), this, true) }

    fun setup(
        file: File,
        shareLink: ShareLink? = null,
        onTitleClicked: ((shareLink: ShareLink?) -> Unit)? = null,
        onSettingsClicked: ((shareLink: ShareLink) -> Unit)? = null,
    ) {
        currentFile = file
        this.shareLink = shareLink
        isVisible = true
        urlValue = file.shareLink?.url ?: ""

        selectUi(file.isDropBox())

        if (!file.isDropBox()) {
            binding.titleContainer.setOnClickListener { onTitleClicked?.invoke(this.shareLink) }
            binding.shareLinkSettings.setOnClickListener {
                this.shareLink?.let { shareLink -> onSettingsClicked?.invoke(shareLink) }
            }
            binding.shareLinkButton.setOnClickListener {
                // if `shareLink` isn't null it means that it's public share. Otherwise we create a private link.
                context.shareText(text = this.shareLink?.url ?: restrictedShareLink(file))
            }
        }
    }

    fun update(shareLink: ShareLink?) {
        this.shareLink = shareLink
        if (shareLink == null) urlValue = ""
        selectUi()
    }

    fun setupMyKSuitePlusChip(canCreateShareLink: Boolean, showMyKSuitePlusAd: () -> Unit) = with(binding) {
        if (this@ShareLinkContainerView.shareLink != null || currentFile.isDropBox()) return@with

        shareLinkMyKSuiteChip.isGone = canCreateShareLink
        titleContainer.isEnabled = canCreateShareLink
        titleContainer.isClickable = canCreateShareLink
        shareLinkSettings.isEnabled = canCreateShareLink
        shareLinkButton.isEnabled = canCreateShareLink

        root.apply {
            if (!canCreateShareLink) setOnClickListener { showMyKSuitePlusAd() } else setOnClickListener(null)
            isClickable = !canCreateShareLink
            isEnabled = !canCreateShareLink
        }
    }

    private fun selectUi(isDropbox: Boolean = false) {
        when {
            isDropbox -> {
                setDropboxUi()
                binding.shareLinkSwitch.isGone = true
            }
            shareLink == null && urlValue.isBlank() -> {
                context?.trackShareRightsEvent(MatomoName.RestrictedShareLink)
                setRestrictedUi()
            }
            else -> {
                context?.trackShareRightsEvent(MatomoName.PublicShareLink)
                setPublicUi()
            }
        }
    }

    private fun setDropboxUi() {
        setUi(
            iconId = R.drawable.ic_folder_dropbox,
            title = context.getString(R.string.dropboxSharedLinkTitle),
            shareButtonVisibility = false,
            shareSettings = false,
            status = context.getString(R.string.dropboxSharedLinkDescription),
        )
        binding.shareLinkSettings.isGone = true
    }

    private fun setRestrictedUi() {
        setUi(
            iconId = R.drawable.ic_lock,
            title = context.getString(R.string.restrictedSharedLinkTitle),
            shareButtonVisibility = true,
            shareSettings = false,
            status = context.getString(R.string.shareLinkRestrictedRightDescription, currentFile.getTypeName(context)),
        )
    }

    private fun setPublicUi() {
        setUi(
            iconId = R.drawable.ic_unlock,
            title = context.getString(R.string.publicSharedLinkTitle),
            shareButtonVisibility = true,
            shareSettings = true,
            status = getShareLinkPublicRightDescription(),
        )
    }

    private fun setUi(iconId: Int, title: String, shareButtonVisibility: Boolean, shareSettings: Boolean, status: String) {
        with(binding) {
            shareLinkIcon.setImageResource(iconId)
            shareLinkTitle.text = title
            shareLinkButton.isVisible = shareButtonVisibility
            shareLinkSettings.isInvisible = !shareSettings
            shareLinkStatus.text = status
        }
    }

    private fun getShareLinkPublicRightDescription(): String {

        val resId = R.string.shareLinkPublicRightDescription

        val permission = context.getString(
            if (shareLink?.capabilities?.canEdit == true) R.string.shareLinkOfficePermissionWriteTitle
            else R.string.shareLinkOfficePermissionReadTitle
        ).lowercase()

        val fileName = currentFile.getTypeName(context)

        val password = if (shareLink?.right == ShareLink.ShareLinkFilePermission.PASSWORD) {
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

    private fun File.getTypeName(context: Context): String = context.getString(getTypeName(isFolder(), hasOnlyoffice))

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
