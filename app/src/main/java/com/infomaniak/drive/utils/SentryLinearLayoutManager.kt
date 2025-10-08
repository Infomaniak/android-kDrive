/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import androidx.navigation.NavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * TODO Temp fix
 */
class SentryLinearLayoutManager(private val navController: NavController, context: Context?) : LinearLayoutManager(context) {

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (exception: IndexOutOfBoundsException) {
            layoutManagerSentryLog(navController, exception)
        }
    }

    companion object {
        fun layoutManagerSentryLog(navController: NavController, exception: IndexOutOfBoundsException) {
            exception.printStackTrace()
            Sentry.captureException(exception) { scope ->
                navController.currentDestination?.displayName?.let { name ->
                    scope.setExtra("navigation", name)
                }
                scope.level = SentryLevel.WARNING
                scope.setExtra("message", "Data modified in different thread")
            }
        }
    }
}
