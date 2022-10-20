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
package com.infomaniak.drive.ui.menu.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.R
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import kotlinx.android.synthetic.main.fragment_settings_about.*

class AboutSettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        privacyLayout.setOnClickListener {
            requireContext().openUrl(GDPR_URL)
        }

        sourceCodeLayout.setOnClickListener {
            requireContext().openUrl(GITHUB_URL)
        }

        licenseLayout.setOnClickListener {
            requireContext().openUrl(GPL_LICENSE_URL)
        }

        appVersionDescription.text = "v ${BuildConfig.VERSION_NAME} build ${BuildConfig.VERSION_CODE}"
    }

    companion object {
        const val GDPR_URL = "https://infomaniak.com/gtl/rgpd"
        const val GITHUB_URL = "https://github.com/Infomaniak/android-kDrive"
        const val GPL_LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
    }
}
