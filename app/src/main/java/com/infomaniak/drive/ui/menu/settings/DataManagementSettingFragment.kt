/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.drive.ui.menu.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.core.extensions.openUrl
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.drive.databinding.FragmentDataManagementSettingBinding
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.MatomoDrive.SHOW_SOURCE_CODE
import com.infomaniak.drive.MatomoDrive.trackEventDataManagement
import com.infomaniak.drive.extensions.enableEdgeToEdge

class DataManagementSettingFragment : Fragment() {

    private var binding: FragmentDataManagementSettingBinding by safeBinding()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentDataManagementSettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.enableEdgeToEdge()

        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        setupListeners()
    }

    private fun setupListeners() = with(binding) {
        dataManagementMatomo.setOnClickListener {
            safelyNavigate(DataManagementSettingFragmentDirections.actionDataManagementSettingToDataManagementMatomo())
        }
        dataManagementSentry.setOnClickListener {
            safelyNavigate(DataManagementSettingFragmentDirections.actionDataManagementSettingToDataManagementSentry())
        }
        dataManagementSourceCodeButton.setOnClickListener {
            trackEventDataManagement(SHOW_SOURCE_CODE)
            requireContext().openUrl(BuildConfig.GITHUB_REPO_URL)
        }
    }
}
