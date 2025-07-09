/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
package com.infomaniak.drive.ui.dropbox

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
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.toFloat
import com.infomaniak.drive.MatomoDrive.trackDropboxEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.DropBox
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.FragmentManageDropboxBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.showOrHideEmptyError
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.hideProgressCatching
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.showProgressCatching
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date
import java.util.Locale

open class ManageDropboxFragment : Fragment() {

    protected var binding: FragmentManageDropboxBinding by safeBinding()

    protected val dropboxViewModel: DropboxViewModel by activityViewModels()

    protected open var isManageDropBox = true

    private val navigationArgs: ManageDropboxFragmentArgs by navArgs()

    private var currentDropBox: DropBox? = null
    private var validationCount = 0
    private var hasErrors = false
    private var needNewPassword = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentManageDropboxBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        fileShareCollapsingToolbarLayout.title = getString(R.string.manageDropboxTitle, navigationArgs.fileName)

        shareLinkContainer.binding.apply {
            shareLinkTitle.text = getString(R.string.dropBoxLinkTitle)
            shareLinkUrl.isVisible = true
            shareLinkIcon.isGone = true
            shareLinkStatus.isGone = true
            shareLinkSwitch.isGone = true
        }

        disableButton.isEnabled = false
        saveButton.isEnabled = false

        FileController.getFileById(navigationArgs.fileId)?.let { file ->
            disableButton.isEnabled = true

            if (isManageDropBox) {
                file.dropbox?.url?.let { url -> shareLinkContainer.binding.shareLinkUrl.setUrl(url) }

                dropboxViewModel.getDropBox(file).observe(viewLifecycleOwner) { apiResponse ->
                    apiResponse.data?.let { updateUi(file, it) } ?: run {
                        findNavController().popBackStack()
                        showSnackbar(apiResponse.translateError())
                    }
                }
            }
        }

