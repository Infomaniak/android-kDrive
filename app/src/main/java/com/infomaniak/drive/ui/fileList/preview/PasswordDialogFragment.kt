/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.drive.ui.fileList.preview

import android.app.Dialog
import android.os.Bundle
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.DialogFragmentPasswordBinding

class PasswordDialogFragment : DialogFragment() {

    companion object {
        private const val NAVIGATION_ARG_PASSWORD_KEY = "password"
        private const val NAVIGATION_ARG_IS_CANCELED_KEY = "isCanceled"
    }

    private val binding by lazy { DialogFragmentPasswordBinding.inflate(layoutInflater) }
    private val navigationArgs: PasswordDialogFragmentArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding.validate.setOnClickListener {
            findNavController().apply {
                previousBackStackEntry?.savedStateHandle?.set(
                    NAVIGATION_ARG_PASSWORD_KEY,
                    binding.passwordEditText.text.toString()
                )
            }
        }
        binding.cancel.setOnClickListener {
            findNavController().apply {
                previousBackStackEntry?.savedStateHandle?.set(NAVIGATION_ARG_IS_CANCELED_KEY, true)
                navigateUp()
            }
        }
        binding.passwordEditText.addTextChangedListener {
            binding.passwordTextLayout.isErrorEnabled = false
            binding.passwordTextLayout.error = null
        }

        if (navigationArgs.isWrongPassword) {
            onWrongPasswordEntered()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setCancelable(false)
            .create().apply {
                setCanceledOnTouchOutside(false)
                setCancelable(false)
                return this
            }
    }

    private fun onWrongPasswordEntered() {
        binding.passwordEditText.text?.clear()
        binding.passwordTextLayout.isErrorEnabled = true
        binding.passwordTextLayout.error = getString(R.string.wrongPdfPassword)
    }
}