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
package com.infomaniak.drive.ui.fileList.preview

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.load
import coil.request.ImageRequest
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.FragmentPreviewPictureBinding
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.openWithClicked
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.toggleFullscreen
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.lib.core.utils.Utils.createRefreshTimer
import com.infomaniak.lib.core.utils.safeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreviewPictureFragment : PreviewFragment() {

    private var binding: FragmentPreviewPictureBinding by safeBinding()
    private val loadTimer by lazy {
        createRefreshTimer(milliseconds = 400) { binding.noThumbnailLayout.root.isVisible = true }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPreviewPictureBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        if (noCurrentFile()) return

        noThumbnailLayout.apply {
            previewDescription.isGone = true
            fileIcon.setImageResource(file.getFileType().icon)

            bigOpenWithButton.isGone = true
            bigOpenWithButton.setOnClickListener { openWithClicked() }
        }

        loadImage()

        container.layoutTransition?.setAnimateParentHierarchy(false)
        setupImageListeners()
    }

    override fun onDestroyView() {
        loadTimer.cancel()
        super.onDestroyView()
    }

    private fun loadImage() = with(binding) {
        val imageViewDisposable = imageView.load(file.thumbnail(previewSliderViewModel.shareLinkUuid)) { placeholder(R.drawable.coil_hack) }
        val imageLoader = Coil.imageLoader(requireContext())
        val previewRequest = buildPreviewRequest()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            imageLoader.execute(previewRequest).drawable?.let { drawable ->
                if (!imageViewDisposable.isDisposed) imageViewDisposable.dispose()
                withContext(Dispatchers.Main) { imageView.load(drawable) { crossfade(false) } }
            }
        }
    }

    private fun buildPreviewRequest(): ImageRequest = with(binding) {
        loadTimer.start()
        val offlineFile = if (file.isOffline) getOfflineFile() else null

        return ImageRequest.Builder(requireContext())
            .data(offlineFile ?: file.imagePreview(previewSliderViewModel.shareLinkUuid))
            .listener(
                onError = { _, _ ->
                    noThumbnailLayout.apply {
                        fileName.text = file.name
                        previewDescription.isVisible = true
                        bigOpenWithButton.isVisible = true
                    }
                    imageView.isGone = true
                },
                onSuccess = { _, _ ->
                    loadTimer.cancel()
                    noThumbnailLayout.root.isGone = true
                },
            )
            .build()
    }

    private fun getOfflineFile(): IOFile? {
        return file.getOfflineFile(requireContext(), previewSliderViewModel.userDrive.userId)?.let {
            if (file.isOfflineAndIntact(it)) it else null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupImageListeners(): Unit = with(binding) {
        imageView.apply {

            setOnTouchListener { view, event ->
                var result = true

                // `canScrollHorizontally` checks if there's still a part of
                // the image that can be scrolled until you reach the edge.
                if (event.pointerCount >= 2 || view.canScrollHorizontally(1) && imageView.canScrollHorizontally(-1)) {
                    // Multi-touch event
                    result = when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            // Disallow RecyclerView to intercept touch events.
                            parent.requestDisallowInterceptTouchEvent(true)
                            // Disable touch on view
                            false
                        }
                        MotionEvent.ACTION_UP -> {
                            // Allow RecyclerView to intercept touch events.
                            parent.requestDisallowInterceptTouchEvent(false)
                            true
                        }
                        else -> true
                    }
                }
                result
            }

            setOnClickListener { toggleFullscreen() }
        }
    }
}
