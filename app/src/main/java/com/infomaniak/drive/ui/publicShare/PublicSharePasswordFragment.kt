/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024-2026 Infomaniak Network SA
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
import com.infomaniak.core.legacy.utils.SnackbarUtils.showSnackbar
import com.infomaniak.core.legacy.utils.hideProgressCatching
import com.infomaniak.core.legacy.utils.initProgress
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.core.legacy.utils.setMargins
import com.infomaniak.core.legacy.utils.showProgressCatching
import com.infomaniak.core.network.models.ApiError
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackPublicShareActionEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.SHARE_URL_V1
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.databinding.FragmentPublicSharePasswordBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.publicShare.PublicShareActivity.Companion.PUBLIC_SHARE_TAG
import com.infomaniak.drive.ui.publicShare.PublicShareListFragment.Companion.PUBLIC_SHARE_DEFAULT_ID
import com.infomaniak.drive.utils.PublicShareUtils
import com.infomaniak.core.network.models.exceptions.NetworkException as ApiControllerNetworkException

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
        observeSubmitPasswordResult()
        observeInitResult()

        binding.root.enableEdgeToEdge(withPadding = true, withBottom = false) {
            binding.passwordValidateButton.setMargins(
                bottom = resources.getDimension(R.dimen.marginStandardMedium).toInt() + it.bottom
            )
        }
    }

    //region Hack TODO: Remove this when the back will support bearer token
    private fun getPublicShareUrl(): String {
        return "${SHARE_URL_V1}/share/${publicShareViewModel.driveId}/${publicShareViewModel.publicShareUuid}"
    }
    //endregion

    private fun setupValidationButton() = with(binding.passwordValidateButton) {
        initProgress(viewLifecycleOwner)
        setOnClickListener {
            if (isFieldBlank()) return@setOnClickListener

            showProgressCatching()
            trackPublicShareActionEvent(MatomoName.ValidatePassword)
            val password = binding.publicSharePasswordEditText.text?.trim().toString()
            publicShareViewModel.submitPublicSharePassword(password)
        }
    }

    private fun observeSubmitPasswordResult() = with(binding) {
        publicShareViewModel.submitPasswordResult.observe(viewLifecycleOwner) { isAuthorized ->
            if (isAuthorized == true) {
                publicShareViewModel.hasBeenAuthenticated = true
                publicShareViewModel.initPublicShare()
            } else {
                passwordValidateButton.hideProgressCatching(R.string.buttonValid)
                publicSharePasswordEditText.text = null
                publicSharePasswordLayout.error = getString(R.string.errorWrongPassword)
            }
        }
    }

    private fun observeInitResult() {
        publicShareViewModel.initPublicShareResult.observe(viewLifecycleOwner) { (errorMessage, shareLink) ->
            errorMessage?.let(::onInitError) ?: onInitSuccess(shareLink)
        }
    }

    private fun onInitSuccess(shareLink: ShareLink?) {
        binding.passwordValidateButton.hideProgressCatching(R.string.buttonValid)
        publicShareViewModel.canDownloadFiles = shareLink?.capabilities?.canDownload == true
        safeNavigate(
            PublicSharePasswordFragmentDirections.actionPublicSharePasswordFragmentToPublicShareListFragment(
                fileId = shareLink?.fileId ?: PUBLIC_SHARE_DEFAULT_ID,
            )
        )
    }

    private fun onInitError(error: ApiError?) = with(binding) {
        passwordValidateButton.hideProgressCatching(R.string.buttonValid)
        val errorRes = if (error?.exception is ApiControllerNetworkException) {
            R.string.errorNetwork
        } else {
            SentryLog.i(PUBLIC_SHARE_TAG, "Download init public share: ${error?.code}")
            R.string.anErrorHasOccurred
        }

        showSnackbar(errorRes, anchor = passwordValidateButton)
    }

    private fun isFieldBlank(): Boolean {
        return binding.publicSharePasswordEditText.text.isNullOrBlank().also { isBlank ->
            if (isBlank) binding.publicSharePasswordLayout.error = getString(R.string.allEmptyInputError)
        }
    }
}
