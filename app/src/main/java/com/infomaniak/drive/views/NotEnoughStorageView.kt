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
package com.infomaniak.drive.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import com.infomaniak.core.FormatterFileSize.formatShortFileSize
import com.infomaniak.core.ksuite.myksuite.ui.utils.MatomoMyKSuite
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.databinding.ViewNotEnoughStorageBinding
import com.infomaniak.drive.utils.openMyKSuiteUpgradeBottomSheet

class NotEnoughStorageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewNotEnoughStorageBinding.inflate(LayoutInflater.from(context), this, true) }

    @SuppressLint("SetTextI18n")
    fun setup(currentDrive: Drive) = with(binding) {
        currentDrive.apply {
            val storagePercentage = if (size > 0L) (usedSize.toDouble() / size).toFloat() * 100.0f else 0.0f
            if (storagePercentage > STORAGE_ALERT_MIN_PERCENTAGE) {
                this@NotEnoughStorageView.isVisible = true

                val usedStorage = context.formatShortFileSize(usedSize, valueOnly = true)
                val totalStorage = context.formatShortFileSize(size)
                progressIndicator.progress = (storagePercentage).toInt()
                title.text = "$usedStorage / $totalStorage"

                if (isAdmin) {
                    description.setText(R.string.notEnoughStorageDescription1)
                    upgradeOffer.isVisible = true
                    upgradeOffer.setOnClickListener {
                        context.openMyKSuiteUpgradeBottomSheet(
                            navController = findNavController(),
                            matomoTrackerName = MatomoMyKSuite.NOT_ENOUGH_STORAGE_UPGRADE_NAME,
                        )
                    }
                } else {
                    description.setText(R.string.notEnoughStorageDescription2)
                    upgradeOffer.isGone = true
                }

                close.setOnClickListener {
                    this@NotEnoughStorageView.isGone = true
                }
            } else {
                this@NotEnoughStorageView.isGone = true
            }
        }
    }

    companion object {
        private const val STORAGE_ALERT_MIN_PERCENTAGE = 90.0f
    }
}
