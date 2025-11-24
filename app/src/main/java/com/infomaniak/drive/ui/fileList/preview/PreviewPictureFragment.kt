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
package com.infomaniak.drive.ui.fileList.preview

import android.annotation.SuppressLint
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.infomaniak.core.legacy.utils.Utils.createRefreshTimer
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.drive.MainApplication
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.coil.ImageLoaderType
import com.infomaniak.drive.databinding.FragmentPreviewPictureBinding
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.openWithClicked
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.toggleFullscreen
import com.infomaniak.drive.utils.IOFile
import kotlinx.coroutines.launch

class PreviewPictureFragment : PreviewFragment() {

    private var binding: FragmentPreviewPictureBinding by safeBinding()
    private val loadTimer by lazy {
        createRefreshTimer(milliseconds = LOADER_TIMEOUT_MS) {
            binding.loader.isGone = true
            binding.noThumbnailLayout.root.isVisible = true
            binding.imageView.isGone = true
        }
    }
    private val previewRequestListener = object : ImageRequest.Listener {
        override fun onStart(request: ImageRequest) = binding.onPreviewRequestStart()
        override fun onSuccess(request: ImageRequest, result: SuccessResult) = binding.onPreviewRequestSuccess(result)
        override fun onError(request: ImageRequest, result: ErrorResult) = binding.onPreviewRequestError()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPreviewPictureBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        if (noCurrentFile()) return

        noThumbnailLayout.apply {
            fileIcon.setImageResource(file.getFileType().icon)
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

    private fun loadImage() {
        val thumbnailPreviewRequest = buildThumbnailPreviewRequest()
        val previewRequest = buildPreviewRequest()
        val imageLoader = getImageLoader()

        viewLifecycleOwner.lifecycleScope.launch {
            imageLoader.execute(thumbnailPreviewRequest)
            imageLoader.execute(previewRequest)
        }
    }

    private fun getImageLoader(): ImageLoader {
        return if (navigationArgs?.isPublicShared == true) {
            val mainApp = requireContext().applicationContext as MainApplication
            mainApp.newImageLoader(ImageLoaderType.PublicShared)
        } else {
            requireContext().imageLoader
        }
    }

    private fun buildThumbnailPreviewRequest(): ImageRequest {
        return ImageRequest.Builder(requireContext())
            .data(ApiRoutes.getThumbnailUrl(file))
            .listener(previewRequestListener)
            .build()
    }

    private fun buildPreviewRequest(): ImageRequest {
        val offlineFile = if (file.isOffline) getOfflineFile() else null

        return ImageRequest.Builder(requireContext())
            .data(offlineFile ?: ApiRoutes.getImagePreviewUrl(file))
            .listener(previewRequestListener)
            .build()
    }

    private fun FragmentPreviewPictureBinding.onPreviewRequestStart() {
        if (imageView.isVisible.not() && noThumbnailLayout.root.isVisible.not()) {
            loader.isVisible = true
            loadTimer.start()
        }
    }

    private fun FragmentPreviewPictureBinding.onPreviewRequestSuccess(result: SuccessResult) {
        loadTimer.cancel()
        loader.isGone = true
        noThumbnailLayout.root.isGone = true
        imageView.isVisible = true
        val drawable = result.image.asDrawable(resources)
        imageView.setImageDrawable(drawable)
        // This is to start the GIF animation. Otherwise, it'll stay at the first frame.
        (drawable as? Animatable)?.start()
    }

    private fun FragmentPreviewPictureBinding.onPreviewRequestError() {
        loadTimer.cancel()
        loader.isGone = true
        noThumbnailLayout.apply {
            fileName.text = file.name
            root.isVisible = true
        }
        imageView.isGone = true
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

    companion object {
        private const val LOADER_TIMEOUT_MS = 30_000L
    }
}
