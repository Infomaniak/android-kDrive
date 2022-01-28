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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileComment
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.openOnlyOfficeDocument
import com.infomaniak.drive.utils.showSnackbar
import kotlinx.android.synthetic.main.fragment_file_details.*
import kotlinx.android.synthetic.main.fragment_file_details_comments.*

class FileDetailsCommentsFragment : FileDetailsSubFragment() {

    private lateinit var commentsAdapter: FileCommentsAdapter
    private lateinit var currentFile: File

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_file_details_comments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileDetailsViewModel.currentFile.observe(viewLifecycleOwner) { file ->
            currentFile = file
            setupView()
        }
    }

    private fun setupView() {
        val onClickAddCommentButton: (view: View) -> Unit
        if (currentFile.isOnlyOfficePreview()) {
            noCommentsLayout.setup(
                icon = R.drawable.ic_comment,
                title = R.string.fileDetailsCommentsUnavailable,
                initialListView = fileCommentsRecyclerView,
                secondaryBackground = true
            )
            noCommentsLayout.toggleVisibility(isVisible = true)
            onClickAddCommentButton = { openOnlyOfficeDocument(currentFile) }
        } else {
            setCommentsAdapter()
            noCommentsLayout.setup(
                icon = R.drawable.ic_comment,
                title = R.string.fileDetailsNoComments,
                initialListView = fileCommentsRecyclerView,
                secondaryBackground = true
            )

            onClickAddCommentButton = {
                Utils.createPromptNameDialog(
                    context = requireContext(),
                    title = R.string.buttonAddComment,
                    fieldName = R.string.fileDetailsCommentsFieldName,
                    positiveButton = R.string.buttonSend
                ) { dialog, name ->
                    fileDetailsViewModel.postFileComment(currentFile, name).observe(viewLifecycleOwner) { apiResponse ->
                        if (apiResponse.isSuccess()) {
                            apiResponse?.data?.let { comment ->
                                commentsAdapter.addComment(comment)
                                requireActivity().showSnackbar(R.string.fileDetailsCommentsConfirmationSnackbar)
                            }
                        } else {
                            requireActivity().showSnackbar(R.string.errorAddComment)
                        }
                        dialog.dismiss()
                        noCommentsLayout.toggleVisibility(commentsAdapter.itemCount == 0, showRefreshButton = false)
                    }
                }
            }
        }

        requireParentFragment().addCommentButton.setOnClickListener(onClickAddCommentButton)
    }

    private fun setCommentsAdapter() {
        commentsAdapter = FileCommentsAdapter { currentComment ->
            toggleLike(currentComment)
        }

        commentsAdapter.apply {
            isComplete = false
            fileDetailsViewModel.getFileComments(currentFile).observe(viewLifecycleOwner) { apiResponse ->
                apiResponse?.data?.let { comments ->
                    addAll(comments)
                    isComplete = apiResponse.page == apiResponse.pages
                } ?: also {
                    isComplete = true
                }
                noCommentsLayout.toggleVisibility(itemCount == 0, showRefreshButton = false)
            }

            onEditClicked = { comment ->
                Utils.createPromptNameDialog(
                    context = requireContext(),
                    title = R.string.modalCommentAddTitle,
                    fieldName = R.string.fileDetailsCommentsFieldName,
                    fieldValue = comment.body,
                    positiveButton = R.string.buttonSave
                ) { dialog, body ->
                    fileDetailsViewModel.putFileComment(currentFile, comment.id, body)
                        .observe(viewLifecycleOwner) { apiResponse ->
                            if (apiResponse.isSuccess()) {
                                commentsAdapter.updateComment(comment.apply { this.body = body })
                            }
                            dialog.dismiss()
                        }
                }
            }

            onDeleteClicked = { comment ->
                Utils.createConfirmation(
                    context = requireContext(),
                    title = "",
                    autoDismiss = false,
                    message = getString(R.string.modalCommentDeleteDescription)
                ) { dialog ->
                    fileDetailsViewModel.deleteFileComment(currentFile, comment.id)
                        .observe(viewLifecycleOwner) { apiResponse ->
                            dialog.dismiss()
                            if (apiResponse.isSuccess()) {
                                commentsAdapter.deleteComment(comment)
                                noCommentsLayout.toggleVisibility(commentsAdapter.itemCount == 0, showRefreshButton = false)
                            } else {
                                requireActivity().showSnackbar(R.string.errorDelete)
                            }
                        }
                }
            }
        }
        fileCommentsRecyclerView.adapter = commentsAdapter
    }

    private fun toggleLike(fileComment: FileComment) {
        if (fileComment.liked) {
            fileDetailsViewModel.postUnlike(currentFile, fileComment).observe(viewLifecycleOwner) { apiResponse ->
                if (apiResponse.isSuccess()) {
                    fileComment.liked = false
                    fileComment.likes?.remove(fileComment.likes.find { it.id == AccountUtils.currentUserId })
                    fileComment.likesCount = fileComment.likesCount - 1
                    commentsAdapter.updateComment(fileComment)
                } else {
                    requireActivity().showSnackbar(apiResponse.translatedError)
                }
            }
        } else {
            fileDetailsViewModel.postLike(currentFile, fileComment).observe(viewLifecycleOwner) { apiResponse ->
                if (apiResponse.isSuccess()) {
                    fileComment.liked = true
                    AccountUtils.currentUser?.let { fileComment.likes?.add(it) }
                    fileComment.likesCount = fileComment.likesCount + 1
                    commentsAdapter.updateComment(fileComment)
                } else {
                    requireActivity().showSnackbar(apiResponse.translatedError)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireParentFragment().addCommentButton.isVisible = true
    }
}