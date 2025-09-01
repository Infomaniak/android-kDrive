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
import androidx.fragment.app.Fragment
import com.infomaniak.core.FormatterFileSize.formatShortFileSize
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.ksuite.ui.utils.MatomoKSuite
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.databinding.ViewNotEnoughStorageBinding
import com.infomaniak.drive.utils.openKSuiteProBottomSheet
import com.infomaniak.drive.utils.openMyKSuiteUpgradeBottomSheet

class NotEnoughStorageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewNotEnoughStorageBinding.inflate(LayoutInflater.from(context), this, true) }

    @SuppressLint("SetTextI18n")
    fun setup(drive: Drive, fragment: Fragment) = with(binding) {
        val storagePercentage = if (drive.size > 0L) (drive.usedSize.toDouble() / drive.size).toFloat() * 100.0f else 0.0f
        if (storagePercentage > STORAGE_ALERT_MIN_PERCENTAGE) {
            this@NotEnoughStorageView.isVisible = true

            val usedStorage = context.formatShortFileSize(drive.usedSize, valueOnly = true)
            val totalStorage = context.formatShortFileSize(drive.size)
            progressIndicator.progress = (storagePercentage).toInt()
            title.text = "$usedStorage / $totalStorage"

            description.setText(if (drive.isAdmin) R.string.notEnoughStorageDescription1 else R.string.notEnoughStorageDescription2)

            if (drive.isKSuiteFreeTier) {
                upgradeOffer.isVisible = true
                upgradeOffer.setOnClickListener {
                    val matomoName = MatomoKSuite.NOT_ENOUGH_STORAGE_UPGRADE_NAME
                    if (drive.kSuite == KSuite.Pro.Free) {
                        fragment.openKSuiteProBottomSheet(drive.kSuite!!, drive.isAdmin, matomoName)
                    } else {
                        fragment.openMyKSuiteUpgradeBottomSheet(matomoName)
                    }
                }
            } else {
                upgradeOffer.isGone = true
            }
            close.setOnClickListener {
                this@NotEnoughStorageView.isGone = true
            }
        } else {
            this@NotEnoughStorageView.isGone = true
        }
    }

    companion object {
        private const val STORAGE_ALERT_MIN_PERCENTAGE = 90.0f
    }
}
