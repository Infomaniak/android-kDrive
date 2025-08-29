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
package com.infomaniak.drive.utils

import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.ksuite.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.ksuite.myksuite.ui.data.MyKSuiteDataManager
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.SentryLog
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlin.coroutines.cancellation.CancellationException

object MyKSuiteDataUtils : MyKSuiteDataManager() {

    private val TAG = MyKSuiteDataUtils::class.simpleName.toString()

    override val currentUserId get() = AccountUtils.currentUserId

    override var myKSuite: MyKSuiteData? = null

    override suspend fun fetchData(): MyKSuiteData? = runCatching {
        MyKSuiteDataUtils.requestKSuiteData()

        // Don't try to fetch the my kSuite data if the user doesn't have a my kSuite offer
        val kSuite = AccountUtils.getCurrentDrive()?.kSuite
        if (kSuite != KSuite.PersoFree && kSuite != KSuite.PersoPlus) return@runCatching null

        val apiResponse = ApiRepository.getMyKSuiteData(HttpClient.okHttpClient)
        if (apiResponse.data == null) {
            @OptIn(ExperimentalSerializationApi::class)
            apiResponse.error?.exception?.let {
                if (it is MissingFieldException) SentryLog.e(TAG, "Error decoding the api result MyKSuiteObject", it)
            }
        } else {
            MyKSuiteDataUtils.upsertKSuiteData(apiResponse.data!!)
        }

        return@runCatching apiResponse.data
    }.getOrElse { exception ->
        if (exception is CancellationException) throw exception
        SentryLog.d(TAG, "Exception during myKSuite data fetch", exception)
        null
    }
}
