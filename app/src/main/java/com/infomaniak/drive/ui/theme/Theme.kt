/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.drive.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import com.infomaniak.core.ui.compose.basics.bottomsheet.BottomSheetThemeDefaults
import com.infomaniak.core.ui.compose.basics.bottomsheet.ProvideBottomSheetTheme
import com.infomaniak.core.ui.compose.materialthemefromxml.MaterialThemeFromXml
import com.infomaniak.drive.R

@Composable
fun DriveTheme(content: @Composable () -> Unit) {
    MaterialThemeFromXml {
        ProvideBottomSheetTheme(
            theme = BottomSheetThemeDefaults.theme(
                shape = RoundedCornerShape(
                    topStart = dimensionResource(R.dimen.bottomSheetCornerSize),
                    topEnd = dimensionResource(R.dimen.bottomSheetCornerSize),
                ),
                dragHandleColor = colorResource(R.color.dragHandleColor),
                titleTextStyle = Typography.subtitle2,
                titleColor = colorResource(R.color.title)
            ),
            content = content,
        )
    }
}
