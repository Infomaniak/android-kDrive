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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.databinding.FragmentPublicSharePasswordBinding
import com.infomaniak.drive.ui.publicShare.PublicShareActivity.Companion.PUBLIC_SHARE_TAG
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiError
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar

class PublicSharePasswordFragment : Fragment() {

    private var binding: FragmentPublicSharePasswordBinding by safeBinding()
    private val publicShareViewModel: PublicShareViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPublicSharePasswordBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        setupValidationButton()

        publicSharePasswordEditText.addTextChangedListener { publicSharePasswordLayout.error = null }
    }

    private fun setupValidationButton() = with(binding.passwordValidateButton) {
        initProgress(viewLifecycleOwner)
        setOnClickListener {
            if (isFieldBlank()) return@setOnClickListener

            showProgressCatching()
            val password = binding.publicSharePasswordEditText.text?.trim().toString()
            publicShareViewModel.submitPublicSharePassword(password).observe(viewLifecycleOwner) { isAuthorized ->
                if (isAuthorized == true) {
                    publicShareViewModel.hasBeenAuthenticated = true
                    publicShareViewModel.initPublicShare(::onInitSuccess, ::onInitError)
                } else {
                    hideProgressCatching(R.string.buttonValid)
                    binding.publicSharePasswordEditText.text = null
                    binding.publicSharePasswordLayout.error = getString(R.string.wrongPdfPassword)
                }
            }
        }
    }

    private fun onInitSuccess(fileId: Int?) {
        binding.passwordValidateButton.hideProgressCatching(R.string.buttonValid)
        safeNavigate(
            PublicSharePasswordFragmentDirections.actionPublicSharePasswordFragmentToPublicShareListFragment(
                fileId = fileId ?: -1,
            )
        )
    }

    private fun onInitError(error: ApiError?) = with(binding) {
        passwordValidateButton.hideProgressCatching(R.string.buttonValid)
        when {
            error?.exception is ApiController.NetworkException -> {
                showSnackbar(R.string.errorNetwork, anchor = passwordValidateButton)
            }
            error?.code == ErrorCode.PASSWORD_NOT_VALID -> {
                showSnackbar(R.string.wrongPdfPassword, anchor = passwordValidateButton)
            }
            else -> SentryLog.e(PUBLIC_SHARE_TAG, "downloadSharedFile: ${error?.code}")
        }
    }

    private fun isFieldBlank(): Boolean {
        return binding.publicSharePasswordEditText.text.isNullOrBlank().also { isBlank ->
            if (isBlank) binding.publicSharePasswordLayout.error = getString(R.string.allEmptyInputError)
        }
    }
}
