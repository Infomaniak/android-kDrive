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
package com.infomaniak.drive.ui.fileList.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.openWithClicked
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.toggleFullscreen
import kotlinx.android.synthetic.main.fragment_preview_others.bigOpenWithButton
import kotlinx.android.synthetic.main.fragment_preview_others.container
import kotlinx.android.synthetic.main.fragment_preview_others.fileIcon
import kotlinx.android.synthetic.main.fragment_preview_others.fileName

class PreviewOtherFragment : PreviewFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_preview_others, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (noCurrentFile()) return

        container?.layoutTransition?.setAnimateParentHierarchy(false)

        fileIcon.setImageResource(file.getFileType().icon)
        fileName.text = file.name

        container?.setOnClickListener { toggleFullscreen() }

        bigOpenWithButton.setOnClickListener { openWithClicked() }
    }
}