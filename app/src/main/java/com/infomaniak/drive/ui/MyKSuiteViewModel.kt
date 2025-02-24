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
package com.infomaniak.drive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.myksuite.ui.data.MyKSuiteData
import com.infomaniak.drive.utils.MyKSuiteDataUtils
import com.infomaniak.lib.core.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyKSuiteViewModel : ViewModel() {

    val myKSuiteDataResult = SingleLiveEvent<MyKSuiteData>()

    fun refreshMyKSuite() = viewModelScope.launch(Dispatchers.IO) {
        MyKSuiteDataUtils.fetchData()?.let(myKSuiteDataResult::postValue)
    }
}
