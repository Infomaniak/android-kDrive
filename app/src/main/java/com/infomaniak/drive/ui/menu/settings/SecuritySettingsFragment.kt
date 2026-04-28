/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.core.applock.AppLockManager
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackSettingsEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.databinding.FragmentSettingsSecurityBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import splitties.init.appCtx

class SecuritySettingsFragment : Fragment() {

    private var binding: FragmentSettingsSecurityBinding by safeBinding()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSettingsSecurityBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        appSecurity.apply {
            if (AppLockManager.hasBiometrics()) {
                isVisible = true
                setOnClickListener {
                    trackSettingsEvent(MatomoName.LockApp)
                    safelyNavigate(R.id.appSecurityActivity)
                }
            } else {
                isGone = true
            }
        }
        contentProviderSwitch.isChecked = !CloudStorageProvider.isDisabled(appCtx)

        contentProviderSwitch.setOnCheckedChangeListener { _, isChecked ->
            CloudStorageProvider.setDisabled(appCtx, disabled = !isChecked)
        }

        binding.root.enableEdgeToEdge()
    }

    override fun onResume() = with(binding) {
        super.onResume()
        appSecurity.endText = getString(if (AppSettings.appSecurityLock) R.string.allActivated else R.string.allDisabled)
        contentProviderSwitch.isChecked = !CloudStorageProvider.isDisabled(appCtx)
    }

}
