/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
package com.infomaniak.drive.ui.fileList

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.DropBox
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.showOrHideEmptyError
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import kotlinx.android.synthetic.main.fragment_manage_dropbox.*
import kotlinx.android.synthetic.main.item_dropbox_settings.*
import kotlinx.android.synthetic.main.view_share_link_container.view.*
import java.util.*

open class ManageDropboxFragment : Fragment() {

    private val navigationArgs: ManageDropboxFragmentArgs by navArgs()
    private var currentDropBox: DropBox? = null
    protected val mainViewModel: MainViewModel by activityViewModels()

    private var validationCount = 0
    private var hasErrors = false
    private var needNewPassword = false

    protected open var isManageDropBox = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_manage_dropbox, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        fileShareCollapsingToolbarLayout.title = getString(R.string.manageDropboxTitle, navigationArgs.fileName)

        shareLinkContainer.apply {
            shareLinkTitle.text = getString(R.string.dropBoxLinkTitle)
            shareLinkUrl.isVisible = true
            shareLinkIcon.isGone = true
            titleSeparator.isGone = true
            shareLinkStatus.isGone = true
            shareLinkSwitch.isGone = true
            shareLinkSettings.isGone = true
        }

        disableButton.isEnabled = false
        saveButton.isEnabled = false

        FileController.getFileById(navigationArgs.fileId)?.let { file ->
            disableButton.isEnabled = true

            if (isManageDropBox) {
                file.collaborativeFolder?.let { url -> shareLinkContainer.shareLinkUrl.setUrl(url) }

                mainViewModel.getDropBox(file).observe(viewLifecycleOwner) { apiResponse ->
                    if (apiResponse?.isSuccess() == true) {
                        apiResponse.data?.let { updateUI(file, it) }
                    } else {
                        requireActivity().showSnackbar(apiResponse.translateError())
                    }
                }
            }

        }
    }

    protected fun updateUI(file: File, dropBox: DropBox?) {
        currentDropBox = dropBox?.apply { initLocalValue() }

        dropBox?.let {
            emailWhenFinishedSwitch.isChecked = dropBox.emailWhenFinished
            passwordSwitch.isChecked = dropBox.password
            expirationDateSwitch.isChecked = dropBox.validUntil != null
            limitStorageSwitch.isChecked = dropBox.limitFileSize != null
            dropBox.limitFileSize?.let { size -> limitStorageValue.setText(Utils.convertBytesToGigaBytes(size).toString()) }

            if (dropBox.password) {
                newPasswordButton.isVisible = true
                needNewPassword = true
            }
            if (expirationDateSwitch.isChecked) expirationDateInput.isVisible = true
            if (limitStorageSwitch.isChecked) limitStorageValueLayout.isVisible = true
            if (limitStorageSwitch.isChecked) limitStorageValueUnit.isVisible = true

            expirationDateInput.init(fragmentManager = parentFragmentManager, dropBox.validUntil ?: Date()) {
                currentDropBox?.newValidUntil = Date(it)
                if (validationCount <= 0) validationCount++
                enableSaveButton()
            }
        }

        emailWhenFinishedSwitch.setOnCheckedChangeListener { _, isChecked -> emailSwitched(dropBox, isChecked) }
        passwordSwitch.setOnCheckedChangeListener { _, isChecked -> passwordSwitched(dropBox, isChecked) }
        expirationDateSwitch.setOnCheckedChangeListener { _, isChecked -> expirationDateSwitched(dropBox, isChecked) }
        limitStorageSwitch.setOnCheckedChangeListener { _, isChecked -> limitStorageSwitched(dropBox, isChecked) }
        newPasswordButton.setOnClickListener {
            passwordTextLayout.isVisible = true
            newPasswordButton.isGone = true
            needNewPassword = false
        }

        limitStorageValue.addTextChangedListener { limitStorageChanged(it) }

        disableButton.initProgress(this)
        disableButton.setOnClickListener {
            disableButton.showProgress(ContextCompat.getColor(requireContext(), R.color.title))
            mainViewModel.deleteDropBox(file).observe(viewLifecycleOwner) { apiResponse ->
                if (apiResponse.isSuccess()) {
                    findNavController().popBackStack()
                } else {
                    requireActivity().showSnackbar(R.string.errorDelete)
                }
                disableButton.hideProgress(R.string.buttonDisableDropBox)
            }
        }

        saveButton.initProgress(this)
        saveButton.setOnClickListener {
            currentDropBox?.newPasswordValue = passwordTextInput.text?.toString()
            currentDropBox?.newLimitFileSize =
                if (limitStorageSwitch.isChecked) {
                    limitStorageValue.text?.toString()?.toLongOrNull()?.let { Utils.convertGigaByteToBytes(it) }
                } else null

            currentDropBox?.let {
                saveButton.showProgress()
                mainViewModel.updateDropBox(file, it).observe(viewLifecycleOwner) { apiResponse ->
                    if (apiResponse.isSuccess()) {
                        findNavController().popBackStack()
                    } else {
                        requireActivity().showSnackbar(R.string.errorModification)
                    }
                    saveButton.hideProgress(R.string.buttonSave)
                }
            }
        }
    }

    private fun limitStorageChanged(it: Editable?) {
        if (limitStorageSwitch.isChecked && (it.toString().isBlank() || it.toString().toLong() == 0L)) {
            hasErrors = currentDropBox?.limitFileSize != null
            limitStorageValueLayout.error = when {
                it.toString().toLongOrNull() == 0L -> getString(R.string.createDropBoxLimitFileSizeError)
                it.toString().isBlank() -> getString(R.string.allEmptyInputError)
                else -> ""
            }
        } else {
            val newSize = it.toString().toLong()
            if (Utils.convertGigaByteToBytes(newSize) != currentDropBox?.limitFileSize && validationCount == 0) validationCount++
            limitStorageValue.showOrHideEmptyError()
            hasErrors = false
        }
        enableSaveButton()
    }

    private fun emailSwitched(dropBox: DropBox?, isChecked: Boolean) {
        if (dropBox?.emailWhenFinished == isChecked) validationCount-- else validationCount++
        currentDropBox?.newEmailWhenFinished = isChecked
        enableSaveButton()
    }

    private fun passwordSwitched(dropBox: DropBox?, isChecked: Boolean) {

        if (dropBox?.password == isChecked) validationCount-- else validationCount++

        if (needNewPassword) newPasswordButton else passwordTextLayout
            .apply { isVisible = isChecked }

        currentDropBox?.newPassword = isChecked

        enableSaveButton()
    }

    private fun expirationDateSwitched(dropBox: DropBox?, isChecked: Boolean) {

        if ((dropBox?.validUntil != null) == isChecked) validationCount-- else validationCount++

        expirationDateInput.isVisible = isChecked

        currentDropBox?.newValidUntil =
            if (isChecked) currentDropBox?.validUntil ?: Date()
            else null

        enableSaveButton()
    }

    private fun limitStorageSwitched(dropBox: DropBox?, isChecked: Boolean) {
        if ((dropBox?.limitFileSize != null) == isChecked) validationCount-- else validationCount++
        limitStorageValueLayout.isVisible = isChecked
        limitStorageValueUnit.isVisible = isChecked
        currentDropBox?.withLimitFileSize = isChecked
        enableSaveButton()
    }

    protected fun enableSaveButton() {
        saveButton.isEnabled = !isManageDropBox || (validationCount > 0 && !hasErrors)
    }
}