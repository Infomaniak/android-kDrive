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

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.core.view.forEach
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.get
import androidx.navigation.ui.NavigationUI.onNavDestinationSelected
import androidx.navigation.ui.onNavDestinationSelected
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import io.sentry.Sentry
import java.lang.ref.WeakReference

/**
 * Custom version of [androidx.navigation.ui.NavigationUI].
 *
 * Currently, when we go to "Shared with me" for example, and return to the file list from
 * the bottom navigation, we cannot reselect the menu item from the bottom navigation.
 *
 * @see [issue](https://issuetracker.google.com/issues/206147604)
 */
object NavigationUiUtils {

    /**
     * Sets up a [NavigationBarView] to use with a [NavController]. This will call
     * [android.view.MenuItem.onNavDestinationSelected] when a menu item is selected.
     *
     * The selected item in the NavigationView will automatically be updated when the destination changes.
     */
    fun NavigationBarView.setupWithNavControllerCustom(navController: NavController) {
        setupWithNavController(this, navController)
    }

    /**
     * Sets up a [NavigationBarView] to use with a [NavController]. This
     * will call [onNavDestinationSelected] when a menu item is selected.
     *
     * The selected item in the NavigationBarView will automatically be updated when the destination changes.
     *
     * @param navigationBarView The NavigationBarView ([BottomNavigationView] or
     * [NavigationRailView]) that should be kept in sync with changes to the NavController.
     * @param navController The NavController that supplies the primary menu. Navigation actions
     * on this NavController will be reflected in the selected item in the NavigationBarView.
     */
    private fun setupWithNavController(navigationBarView: NavigationBarView, navController: NavController) {
        navigationBarView.setOnItemSelectedListener { item ->
            runCatching {
                item.isChecked = true
                onNavDestinationSelected(item, navController)
            }.getOrElse { exception ->
                Sentry.captureException(exception) { scope ->
                    scope.setTag("ItemId", item.itemId.toString())
                    scope.setTag("CurrentDestinationId", navController.currentDestination?.id.toString())
                    scope.setExtra("ItemId", item.itemId.toString())
                    scope.setExtra("Item isChecked", item.isChecked.toString())
                    scope.setExtra("CurrentDestinationId", navController.currentDestination?.id.toString())
                    scope.setExtra("ParentId", navController.currentDestination?.parent?.id.toString())
                    scope.setExtra("Graph", navController.graph[item.itemId].route.toString())
                }
                false
            }
        }

        val weakReference = WeakReference(navigationBarView)
        navController.addOnDestinationChangedListener(object : NavController.OnDestinationChangedListener {

            override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
                val view = weakReference.get()
                if (view == null) {
                    navController.removeOnDestinationChangedListener(this)
                    return
                }
                view.menu.forEach { item ->
                    if (destination.matchDestination(item.itemId) && !item.isChecked) {
                        item.isChecked = true
                    }
                }
            }

        })
    }

    private fun NavDestination.matchDestination(@IdRes destId: Int) = hierarchy.any { it.id == destId }
}
