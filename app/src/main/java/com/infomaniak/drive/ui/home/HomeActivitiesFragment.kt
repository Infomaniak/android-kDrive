/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
package com.infomaniak.drive.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.distinctUntilChanged
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.core.legacy.utils.setPagination
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.FragmentHomeActivitiesBinding
import com.infomaniak.drive.extensions.onApplyWindowInsetsListener
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.navigateToParentFolder
import com.infomaniak.drive.utils.showSnackbar

class HomeActivitiesFragment : Fragment() {

    private var _binding: FragmentHomeActivitiesBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView

    private val homeViewModel: HomeViewModel by navGraphViewModels(R.id.homeFragment)
    private val mainViewModel: MainViewModel by activityViewModels()

    private var isDownloadingActivities = false

    private var paginationListener: RecyclerView.OnScrollListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentHomeActivitiesBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initAdapter()
        AccountUtils.getCurrentDrive()?.let { currentDrive ->
            if (!homeViewModel.needToRestoreFiles) {
                getLastActivities(currentDrive.id)
            }
        }
        observeLastActivities()

        binding.homeTabsRecyclerView.onApplyWindowInsetsListener { view, windowInsets ->
            view.updatePadding(
                bottom = resources.getDimension(R.dimen.recyclerViewPaddingBottom).toInt() + windowInsets.bottom,
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        paginationListener = null
    }

    private fun initAdapter() {
        binding.homeTabsRecyclerView.apply {
            paginationListener?.let(::removeOnScrollListener)

            val lastActivitiesAdapter = LastActivitiesAdapter()
            lastActivitiesAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
            layoutManager = LinearLayoutManager(requireContext())
            adapter = lastActivitiesAdapter

            paginationListener = setPagination(
                whenLoadMoreIsPossible = {
                    if (!lastActivitiesAdapter.isComplete && !isDownloadingActivities) {
                        AccountUtils.getCurrentDrive()?.let { currentDrive ->
                            isDownloadingActivities = true
                            homeViewModel.loadMoreActivities(currentDrive.id)
                        }
                    }
                })

            lastActivitiesAdapter.apply {
                onMoreFilesClicked = { fileActivity, validPreviewFiles ->
                    parentFragment?.safeNavigate(
                        HomeFragmentDirections.actionHomeFragmentToActivityFilesFragment(
                            fileIdList = validPreviewFiles.map { file -> file.id }.toIntArray(),
                            activityUser = fileActivity.user,
                            activityTranslation = resources.getQuantityString(
                                fileActivity.homeTranslation,
                                validPreviewFiles.size,
                                validPreviewFiles.size
                            )
                        )
                    )
                }
                onFileClicked = { currentFile, validPreviewFiles ->
                    when {
                        currentFile.isTrashed() -> showSnackbar(R.string.errorPreviewTrash, true)
                        currentFile.isFolder() -> navigateToParentFolder(currentFile.id, mainViewModel)
                        else -> Utils.displayFile(
                            mainViewModel = mainViewModel,
                            navController = findNavController(),
                            selectedFile = currentFile,
                            fileList = validPreviewFiles,
                            hideActions = true
                        )
                    }
                }
            }
        }
    }

    fun getLastActivities(driveId: Int, forceDownload: Boolean = false) {
        if (_binding == null || isDownloadingActivities) return

        (binding.homeTabsRecyclerView.adapter as? LastActivitiesAdapter)?.apply {
            if (forceDownload) {
                clean()
            }
            showLoading()
            isComplete = false
            isDownloadingActivities = true
            homeViewModel.loadLastActivities(driveId, forceDownload)
        }
    }

    private fun observeLastActivities() {
        val lastActivitiesAdapter = binding.homeTabsRecyclerView.adapter as? LastActivitiesAdapter ?: return

        if (homeViewModel.needToRestoreFiles && lastActivitiesAdapter.itemList.isEmpty()) {
            // TODO: (Realm kotlin) - Should be improved with realm kotlin, the current problem will no longer exist
            homeViewModel.restoreActivitiesIfNeeded()
        }

        homeViewModel.lastActivitiesResult.distinctUntilChanged().observe(viewLifecycleOwner) { lastActivityResult ->

            with(lastActivitiesAdapter) {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

                lastActivityResult?.let { (mergedActivities, isComplete, isFirstPage) ->
                    if (isFirstPage && itemCount > 0) clean()
                    addAll(mergedActivities)
                    this.isComplete = isComplete
                } ?: also {
                    isComplete = true
                    addAll(arrayListOf())
                }
            }
            isDownloadingActivities = false
        }
    }

}
