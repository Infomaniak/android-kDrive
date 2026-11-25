/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.navGraphViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.core.ui.view.utils.SnackbarUtils.showSnackbar
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.publicshare.PublicShareApiRepository
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewSliderBinding
import com.infomaniak.drive.ui.BasePreviewSliderFragment
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderViewModel
import com.infomaniak.drive.utils.setupBottomSheetFileBehavior
import com.infomaniak.drive.views.ExternalFileInfoActionsView
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class PublicSharePreviewSliderFragment : BasePreviewSliderFragment(), OnPublicShareItemClickListener {

    override val previewSliderViewModel: PreviewSliderViewModel by navGraphViewModels(R.id.publicSharePreviewSliderFragment)
    override val publicShareViewModel: PublicShareViewModel by activityViewModels()

    override val bottomSheetView: ExternalFileInfoActionsView
        get() = binding.publicShareBottomSheetFileActions
    override val bottomSheetBehavior: BottomSheetBehavior<View>
        get() = BottomSheetBehavior.from(bottomSheetView)

    override val isPublicShared = true
    override val canDownloadFiles
        get() = publicShareViewModel.canDownloadFiles
    override val ownerFragment = this

    override fun initCurrentFile() {
        currentFile = getPreviewFile(previewSliderViewModel.currentFileId, null)
            ?: throw Exception("No current preview found")
    }

    override fun onDownloadSuccess() {
        toggleBottomSheet(shouldShow = true)
    }

    override fun onDownloadError(errorMessage: Int) {
        showSnackbar(errorMessage, anchor = bottomSheetView)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPreviewSliderBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override suspend fun loadPreviewFiles(): List<File> {
        val cachedFiles = publicShareViewModel.childrenLiveData.value?.files.orEmpty().associateBy(File::id)
        val rootFile = publicShareViewModel.rootSharedFile.value
        val driveId = publicShareViewModel.driveId
        val uuid = publicShareViewModel.publicShareUuid
        val semaphore = Semaphore(6)
        return coroutineScope {
            (previewSliderViewModel.fileIds ?: intArrayOf()).asList().map { id ->
                async {
                    cachedFiles[id] ?: rootFile?.takeIf { it.id == id }
                        ?: semaphore.withPermit {
                            withContext(Dispatchers.IO) {
                                PublicShareApiRepository.getPublicShareRootFile(driveId, uuid, id).let { response ->
                                    if (!response.isSuccess()) Sentry.captureMessage("Unable to load public preview file $id")
                                    response.data.takeIf { response.isSuccess() }?.apply {
                                        publicShareUuid = uuid
                                        publicShareAuthToken = null
                                    }
                                }
                            }
                        }
                }
            }.awaitAll().filterNotNull()
        }
    }

    override fun onPreviewReady() = with(previewSliderViewModel) {
        userDrive = UserDrive(driveId = publicShareViewModel.driveId)
        this@PublicSharePreviewSliderFragment.userDrive = userDrive
        publicShareCanDownload = publicShareViewModel.canDownloadFiles
        initBottomSheet()
        observeCacheFileForAction(viewLifecycleOwner)
    }

    private fun initBottomSheet() = with(bottomSheetView) {
        requireActivity().setupBottomSheetFileBehavior(
            bottomSheetBehavior = bottomSheetBehavior,
            isDraggable = publicShareViewModel.canDownloadFiles,
            isFitToContents = true,
        )
        viewLifecycleOwner.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) { updateWithExternalFile(currentFile) }
        setupActions(publicShareViewModel.canDownloadFiles, currentFile)

        initOnClickListener(onItemClickListener = this@PublicSharePreviewSliderFragment)
    }
}
