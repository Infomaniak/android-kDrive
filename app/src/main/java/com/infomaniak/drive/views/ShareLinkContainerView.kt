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
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat.startActivity
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
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

    private lateinit var shareLinkListener: ShareLinkListener

    interface ShareLinkListener {
        fun onTitleClicked(shareLink: ShareLink?, currentFileId: Int)
        fun onSettingsClicked(shareLink: ShareLink, currentFile: File)
    }

    fun init(listener: ShareLinkListener) {
        shareLinkListener = listener
    }

    fun setup(shareLink: ShareLink?, file: File) {
        currentFile = file
        this.shareLink = shareLink
        visibility = VISIBLE
        urlValue = file.shareLink ?: ""

        updateUi()

        titleContainer.setOnClickListener {
            shareLinkListener.onTitleClicked(this.shareLink, currentFile.id) // cannot be null, if null, settings will not appear
        }

        shareLinkSettings.setOnClickListener {
            // cannot be null, if null, settings will not appear
            shareLinkListener.onSettingsClicked(this.shareLink!!, currentFile)
        }

        shareLinkButton.setOnClickListener {
            this.shareLink?.url?.let {
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, it)
                    type = "text/plain"
                }
                startActivity(context, Intent.createChooser(intent, null), null)
            }
        }
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
            shareLinkStatus.setText(R.string.shareLinkRestrictedRightDescription)
            shareLinkBottomContainer.visibility = View.GONE
        } else {
            shareLinkIcon.setImageResource(R.drawable.ic_unlock)
            shareLinkTitle.setText(R.string.publicSharedLinkTitle)
            shareLinkStatus.setText(R.string.shareLinkPublicRightDescription)
            shareLinkBottomContainer.visibility = View.VISIBLE
        }
    }
}
