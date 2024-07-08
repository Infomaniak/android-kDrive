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
package com.infomaniak.drive.data.documentprovider

import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import com.infomaniak.drive.R
import kotlinx.coroutines.Job

class DocumentCursor(
    projection: Array<out String>?,
    private var isAutoCloseableJob: Boolean = true,
) : MatrixCursor(projection) {
    private var _extras = Bundle.EMPTY

    var job: Job = Job()

    override fun getExtras(): Bundle {
        return _extras
    }

    override fun setExtras(extras: Bundle?) {
        _extras = extras ?: Bundle.EMPTY
    }

    override fun close() {
        super.close()
        if (isAutoCloseableJob) cancelJob()
    }

    fun cancelJob() {
        job.cancel()
    }

    companion object {
        fun createUri(context: Context?, parentDocumentId: String): Uri {
            val authority = context?.getString(R.string.CLOUD_STORAGE_AUTHORITY)
            return DocumentsContract.buildChildDocumentsUri(authority, parentDocumentId)
        }
    }
}
