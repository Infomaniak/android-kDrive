/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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

package com.infomaniak.drive

import com.infomaniak.drive.ui.home.RootFileTreeCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RootFileTreeCategoryTest {

    @Test
    fun checkOrdinalsAreStable() {
        // Ensure that the ordinals are kept stable
        assertEquals(0, RootFileTreeCategory.CommonFolders.ordinal)
        assertEquals(1, RootFileTreeCategory.PersonalFolder.ordinal)
        assertEquals(2, RootFileTreeCategory.Favorites.ordinal)
        assertEquals(3, RootFileTreeCategory.RecentChanges.ordinal)
        assertEquals(4, RootFileTreeCategory.SharedWithMe.ordinal)
        assertEquals(5, RootFileTreeCategory.MyShares.ordinal)
        assertEquals(6, RootFileTreeCategory.Offline.ordinal)
        assertEquals(7, RootFileTreeCategory.Trash.ordinal)
    }
}
