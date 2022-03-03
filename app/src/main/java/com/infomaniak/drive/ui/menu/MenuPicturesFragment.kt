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
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.FragmentMenuPicturesBinding
import com.infomaniak.drive.databinding.MultiSelectLayoutBinding
import com.infomaniak.lib.core.utils.Utils.createRefreshTimer

class MenuPicturesFragment : Fragment() {

    private lateinit var binding: FragmentMenuPicturesBinding

    private val picturesFragment = PicturesFragment(
        onFinish = {
            timer.cancel()
            binding.swipeRefreshLayout.isRefreshing = false
        },
    )

    private val timer: CountDownTimer by lazy { createRefreshTimer { binding.swipeRefreshLayout.isRefreshing = true } }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMenuPicturesBinding.inflate(inflater, container, false).apply {
            toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
            swipeRefreshLayout.setOnRefreshListener { picturesFragment.onRefreshPictures() }
        }

        binding.multiSelectLayout.apply {
            selectAllButton.isInvisible = true
            setMultiSelectClickListeners()
        }

        picturesFragment.apply {
            menuPicturesBinding = binding
            multiSelectToolbar = binding.collapsingToolbarLayout
        }

        return binding.root
    }

    private fun MultiSelectLayoutBinding.setMultiSelectClickListeners() = with(picturesFragment) {
        closeButtonMultiSelect.setOnClickListener { closeMultiSelect() }
        moveButtonMultiSelect.setOnClickListener { onMoveButtonClicked() }
        deleteButtonMultiSelect.setOnClickListener { deleteFiles() }
        menuButtonMultiSelect.setOnClickListener { onMenuButtonClicked() }
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
}
