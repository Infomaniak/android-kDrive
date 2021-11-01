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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.loadUrl
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

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        FileController.getFileById(navigationArgs.fileId, navigationArgs.userDrive)?.let { file ->
            setFile(file)
        }

        mainViewModel.getFileDetails(navigationArgs.fileId, navigationArgs.userDrive)
            .observe(viewLifecycleOwner) { fileResponse ->
                fileResponse?.let { file ->
                    setFile(file)
                }
            }

        mainViewModel.getFileShare(navigationArgs.fileId).observe(viewLifecycleOwner) { shareResponse ->
            shareResponse.data?.let { share ->
                fileDetailsViewModel.currentFileShare.value = share
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
            collapsingBackground.loadUrl(file.thumbnail())
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
            noPreviewLayout.icon.load(file.getFileType().icon)
        }
    }

    private fun setupTabLayout(isFolder: Boolean) {
        if (tabsViewPager.adapter == null) {
            val tabs = arrayListOf(
                FileDetailsTab(0, FileDetailsInfosFragment(), R.string.fileDetailsInfosTitle, R.id.fileInfo),
                FileDetailsTab(1, FileDetailsActivitiesFragment(), R.string.fileDetailsActivitiesTitle, R.id.fileActivities),
            )

            if (!isFolder) {
                fileComments.isVisible = true
                tabs.add(
                    FileDetailsTab(
                        position = 2,
                        fragment = FileDetailsCommentsFragment(),
                        title = R.string.fileDetailsCommentsTitle,
                        button = R.id.fileComments
                    )
                )
            }

            tabsViewPager.apply {
                adapter = FileDetailsPagerAdapter(childFragmentManager, lifecycle, tabs)
                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        tabsGroup.check(tabs[position].button)
                    }
                })
            }

            tabsGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    tabsViewPager.setCurrentItem(tabs.find { it.button == checkedId }!!.position, true)
                }
            }
        }
    }

    data class FileDetailsTab(
        val position: Int,
        val fragment: Fragment,
        val title: Int,
        val button: Int
    )

    override fun onPause() {
        super.onPause()
        // TODO Understand why we need to do this
        toolbar.setNavigationIconTint(ContextCompat.getColor(requireContext(), R.color.primary))
    }
}