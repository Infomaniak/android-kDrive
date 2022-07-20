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

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.appbar.AppBarLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.TabViewPagerUtils.setup
import com.infomaniak.drive.views.CollapsingSubTitleToolbarBehavior
import com.infomaniak.lib.core.utils.format
import com.infomaniak.lib.core.utils.lightStatusBar
import com.infomaniak.lib.core.utils.toggleEdgeToEdge
import kotlinx.android.synthetic.main.empty_icon_layout.view.*
import kotlinx.android.synthetic.main.fragment_file_details.*
import kotlinx.android.synthetic.main.fragment_file_details.view.*
import kotlinx.android.synthetic.main.view_subtitle_toolbar.view.*
import kotlin.math.abs

class FileDetailsFragment : FileDetailsSubFragment() {
    private val navigationArgs: FileDetailsFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_file_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        FileController.getFileById(fileId, userDrive)?.let(::setFile)

        mainViewModel.getFileDetails(fileId, userDrive).observe(viewLifecycleOwner) { fileResponse ->
            fileResponse?.let(::setFile)

            mainViewModel.getFileShare(fileId).observe(viewLifecycleOwner) { shareResponse ->
                shareResponse.data?.let { fileDetailsViewModel.currentFileShare.value = it }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.window?.apply {
            statusBarColor = Color.TRANSPARENT
            toggleEdgeToEdge(true)

            // Corrects the layout so it still takes into account system bars in edge-to-edge mode
            ViewCompat.setOnApplyWindowInsetsListener(requireView()) { view, windowInsets ->
                with(windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())) {
                    toolbar.setMargin(top = top)
                    view.setMargin(bottom = bottom)
                }

                // Return CONSUMED if you don't want the window insets to keep being passed down to descendant views
                WindowInsetsCompat.CONSUMED
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // TODO Understand why we need to do this
        toolbar.setNavigationIconTint(ContextCompat.getColor(requireContext(), R.color.primary))
    }

    override fun onStop() {
        requireActivity().window.toggleEdgeToEdge(false)
        super.onStop()
    }

    private fun setFile(file: File) {
        fileDetailsViewModel.currentFile.value = file
        requireActivity().window.lightStatusBar(context?.resources?.isNightModeEnabled() == false && !file.hasThumbnail)
        subtitleToolbar.title.text = file.name
        subtitleToolbar.subTitle.text = file.getLastModifiedAt().format(getString(R.string.allLastModifiedFilePattern))
        setBannerThumbnail(file)
        setupTabLayout(file.isFolder())
    }

    private fun setBannerThumbnail(file: File) {
        val params = subtitleToolbar.layoutParams as CoordinatorLayout.LayoutParams
        val collapsingToolbarBehavior = params.behavior as? CollapsingSubTitleToolbarBehavior

        appBar.addOnOffsetChangedListener(object : AppBarStateChangeListener() {
            override fun onStateChanged(state: State) {
                collapsingToolbarBehavior?.apply {
                    isNewState = true
                    isExpanded = state == State.EXPANDED

                    // If in Light mode, change the status icons color to match the background.
                    // If in Dark mode, the icons stay white all along, no need to check.
                    if (context?.resources?.isNightModeEnabled() == false && file.hasThumbnail) activity?.window?.lightStatusBar(!isExpanded)
                }
            }
        })

        if (file.hasThumbnail) {
            collapsingBackground.loadAny(file.thumbnail())
        } else {
            appBar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background))
            val titleColor = ContextCompat.getColor(requireContext(), R.color.title)
            val subTitleColor = ContextCompat.getColor(requireContext(), R.color.secondaryText)
            collapsingToolbarBehavior?.setExpandedColor(titleColor, subTitleColor)
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
        noPreviewLayout.icon.apply {
            if (file.isFolder()) {
                val (icon, tint) = file.getFolderIcon()
                if (tint != null) imageTintList = ColorStateList.valueOf(Color.parseColor(tint))
                setImageResource(icon)
            } else {
                setImageResource(file.getFileType().icon)
            }
        }
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

    abstract class AppBarStateChangeListener : AppBarLayout.OnOffsetChangedListener {

        private var currentState = State.EXPANDED

        override fun onOffsetChanged(appBarLayout: AppBarLayout, yOffset: Int) {
            // ScrimVisibleHeightTrigger starts from the top of the appbar and stops at the collapsing threshold
            val appBarCollapsingThreshold =
                appBarLayout.height - appBarLayout.fileDetailsCollapsingToolbar.scrimVisibleHeightTrigger

            currentState = callStateChangeListener(
                if (abs(yOffset) <= appBarCollapsingThreshold) State.EXPANDED else State.COLLAPSED
            )
        }

        private fun callStateChangeListener(state: State): State {
            return state.also { if (currentState != it) onStateChanged(it) }
        }

        abstract fun onStateChanged(state: State)

        enum class State { EXPANDED, COLLAPSED }
    }
}
