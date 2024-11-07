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
package com.infomaniak.drive.ui.menu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.FragmentMenuGalleryBinding
import com.infomaniak.drive.databinding.MultiSelectLayoutBinding
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.fileList.multiSelect.GalleryMultiSelectActionsBottomSheetDialog
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.toPx

class MenuGalleryFragment : Fragment() {

    private var binding: FragmentMenuGalleryBinding by safeBinding()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMenuGalleryBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.requestApplyInsets(binding.galleryListCoordinator)
        val galleryFragment = addGalleryFragment()
        setUi(galleryFragment)
    }

    private fun addGalleryFragment(): GalleryFragment = with(childFragmentManager) {
        return@with (findFragmentByTag(GalleryFragment.TAG) as? GalleryFragment) ?: run {
            GalleryFragment().also {
                beginTransaction()
                    .replace(R.id.galleryFragmentView, it, GalleryFragment.TAG)
                    .commit()
            }
        }
    }

    private fun setUi(galleryFragment: GalleryFragment) = with(binding) {

        swipeRefreshLayout.setOnRefreshListener(galleryFragment::onRefreshGallery)

        multiSelectLayout.apply {
            selectAllButton.isGone = true
            setMultiSelectClickListeners(galleryFragment)
        }

        galleryFragment.menuGalleryBinding = binding

        appBar.addOnOffsetChangedListener { _, verticalOffset ->
            galleryFragment.setScrollbarTrackOffset(appBar.totalScrollRange + verticalOffset)
        }

        adjustFastScrollBarScrollRange(galleryFragment)
    }

    private fun MultiSelectLayoutBinding.setMultiSelectClickListeners(galleryFragment: GalleryFragment) = with(galleryFragment) {
        toolbarMultiSelect.setNavigationOnClickListener { closeMultiSelect() }
        moveButtonMultiSelect.setOnClickListener { onMoveButtonClicked() }
        deleteButtonMultiSelect.setOnClickListener { deleteFiles() }
        menuButtonMultiSelect.setOnClickListener {
            onMenuButtonClicked(
                multiSelectBottomSheet = GalleryMultiSelectActionsBottomSheetDialog(),
                areAllFromTheSameFolder = false,
            )
        }
    }

    private fun adjustFastScrollBarScrollRange(galleryFragment: GalleryFragment) = with(binding) {
        val bottomNavigationOffset = with((activity as MainActivity).getBottomNavigation()) {
            layoutParams.height + marginBottom + marginTop + 10.toPx()
        }

        appBar.addOnOffsetChangedListener { _, verticalOffset ->
            val margin = appBar.totalScrollRange + verticalOffset + bottomNavigationOffset
            galleryFragment.setScrollbarTrackOffset(margin)
        }
    }
}
