/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.drive.ui.publicShare

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.databinding.FragmentBottomSheetObtainKdriveAdBinding
import com.infomaniak.drive.ui.login.LoginActivity
import com.infomaniak.drive.ui.login.LoginActivityArgs
import com.infomaniak.lib.core.utils.safeBinding

class ObtainKDriveAdBottomSheetDialog : BottomSheetDialogFragment() {

    private var binding: FragmentBottomSheetObtainKdriveAdBinding by safeBinding()
    private val publicShareViewModel: PublicShareViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetObtainKdriveAdBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.freeTrialButton.setOnClickListener { openLoginActivity(shouldLaunchAccountCreation = true) }
        binding.alreadyGotAnAccountButton.setOnClickListener { openLoginActivity(shouldLaunchAccountCreation = false) }
    }

    private fun openLoginActivity(shouldLaunchAccountCreation: Boolean) = with(publicShareViewModel) {
        Intent(requireActivity(), LoginActivity::class.java).apply {
            putExtras(
                LoginActivityArgs(
                    shouldLaunchAccountCreation = shouldLaunchAccountCreation,
                    publicShareDeeplink = "https://kdrive.infomaniak.com/app/share/$driveId/$publicShareUuid",
                ).toBundle()
            )
        }.also(::startActivity)
        findNavController().popBackStack()
    }
}
