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
package com.infomaniak.drive.data.models

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.infomaniak.drive.R
import kotlinx.parcelize.Parcelize

@Parcelize
enum class ExtensionType(val value: String, @DrawableRes val icon: Int, @StringRes val searchFilterName: Int) : Parcelable {
    ARCHIVE("archive", R.drawable.ic_file_zip, R.string.allArchive),
    AUDIO("audio", R.drawable.ic_file_audio, R.string.allAudio),
    CODE("code", R.drawable.ic_file_code, R.string.allCode),
    FOLDER("dir", R.drawable.ic_folder_filled, R.string.allFolder),
    FONT("font", R.drawable.ic_file_font, R.string.allFont),
    FORM("form", R.drawable.ic_file_form, R.string.allOfficeForm),
    IMAGE("image", R.drawable.ic_file_image, R.string.allPictures),
    MAIL("mail", R.drawable.ic_file_mail, R.string.allEmail),
    MODEL("model", R.drawable.ic_file_3dmodel, R.string.all3DModel),
    PDF("pdf", R.drawable.ic_file_pdf, R.string.allPdf),
    PRESENTATION("presentation", R.drawable.ic_file_presentation, R.string.allOfficePoints),
    SPREADSHEET("spreadsheet", R.drawable.ic_file_sheets, R.string.allOfficeGrids),
    TEXT("text", R.drawable.ic_file_text, R.string.allOfficeDocs),
    UNKNOWN("unknown", R.drawable.ic_file, -1),
    URL("url", R.drawable.url, -1),
    VIDEO("video", R.drawable.ic_file_video, R.string.allVideo),
}
