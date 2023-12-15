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
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.DialogFragmentPasswordBinding
import com.infomaniak.lib.core.utils.showKeyboard

class PasswordDialogFragment : DialogFragment() {

    private val binding by lazy { DialogFragmentPasswordBinding.inflate(layoutInflater) }

    var onPasswordEntered: ((password: String) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        initPasswordField()

        return MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyle)
            .setTitle(R.string.pdfIsLocked)
            //Here we set the listener to null because otherwise, the dialog is dismissed automatically when we press the button
            .setPositiveButton(R.string.buttonValid, null)
            .setNegativeButton(R.string.buttonCancel) { _, _ -> }
            .setView(binding.root)
            .create()
    }

    override fun onStart() {
        super.onStart()
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { sendPassword() }
        binding.passwordEditText.requestFocus()
        dialog?.showKeyboard()
    }

    override fun onDetach() {
        // This is to avoid a crash if the user display the dialog again
        (binding.root.parent as ViewGroup).removeView(binding.root)
        binding.passwordEditText.text?.clear()
        super.onDetach()
    }

    fun onWrongPasswordEntered() {
        with(binding) {
            passwordEditText.text?.clear()
            passwordLayout.apply {
                isErrorEnabled = true
                error = getString(R.string.wrongPdfPassword)
            }
        }
    }

    private fun initPasswordField() {
        with(binding.passwordEditText) {
            addTextChangedListener {
                with(binding) {
                    passwordLayout.isErrorEnabled = false
                    passwordEditText.error = null
                }
            }
            handleActionDone(this)
        }
    }

    private fun handleActionDone(textInputEditText: TextInputEditText) {
        textInputEditText.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    sendPassword()
                    true
                }
                else -> false
            }
        }
    }

    private fun sendPassword() {
        onPasswordEntered?.invoke(binding.passwordEditText.text.toString())
    }
}
