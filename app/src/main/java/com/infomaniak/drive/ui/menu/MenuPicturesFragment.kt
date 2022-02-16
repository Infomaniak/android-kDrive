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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.FragmentMenuPicturesBinding
import com.infomaniak.lib.core.utils.Utils.createRefreshTimer
import kotlinx.android.synthetic.main.fragment_menu_pictures.*

class MenuPicturesFragment : Fragment(), MultiSelectParent {

    private lateinit var binding: FragmentMenuPicturesBinding

    private val picturesFragment: PicturesFragment by lazy {
        PicturesFragment(this@MenuPicturesFragment) {
            timer.cancel()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private val timer: CountDownTimer by lazy {
        createRefreshTimer { swipeRefreshLayout?.isRefreshing = true }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMenuPicturesBinding.inflate(inflater, container, false)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        binding.apply {
            closeButtonMultiSelect.setOnClickListener { picturesFragment.onCloseMultiSelection() }
            moveButtonMultiSelect.setOnClickListener { picturesFragment.onMove() }
            deleteButtonMultiSelect.setOnClickListener { picturesFragment.onDelete() }
            menuButtonMultiSelect.setOnClickListener { picturesFragment.onMenu() }
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            picturesFragment.onRefreshPictures()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.requestApplyInsets(pictureListCoordinator)

        timer.start()
        if (childFragmentManager.findFragmentByTag("picturesFragment") == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.picturesFragmentView, picturesFragment, "picturesFragment")
                .commit()
        }
    }

    override fun openMultiSelectBar() = with(binding) {
        collapsingToolbarLayout.isGone = true
        multiSelectLayout.isVisible = true
    }

    override fun closeMultiSelectBar() = with(binding) {
        collapsingToolbarLayout.isVisible = true
        multiSelectLayout.isGone = true
    }

    override fun enableMultiSelectActionButtons() = with(binding) {
        deleteButtonMultiSelect.isEnabled = true
        moveButtonMultiSelect.isEnabled = true
        menuButtonMultiSelect.isEnabled = true
    }

    override fun disableMultiSelectActionButtons() = with(binding) {
        deleteButtonMultiSelect.isEnabled = false
        moveButtonMultiSelect.isEnabled = false
        menuButtonMultiSelect.isEnabled = false
    }

    override fun setTitleMultiSelect(title: String) {
        binding.titleMultiSelect.text = title
    }

    override fun disableSwipeRefresh() {
        binding.swipeRefreshLayout.isEnabled = false
    }

    override fun enableSwipeRefresh() {
        binding.swipeRefreshLayout.isEnabled = true
    }
}
