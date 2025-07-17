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
package com.infomaniak.drive.ui.publicShare

import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.ActivityPublicShareBinding
import com.infomaniak.drive.extensions.addSentryBreadcrumb
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.extensions.trackDestination
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.setColorNavigationBar
import com.infomaniak.drive.utils.setColorStatusBar
import com.infomaniak.lib.core.utils.setMargins

class PublicShareActivity : AppCompatActivity() {

    private val binding by lazy { ActivityPublicShareBinding.inflate(layoutInflater) }
    private val publicShareViewModel: PublicShareViewModel by viewModels()
    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment).navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        navController.addOnDestinationChangedListener { _, dest, _ -> onDestinationChanged(dest) }
        binding.root.enableEdgeToEdge(withTop = false, withBottom = false) {
            binding.mainPublicShareButton.setMargins(bottom = it.bottom)
        }
        if (SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }

    override fun onDestroy() {
        IOFile(filesDir, getString(R.string.EXPOSED_PUBLIC_SHARE_DIR)).apply { if (exists()) deleteRecursively() }
        super.onDestroy()
    }

    private fun onDestinationChanged(destination: NavDestination) {
        destination.addSentryBreadcrumb()
        destination.trackDestination()

        if (destination.id == R.id.publicShareListFragment || destination.id == R.id.publicShareBottomSheetFileActions) {
            setColorStatusBar()
            setColorNavigationBar()
        }

        val isMainButtonVisible = destination.id == R.id.publicShareListFragment && publicShareViewModel.canDownloadFiles
        binding.mainPublicShareButton.isVisible = isMainButtonVisible
    }

    fun getMainButton() = binding.mainPublicShareButton

    companion object {
        const val PUBLIC_SHARE_TAG = "publicShare"
    }
}
