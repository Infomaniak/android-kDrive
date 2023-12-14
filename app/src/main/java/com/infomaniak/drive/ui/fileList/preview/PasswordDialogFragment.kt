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
import android.view.inputmethod.EditorInfo
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.DialogFragmentPasswordBinding
import com.infomaniak.lib.core.utils.showKeyboard

class PasswordDialogFragment : DialogFragment() {

    private val binding by lazy { DialogFragmentPasswordBinding.inflate(layoutInflater) }

    private val navController by lazy { findNavController() }

    private val navigationArgs: PasswordDialogFragmentArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyle)
            .setTitle(R.string.pdfIsLocked)
            .setPositiveButton(R.string.buttonValid) { _, _ -> sendPassword() }
            .setNegativeButton(R.string.buttonCancel) { _, _ ->
                navController.apply {
                    previousBackStackEntry?.savedStateHandle?.set(NAVIGATION_ARG_IS_CANCELED_KEY, true)
                    navigateUp()
                }
            }
            .setView(binding.root)
            .create()

        binding.passwordEditText.apply {
            addTextChangedListener {
                with(binding) {
                    passwordLayout.isErrorEnabled = false
                    passwordEditText.error = null
                }
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE -> {
                        sendPassword()
                        true
                    }
                    else -> false
                }
            }
        }

        return dialog
    }

    override fun onStart() {
        super.onStart()
        binding.passwordEditText.requestFocus()
        dialog?.showKeyboard()
        if (navigationArgs.isWrongPassword) onWrongPasswordEntered()
    }

    private fun sendPassword() {
        navController.apply {
            previousBackStackEntry?.savedStateHandle?.set(
                NAVIGATION_ARG_PASSWORD_KEY,
                binding.passwordEditText.text.toString()
            )
        }
    }

    private fun onWrongPasswordEntered() {
        with(binding) {
            passwordEditText.text?.clear()
            with(passwordLayout) {
                isErrorEnabled = true
                error = getString(R.string.wrongPdfPassword)
            }
        }
    }

    companion object {
        private const val NAVIGATION_ARG_PASSWORD_KEY = "password"
        private const val NAVIGATION_ARG_IS_CANCELED_KEY = "isCanceled"
    }
}