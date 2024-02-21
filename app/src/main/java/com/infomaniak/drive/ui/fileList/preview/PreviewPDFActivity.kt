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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.ActivityPreviewPdfBinding
import com.infomaniak.drive.ui.SaveExternalFilesActivity
import com.infomaniak.drive.ui.SaveExternalFilesActivityArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.setupTransparentStatusBar
import com.infomaniak.lib.core.utils.setMargins

class PreviewPDFActivity : AppCompatActivity() {

    private val binding: ActivityPreviewPdfBinding by lazy { ActivityPreviewPdfBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupNavController().navigate(R.id.previewPDFFragment)

        binding.backButton.setOnClickListener { finish() }
        binding.saveToKDrive.setOnClickListener { saveToKDrive(Uri.parse(intent.dataString)) }
    }

    override fun onStart() {
        super.onStart()
        setupWindowInsetsListener()
        setupTransparentStatusBar()
    }

    private fun saveToKDrive(uri: Uri) {
        Intent(this, SaveExternalFilesActivity::class.java).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtras(
                SaveExternalFilesActivityArgs(
                    userId = AccountUtils.currentUserId,
                    driveId = AccountUtils.currentDriveId,
                ).toBundle()
            )
            type = "/pdf"
            startActivity(this)
        }
    }

    private fun setupWindowInsetsListener() = with(binding) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            with(windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())) {
                header.setMargins(left = left, top = top, right = right)
            }
            windowInsets
        }
    }

    private fun getNavHostFragment() = supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment

    private fun setupNavController(): NavController {
        return getNavHostFragment().navController.apply {
            setGraph(R.navigation.view_pdf, PreviewPDFFragmentArgs(fileURI = intent.dataString).toBundle())
        }
    }
}
