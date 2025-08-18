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
package com.infomaniak.drive.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.viewinterop.NoOpUpdate
import com.infomaniak.core.compose.margin.Margin
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.ViewBottomSheetSeparatorBinding
import splitties.systemservices.layoutInflater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveBottomSheetScaffold(
    isVisible: () -> Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable (ColumnScope.() -> Unit),
) {
    if (isVisible()) {
        val bottomSheetCornerSize = dimensionResource(R.dimen.bottomSheetCornerSize)
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = onDismissRequest,
            shape = RoundedCornerShape(topStart = bottomSheetCornerSize, topEnd = bottomSheetCornerSize),
            containerColor = colorResource(R.color.bottomSheetBackgroundColor),
            contentColor = colorResource(R.color.onBottomSheetBackgroundColor),
            dragHandle = {
                Box(modifier = Modifier.padding(vertical = dimensionResource(R.dimen.bottomSheetDragHandleVerticalMargin))) {
                    AndroidView(
                        factory = { ViewBottomSheetSeparatorBinding.inflate(it.layoutInflater).root },
                        modifier = Modifier
                            .width(dimensionResource(R.dimen.bottomSheetDragHandleWidth))
                            .height(dimensionResource(R.dimen.bottomSheetDragHandleHeight)),
                        update = NoOpUpdate,
                    )
                }
            },
            content = {
                title?.let {
                    Text(
                        it,
                        style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                content()
                Spacer(modifier = Modifier.height(Margin.Medium))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun Preview() {
    MaterialTheme {
        Surface {
            DriveBottomSheetScaffold(
                isVisible = { true },
                onDismissRequest = {},
                title = "This bottom sheet's title"
            ) {
                Text("Hello world")
            }
        }
    }
}
