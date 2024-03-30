/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.CancellableAction
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentBottomSheetFileInfoActionsBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.DownloadProgressDialog
import com.infomaniak.drive.ui.fileList.FileListFragment
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.CANCELLABLE_ACTION_KEY
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.CANCELLABLE_MAIN_KEY
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.CANCELLABLE_TITLE_KEY
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragmentArgs
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.utils.Utils.openWithIntent
import com.infomaniak.drive.views.FileInfoActionsViewController
import com.infomaniak.drive.views.OnItemClickListener
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileInfoActionsBottomSheetDialog : BottomSheetDialogFragment(), OnItemClickListener {

    private var binding: FragmentBottomSheetFileInfoActionsBinding by safeBinding()

    private lateinit var drivePermissions: DrivePermissions
    private val mainViewModel: MainViewModel by activityViewModels()
    private val navigationArgs: FileInfoActionsBottomSheetDialogArgs by navArgs()

    override lateinit var currentFile: File

    //TODO TO DELETE !!

    override fun addFavoritesClicked() {
        TODO("Not yet implemented")
    }

    override fun cancelExternalImportClicked() {
        TODO("Not yet implemented")
    }

    override fun colorFolderClicked(color: String?) {
        TODO("Not yet implemented")
    }

    override fun downloadFileClicked() {
        TODO("Not yet implemented")
    }

    override fun dropBoxClicked(isDropBox: Boolean) {
        TODO("Not yet implemented")
    }

    override fun displayInfoClicked() {
        TODO("Not yet implemented")
    }

    override fun fileRightsClicked() {
        TODO("Not yet implemented")
    }

    override fun goToFolder() {
        TODO("Not yet implemented")
    }

    override fun manageCategoriesClicked(fileId: Int) {
        TODO("Not yet implemented")
    }

    override fun onCacheAddedToOffline() {
        TODO("Not yet implemented")
    }

    override fun onDeleteFile() {
        TODO("Not yet implemented")
    }

    override fun onDuplicateFile() {
        TODO("Not yet implemented")
    }

    //TODO END TO DELETE !!

    override val ownerFragment = this

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data -> onSelectFolderResult(data) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetFileInfoActionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentFile = FileController.getFileById(navigationArgs.fileId, navigationArgs.userDrive) ?: run {
            findNavController().popBackStack()
            return
        }

        drivePermissions = DrivePermissions().apply {
            registerPermissions(this@FileInfoActionsBottomSheetDialog) { authorized ->
                if (authorized) currentFile.downloadFile(requireContext(), this)
            }
        }

        binding.fileInfoActionsView.apply {
            init(
                ownerFragment = this@FileInfoActionsBottomSheetDialog,
                mainViewModel = mainViewModel,
                onItemClickListener = getFileInfoActionsViewController(),
                fileInfoActionsViewController = getFileInfoActionsViewController(),
                selectFolderResultLauncher = selectFolderResultLauncher,
                isSharedWithMe = navigationArgs.userDrive.sharedWithMe,
            )
            updateCurrentFile(currentFile)
        }

        setupBackActionHandler()
    }

    private fun setupBackActionHandler() {
        getBackNavigationResult<Int>(DownloadProgressDialog.OPEN_WITH) {
            context?.openWith(currentFile)
        }

        getBackNavigationResult<Any>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            lifecycleScope.launchWhenResumed { binding.fileInfoActionsView.refreshBottomSheetUi(currentFile) }
        }

        getBackNavigationResult<String>(ColorFolderBottomSheetDialog.COLOR_FOLDER_NAV_KEY) {
            updateFolderColor(it)
        }
    }

    private fun updateFolderColor(color: String) {
        if (isResumed) {
            mainViewModel.updateFolderColor(currentFile, color).observe(viewLifecycleOwner) { apiResponse ->
                findNavController().popBackStack()
                val text = if (apiResponse.isSuccess()) {
                    resources.getQuantityString(R.plurals.fileListColorFolderConfirmationSnackbar, 1)
                } else {
                    getString(apiResponse.translatedError)
                }
                showSnackbar(text, showAboveFab = true)
            }
        }
    }

    override fun onResume() = with(binding) {
        super.onResume()
        // Fix the popBackStack in onViewCreated because onResume is still called
        if (findNavController().currentDestination?.id != R.id.fileInfoActionsBottomSheetDialog) return
        fileInfoActionsView.observeOfflineProgression(this@FileInfoActionsBottomSheetDialog)
        fileInfoActionsView.updateAvailableOfflineItem()
    }

    override fun onPause() {
        super.onPause()
        binding.fileInfoActionsView.removeOfflineObservations(this)
    }

    override fun editDocumentClicked() {
        findNavController().popBackStack()
        super.editDocumentClicked()
    }

    private fun displayFileDetailsFragment() {
        currentFile.apply {
            safeNavigate(
                FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToFileDetailsFragment(
                    fileId = id,
                    userDrive = navigationArgs.userDrive
                )
            )
        }
    }

    private fun onFileRightsClicked() {
        safeNavigate(
            FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToFileShareDetailsFragment(
                fileId = currentFile.id
            )
        )
    }

    override fun sharePublicLink(onActionFinished: () -> Unit) {
        super.sharePublicLink(onActionFinished)
        binding.fileInfoActionsView.createPublicShareLink(
            onSuccess = {
                context?.shareText(it)
                findNavController().popBackStack()
                onActionFinished()
            },
            onError = { translatedError ->
                showSnackbar(translatedError, showAboveFab = true)
                onActionFinished()
            },
        )
    }

    private fun onManageCategoriesClicked(fileId: Int) {
        openManageCategoriesBottomSheetDialog(intArrayOf(fileId), navigationArgs.userDrive)
    }

    override fun removeOfflineFile(offlineLocalPath: IOFile, cacheFile: IOFile) {
        lifecycleScope.launch {
            mainViewModel.removeOfflineFile(currentFile, offlineLocalPath, cacheFile)

            withContext(Dispatchers.Main) {
                currentFile.isOffline = false
                if (findNavController().previousBackStackEntry?.destination?.id == R.id.offlineFileFragment) {
                    findNavController().popBackStack()
                } else {
                    binding.fileInfoActionsView.refreshBottomSheetUi(currentFile)
                }
                mainViewModel.updateOfflineFile.value = currentFile.id
            }
        }
    }

    private fun onDuplicateFile(apiResponse: ApiResponse<File>, succeed: Boolean) {
        if (succeed) {
            apiResponse.data?.let {
                mainViewModel.refreshActivities.value = true
                transmitActionAndPopBack(getString(R.string.allFileDuplicate, currentFile.name))
            }
        } else {
            transmitActionAndPopBack(getString(R.string.errorDuplicate))
        }
    }

    override fun onRenameFile(newName: String, onApiResponse: () -> Unit) {
        if (isResumed) {
            binding.fileInfoActionsView.onRenameFile(mainViewModel, newName,
                onSuccess = { action ->
                    mainViewModel.refreshActivities.value = true
                    transmitActionAndPopBack(
                        getString(R.string.allFileRename, currentFile.name),
                        action.setDriveAndReturn(currentFile.driveId)
                    )
                    onApiResponse()
                }, onError = { translatedError ->
                    transmitActionAndPopBack(translatedError)
                    onApiResponse()
                })
        } else {
            onApiResponse()
        }
    }

    private fun onDeleteFileResponse(file: File, apiResponse: ApiResponse<CancellableAction>, success: Boolean) {
        if (success) {
            mainViewModel.refreshActivities.value = true
            val title = getString(R.string.snackbarMoveTrashConfirmation, file.name)
            transmitActionAndPopBack(title, apiResponse.data?.setDriveAndReturn(currentFile.driveId))
        } else {
            transmitActionAndPopBack(getString(R.string.errorDelete))
        }
    }

    override fun openWithClicked() {
        super.openWithClicked()
        if (requireContext().openWithIntent(currentFile).resolveActivity(requireContext().packageManager) == null) {
            showSnackbar(R.string.errorNoSupportingAppFound, showAboveFab = true)
            findNavController().popBackStack()
        } else {
            safeNavigate(
                FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToDownloadProgressDialog(
                    fileId = currentFile.id,
                    fileName = currentFile.name,
                    userDrive = navigationArgs.userDrive
                )
            )
        }
    }

    override fun onLeaveShare(onApiResponse: () -> Unit) {
        if (isResumed) {
            mainViewModel.deleteFile(currentFile).observe(viewLifecycleOwner) { apiResponse ->
                onApiResponse()
                if (apiResponse.isSuccess()) {
                    transmitActionAndPopBack(getString(R.string.snackbarLeaveShareConfirmation))
                    mainViewModel.refreshActivities.value = true
                } else {
                    transmitActionAndPopBack(getString(R.string.anErrorHasOccurred))
                }
            }
        } else {
            onApiResponse()
        }
    }

    override fun onMoveFile(destinationFolder: File) {
        mainViewModel.moveFile(currentFile, destinationFolder)
            .observe(viewLifecycleOwner) { apiResponse ->
                if (apiResponse.isSuccess()) {
                    mainViewModel.refreshActivities.value = true
                    transmitActionAndPopBack(
                        getString(R.string.allFileMove, currentFile.name, destinationFolder.name),
                        apiResponse.data?.setDriveAndReturn(currentFile.driveId)
                    )
                } else {
                    transmitActionAndPopBack(getString(R.string.errorMove))
                }
            }
    }

    private fun transmitActionAndPopBack(message: String, action: CancellableAction? = null) {
        val bundle = bundleOf(CANCELLABLE_TITLE_KEY to message, CANCELLABLE_ACTION_KEY to action)
        setBackNavigationResult(CANCELLABLE_MAIN_KEY, bundle)
    }

    private fun getFileInfoActionsViewController(): FileInfoActionsViewController {
        return FileInfoActionsViewController(
            ownerFragment,
            currentFile,
            mainViewModel,
            FileInfoActionsViewController.Callbacks(
                getViewLifecycleOwner = { viewLifecycleOwner },
                getUserDrive = { navigationArgs.userDrive },
                getDrivePermissions = { drivePermissions },
                onFavoriteApiCallResult = { isSuccess, _ ->
                    if (isSuccess) {
                        setBackNavigationResult(FileListFragment.REFRESH_FAVORITE_FILE, currentFile.id)
                    } else {
                        findNavController().popBackStack()
                    }
                },
                onDisplayInfoClicked = ::displayFileDetailsFragment,
                onFileDownloaded = { findNavController().popBackStack() },
                onFileRightsClicked = ::onFileRightsClicked,
                onManageCategoriesClicked = ::onManageCategoriesClicked,
                onFileDeleted = ::onDeleteFileResponse,
                onFileDuplicated = { _, apiResponse, success -> onDuplicateFile(apiResponse, success) }
            )
        )
    }

    companion object {

        fun Fragment.openManageCategoriesBottomSheetDialog(filesIds: IntArray, userDrive: UserDrive? = null) {
            val args = SelectCategoriesFragmentArgs(CategoriesUsageMode.MANAGED_CATEGORIES, filesIds, userDrive = userDrive)

            safeNavigate(R.id.selectCategoriesFragment, args.toBundle())
        }
    }
}
