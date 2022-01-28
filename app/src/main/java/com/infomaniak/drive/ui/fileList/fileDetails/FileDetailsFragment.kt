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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.ConvertedType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.Companion.getFolderIcon
import com.infomaniak.drive.utils.TabViewPagerUtils
import com.infomaniak.drive.utils.TabViewPagerUtils.setup
import com.infomaniak.drive.utils.loadGlide
import com.infomaniak.drive.utils.loadGlideUrl
import com.infomaniak.drive.views.CollapsingSubTitleToolbarBehavior
import com.infomaniak.lib.core.utils.format
import kotlinx.android.synthetic.main.empty_icon_layout.view.*
import kotlinx.android.synthetic.main.fragment_file_details.*
import kotlinx.android.synthetic.main.view_subtitle_toolbar.view.*

class FileDetailsFragment : FileDetailsSubFragment() {
    private val navigationArgs: FileDetailsFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_file_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        FileController.getFileById(navigationArgs.fileId, navigationArgs.userDrive)?.let { file ->
            setFile(file)
        }

        mainViewModel.getFileDetails(navigationArgs.fileId, navigationArgs.userDrive)
            .observe(viewLifecycleOwner) { fileResponse ->
                fileResponse?.let { setFile(it) }

                mainViewModel.getFileShare(navigationArgs.fileId).observe(viewLifecycleOwner) { shareResponse ->
                    shareResponse.data?.let { fileDetailsViewModel.currentFileShare.value = it }
                }
            }
    }

    private fun setFile(file: File) {
        fileDetailsViewModel.currentFile.value = file
        subtitleToolbar.title.text = file.name
        subtitleToolbar.subTitle.text = file.getLastModifiedAt().format(getString(R.string.allLastModifiedFilePattern))
        setBannerThumbnail(file)
        setupTabLayout(file.isFolder())
    }

    private fun setBannerThumbnail(file: File) {
        if (file.hasThumbnail) {
            collapsingBackground.loadGlideUrl(file.thumbnail())
        } else {
            appBar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background))
            val params = subtitleToolbar.layoutParams as CoordinatorLayout.LayoutParams
            val titleColor = ContextCompat.getColor(requireContext(), R.color.title)
            val subTitleColor = ContextCompat.getColor(requireContext(), R.color.secondaryText)
            (params.behavior as? CollapsingSubTitleToolbarBehavior)?.setExpandedColor(titleColor, subTitleColor)
            subtitleToolbar.title.setTextColor(titleColor)
            subtitleToolbar.subTitle.setTextColor(subTitleColor)
            toolbar.setNavigationIconTint(titleColor)
            collapsingBackground.isGone = true
            collapsingBackgroundShadow.isGone = true
            noPreviewLayout.isVisible = true
            setNoPreviewIcon(file)
        }
    }

    private fun setNoPreviewIcon(file: File) {
        val fileType = file.getFileType()
        val icon = if (fileType == ConvertedType.FOLDER) {
            file.getFolderIcon(requireContext())
        } else {
            ContextCompat.getDrawable(requireContext(), fileType.icon)
        }
        noPreviewLayout.icon.loadGlide(icon)
    }

    private fun setupTabLayout(isFolder: Boolean) {
        if (tabsViewPager.adapter == null) {
            val tabs = arrayListOf(
                TabViewPagerUtils.FragmentTab(FileDetailsInfoFragment(), R.id.fileInfo),
                TabViewPagerUtils.FragmentTab(FileDetailsActivitiesFragment(), R.id.fileActivities),
            )

            if (!isFolder) {
                fileComments.isVisible = true
                tabs.add(TabViewPagerUtils.FragmentTab(FileDetailsCommentsFragment(), R.id.fileComments))
            }

            setup(tabsViewPager, tabsGroup, tabs)
        }
    }

    override fun onPause() {
        super.onPause()
        // TODO Understand why we need to do this
        toolbar.setNavigationIconTint(ContextCompat.getColor(requireContext(), R.color.primary))
    }
}