        binding.root.enableEdgeToEdge(shouldConsumeInsets = true)
    }

    protected fun updateUi(file: File, dropBox: DropBox? = null) = with(binding.settings) {
        currentDropBox = dropBox?.apply { initLocalValue() }

        dropBox?.let {

            setupSwitches(dropBox)

            dropBox.limitFileSize?.let { size ->
                DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT))
                    .format(Utils.convertBytesToGigaBytes(size))
                    .also { limitStorageValue.setText(it) }
            }

            if (dropBox.hasPassword) {
                newPasswordButton.isVisible = true
                needNewPassword = true
            }

            expirationDateInput.init(fragmentManager = parentFragmentManager, dropBox.validUntil ?: Date()) {
                currentDropBox?.newValidUntil = Date(it)
                if (validationCount <= 0) validationCount++
                enableSaveButton()
            }
        }

        setupOnCheckedChangeListeners(dropBox)
        limitStorageValue.addTextChangedListener { limitStorageChanged(it) }
        setupDisableButton(file)
        setupSaveButton(file)
    }

    private fun setupSwitches(dropBox: DropBox) = with(binding.settings) {
        with(dropBox) {
            emailWhenFinishedSwitch.isChecked = hasNotification
            expirationDateSwitch.isChecked = validUntil != null
            limitStorageSwitch.isChecked = limitFileSize != null
            passwordSwitch.isChecked = hasPassword
        }

        if (expirationDateSwitch.isChecked) expirationDateInput.isVisible = true
        if (limitStorageSwitch.isChecked) {
            limitStorageValueLayout.isVisible = true
            limitStorageValueUnit.isVisible = true
        }
    }

    private fun setupOnCheckedChangeListeners(dropBox: DropBox?) = with(binding.settings) {
        emailWhenFinishedSwitch.setOnCheckedChangeListener { _, isChecked -> emailSwitched(dropBox, isChecked) }
        passwordSwitch.setOnCheckedChangeListener { _, isChecked -> passwordSwitched(dropBox, isChecked) }
        expirationDateSwitch.setOnCheckedChangeListener { _, isChecked -> expirationDateSwitched(dropBox, isChecked) }
        limitStorageSwitch.setOnCheckedChangeListener { _, isChecked -> limitStorageSwitched(dropBox, isChecked) }
        newPasswordButton.setOnClickListener {
            passwordTextLayout.isVisible = true
            newPasswordButton.isGone = true
            needNewPassword = false
        }
    }

    private fun setupDisableButton(file: File) = with(binding) {
        disableButton.apply {
            initProgress(this@ManageDropboxFragment)
            setOnClickListener {
                trackDropboxEvent(MatomoName.ConvertToDropbox)
                showProgressCatching(ContextCompat.getColor(requireContext(), R.color.title))
                dropboxViewModel.deleteDropBox(file).observe(viewLifecycleOwner) { apiResponse ->
                    if (apiResponse.isSuccess()) {
                        findNavController().popBackStack()
                    } else {
                        showSnackbar(R.string.errorDelete)
                    }
                    hideProgressCatching(R.string.buttonDisableDropBox)
                }
            }
        }
    }

    private fun setupSaveButton(file: File) = with(binding) {
        saveButton.apply {
            initProgress(this@ManageDropboxFragment)
            setOnClickListener {
                trackDropboxEvent(MatomoName.SaveDropbox)
                currentDropBox?.newPasswordValue = settings.passwordTextInput.text?.toString()
                currentDropBox?.newLimitFileSize = if (settings.limitStorageSwitch.isChecked) {
                    settings.limitStorageValue.text?.toString()?.toDoubleOrNull()?.let { Utils.convertGigaByteToBytes(it) }
                } else {
                    null
                }
                currentDropBox?.let {
                    showProgressCatching()
                    dropboxViewModel.updateDropBox(file, it).observe(viewLifecycleOwner) { apiResponse ->
                        if (apiResponse.isSuccess()) {
                            findNavController().popBackStack()
                        } else {
                            showSnackbar(R.string.errorModification)
                        }
                        hideProgressCatching(R.string.buttonSave)
                    }
                }
            }
        }
    }

    private fun limitStorageChanged(it: Editable?) = with(binding.settings) {
        val inputtedSizeGb = it.toString().toDoubleOrNull()
        if (limitStorageSwitch.isChecked && (it.toString().isBlank() || inputtedSizeGb == 0.0 || inputtedSizeGb == null)) {
            hasErrors = currentDropBox?.limitFileSize != null
            limitStorageValueLayout.error = when {
                inputtedSizeGb == 0.0 -> getString(R.string.createDropBoxLimitFileSizeError)
                it.toString().isBlank() -> getString(R.string.allEmptyInputError)
                else -> ""
            }
        } else {
            val newSize = it.toString().toDouble()
            trackDropboxEvent(MatomoName.ChangeLimitStorage, TrackerAction.INPUT, newSize.toFloat())
            if (Utils.convertGigaByteToBytes(newSize) != currentDropBox?.limitFileSize && validationCount == 0) validationCount++
            limitStorageValue.showOrHideEmptyError()
            hasErrors = false
        }
        enableSaveButton()
    }

    private fun emailSwitched(dropBox: DropBox?, isChecked: Boolean) {
        trackDropboxEvent(MatomoName.SwitchEmailOnFileImport, value = isChecked.toFloat())

        if (dropBox?.hasNotification == isChecked) validationCount-- else validationCount++
        currentDropBox?.newHasNotification = isChecked
        enableSaveButton()
    }

    private fun passwordSwitched(dropBox: DropBox?, isChecked: Boolean) = with(binding.settings) {
        trackDropboxEvent(MatomoName.SwitchProtectWithPassword, value = isChecked.toFloat())

        if (dropBox?.hasPassword == isChecked) validationCount-- else validationCount++

        (if (needNewPassword) newPasswordButton else passwordTextLayout).apply { isVisible = isChecked }

        currentDropBox?.newPassword = isChecked

        enableSaveButton()
    }

    private fun expirationDateSwitched(dropBox: DropBox?, isChecked: Boolean) {
        trackDropboxEvent(MatomoName.SwitchExpirationDate, value = isChecked.toFloat())

        if ((dropBox?.validUntil != null) == isChecked) validationCount-- else validationCount++

        binding.settings.expirationDateInput.isVisible = isChecked

        currentDropBox?.newValidUntil =
            if (isChecked) currentDropBox?.validUntil ?: Date()
            else null

        enableSaveButton()
    }

    private fun limitStorageSwitched(dropBox: DropBox?, isChecked: Boolean) = with(binding.settings) {
        trackDropboxEvent(MatomoName.SwitchLimitStorageSpace, value = isChecked.toFloat())

        if ((dropBox?.limitFileSize != null) == isChecked) validationCount-- else validationCount++
        limitStorageValueLayout.isVisible = isChecked
        limitStorageValueUnit.isVisible = isChecked
        enableSaveButton()
    }

    protected fun enableSaveButton() {
        binding.saveButton.isEnabled = !isManageDropBox || (validationCount > 0 && !hasErrors)
    }
}
