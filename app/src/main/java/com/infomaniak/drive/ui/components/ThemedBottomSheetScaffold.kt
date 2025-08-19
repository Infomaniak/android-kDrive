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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.infomaniak.core.compose.basics.bottomsheet.LocalBottomSheetTheme
import com.infomaniak.core.compose.basics.bottomsheet.ProvideBottomSheetTheme
import com.infomaniak.core.compose.margin.Margin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemedBottomSheetScaffold(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    title: String? = null,
    content: @Composable (ColumnScope.() -> Unit),
) {
    val theme = LocalBottomSheetTheme.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = theme.shape,
        containerColor = theme.containerColor,
        contentColor = theme.contentColor,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                width = theme.dragHandleSize.width,
                height = theme.dragHandleSize.height,
                shape = theme.dragHandleShape,
                color = theme.dragHandleColor,
            )
        },
        content = {
            title?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth(),
                    color = theme.titleColor,
                    style = theme.titleTextStyle,
                    textAlign = TextAlign.Center,
                )
            }
            content()
            Spacer(modifier = Modifier.height(Margin.Medium))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun Preview() {
    MaterialTheme {
        ProvideBottomSheetTheme {
            Surface {
                ThemedBottomSheetScaffold(
                    onDismissRequest = {},
                    title = "This bottom sheet's title"
                ) {
                    Text("Hello world")
                }
            }
        }

    }
}
