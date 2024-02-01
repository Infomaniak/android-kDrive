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
package com.infomaniak.drive.ui.fileList.preview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.ActivityPreviewPdfBinding

class PreviewPDFActivity : AppCompatActivity() {

    private val binding: ActivityPreviewPdfBinding by lazy { ActivityPreviewPdfBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupNavController().navigate(R.id.previewPDFFragment)
    }

    private fun getNavHostFragment() = supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment

    private fun setupNavController(): NavController {
        return getNavHostFragment().navController.apply {
            setGraph(R.navigation.view_pdf, PreviewPDFFragmentArgs(fileURI = intent.dataString).toBundle())
        }
    }
}
