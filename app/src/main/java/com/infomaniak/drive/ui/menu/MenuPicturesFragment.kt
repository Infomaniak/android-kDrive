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
package com.infomaniak.drive.ui.menu

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.FragmentMenuPicturesBinding
import com.infomaniak.drive.databinding.MultiSelectLayoutBinding
import com.infomaniak.lib.core.utils.Utils.createRefreshTimer

class MenuPicturesFragment : Fragment(), MultiSelectParent {

    private lateinit var binding: FragmentMenuPicturesBinding

    private val picturesFragment: PicturesFragment by lazy {
        PicturesFragment(this@MenuPicturesFragment) {
            timer.cancel()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private val timer: CountDownTimer by lazy {
        createRefreshTimer { binding.swipeRefreshLayout.isRefreshing = true }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMenuPicturesBinding.inflate(inflater, container, false).apply {
            toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
            swipeRefreshLayout.setOnRefreshListener { picturesFragment.onRefreshPictures() }
        }

        binding.multiSelectLayout.apply {
            selectAllButton.isInvisible = true
            setMultiSelectClickListeners()
        }

        return binding.root
    }

    private fun MultiSelectLayoutBinding.setMultiSelectClickListeners() = with(picturesFragment) {
        closeButtonMultiSelect.setOnClickListener { onCloseMultiSelection() }
        moveButtonMultiSelect.setOnClickListener { onMove() }
        deleteButtonMultiSelect.setOnClickListener { onDelete() }
        menuButtonMultiSelect.setOnClickListener { onMenu() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.requestApplyInsets(binding.pictureListCoordinator)

        timer.start()
        if (childFragmentManager.findFragmentByTag("picturesFragment") == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.picturesFragmentView, picturesFragment, "picturesFragment")
                .commit()
        }
    }

    override fun openMultiSelectBar() = with(binding) {
        collapsingToolbarLayout.isGone = true
        multiSelectLayout.root.isVisible = true
    }

    override fun closeMultiSelectBar() = with(binding) {
        collapsingToolbarLayout.isVisible = true
        multiSelectLayout.root.isGone = true
    }

    override fun enableMultiSelectActionButtons() = with(binding.multiSelectLayout) {
        deleteButtonMultiSelect.isEnabled = true
        moveButtonMultiSelect.isEnabled = true
        menuButtonMultiSelect.isEnabled = true
    }

    override fun disableMultiSelectActionButtons() = with(binding.multiSelectLayout) {
        deleteButtonMultiSelect.isEnabled = false
        moveButtonMultiSelect.isEnabled = false
        menuButtonMultiSelect.isEnabled = false
    }

    override fun setTitleMultiSelect(title: String) = with(binding.multiSelectLayout) {
        titleMultiSelect.text = title
    }

    override fun disableSwipeRefresh() = with(binding) {
        swipeRefreshLayout.isEnabled = false
    }

    override fun enableSwipeRefresh() = with(binding) {
        swipeRefreshLayout.isEnabled = true
    }
}
