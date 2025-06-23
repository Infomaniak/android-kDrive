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
package com.infomaniak.drive.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.workDataOf
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.services.BulkOperationWorker

object BulkOperationsUtils {

    const val MIN_SELECTED = 10

    fun generateWorkerData(actionUuid: String, fileCount: Int, type: BulkOperationType): Data {
        return workDataOf(
            BulkOperationWorker.ACTION_UUID to actionUuid,
            BulkOperationWorker.TOTAL_FILES_KEY to fileCount,
            BulkOperationWorker.OPERATION_TYPE_KEY to type.toString()
        )
    }

    fun Context.launchBulkOperationWorker(workData: Data) {
        val bulkOperationWorkRequest: OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<BulkOperationWorker>()
                .setInputData(workData)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(BulkOperationWorker.TAG)
                .build()

        WorkManager.getInstance(this).enqueue(bulkOperationWorkRequest)
    }

    fun Context.isBulkOperationActive(): Boolean {
        return WorkManager.getInstance(this).getWorkInfos(
            WorkQuery.Builder.fromTags(listOf(BulkOperationWorker.TAG))
                .addStates(arrayListOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED))
                .build()
        ).get()?.isNotEmpty() == true
    }
}
