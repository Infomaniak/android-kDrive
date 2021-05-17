/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.load
import coil.request.ImageRequest
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.lib.core.networking.HttpUtils
import kotlinx.android.synthetic.main.fragment_preview_picture.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreviewPictureFragment : PreviewFragment {
    constructor() : super()
    constructor(file: File) : super(file)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_preview_picture, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val imageViewDisposable = imageView.load(previewViewModel.currentFile.thumbnail()) {
            error(R.drawable.ic_images)
            fallback(R.drawable.ic_images)
            placeholder(R.drawable.ic_images)
        }

        container?.layoutTransition?.setAnimateParentHierarchy(false)

        imageView.apply {
            setOnTouchListener { view, event ->
                var result = true
                //can scroll horizontally checks if there's still a part of the image
                //that can be scrolled until you reach the edge
                if (event.pointerCount >= 2 || view.canScrollHorizontally(1) && imageView.canScrollHorizontally(-1)) {
                    //multi-touch event
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
        }

        if (previewViewModel.currentFile.isOffline && !previewViewModel.currentFile.isOldData(requireContext())) {
            if (!imageViewDisposable.isDisposed) imageViewDisposable.dispose()
            if (offlineFile.exists()) imageView?.setImageURI(offlineFile.toUri())
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                val imageLoader = Coil.imageLoader(requireContext())
                val request = ImageRequest.Builder(requireContext())
                    .headers(HttpUtils.getHeaders())
                    .data(previewViewModel.currentFile.imagePreview())
                    .build()

                imageLoader.execute(request).drawable?.let { drawable ->
                    if (!imageViewDisposable.isDisposed) imageViewDisposable.dispose()
                    withContext(Dispatchers.Main) {
                        imageView?.setImageDrawable(drawable)
                    }
                }
            }
        }
    }
}