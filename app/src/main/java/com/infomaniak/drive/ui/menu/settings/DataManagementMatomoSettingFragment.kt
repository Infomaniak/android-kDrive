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
import com.infomaniak.drive.MatomoDrive
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.databinding.FragmentDataManagementMatomoSettingBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.lib.core.utils.safeBinding

class DataManagementMatomoSettingFragment : Fragment() {

    private var binding: FragmentDataManagementMatomoSettingBinding by safeBinding()

    private val uiSettings by lazy { UiSettings(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentDataManagementMatomoSettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        root.enableEdgeToEdge()

        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        settingsTrackingSwitchMatomo.isChecked = uiSettings.isMatomoTrackingEnabled

        settingsTrackingMatomo.setOnClickListener {
            settingsTrackingSwitchMatomo.isChecked = !settingsTrackingSwitchMatomo.isChecked
        }
        settingsTrackingSwitchMatomo.setOnCheckedChangeListener { _, isChecked ->
            uiSettings.isMatomoTrackingEnabled = isChecked
            MatomoDrive.shouldOptOut(!isChecked)
        }
    }
}
