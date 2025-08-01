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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.core.observe
import com.infomaniak.drive.databinding.FragmentBottomSheetCrossLoginBinding
import com.infomaniak.drive.ui.login.CrossAppLoginViewModel
import com.infomaniak.lib.core.utils.safeBinding

class CrossLoginBottomSheetDialog : BottomSheetDialogFragment() {

    private var binding: FragmentBottomSheetCrossLoginBinding by safeBinding()
    private val crossAppLoginViewModel: CrossAppLoginViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetCrossLoginBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeCrossLoginAccounts()
        observeCrossLoginSelectedIds()
        setCrossLoginClicksListeners()
    }

    private fun observeCrossLoginAccounts() {
        crossAppLoginViewModel.availableAccounts.observe(viewLifecycleOwner) { accounts ->
            binding.crossLoginBottomSheet.setAccounts(accounts)
        }
    }

    private fun observeCrossLoginSelectedIds() {
        crossAppLoginViewModel.skippedAccountIds.observe(viewLifecycleOwner) { ids ->
            binding.crossLoginBottomSheet.setSkippedIds(ids)
        }
    }

    private fun setCrossLoginClicksListeners() {

        binding.crossLoginBottomSheet.setOnAnotherAccountClickedListener {
            parentFragmentManager.setFragmentResult(
                /* requestKey = */ ON_ANOTHER_ACCOUNT_CLICKED_KEY,
                /* result = */ Bundle().apply { putString(ON_ANOTHER_ACCOUNT_CLICKED_KEY, "") },
            )
            dismiss()
        }

        binding.crossLoginBottomSheet.setOnSaveClickedListener { skippedAccountIds ->
            crossAppLoginViewModel.skippedAccountIds.value = skippedAccountIds
            dismiss()
        }
    }

    companion object {
        const val ON_ANOTHER_ACCOUNT_CLICKED_KEY = "onAnotherAccountClickedKey"
    }
}
