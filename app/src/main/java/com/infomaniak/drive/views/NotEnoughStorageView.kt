/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import android.widget.FrameLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.FormatterFileSize
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.view_not_enough_storage.view.*

class NotEnoughStorageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        inflate(context, R.layout.view_not_enough_storage, this)
    }

    companion object {
        const val STORAGE_ALERT_MIN_PERCENTAGE = 90
    }

    @SuppressLint("SetTextI18n")
    fun setup(currentDrive: Drive) {
        currentDrive.apply {
            val storagePercentage = if (size > 0) (usedSize.toFloat() / size) * 100 else 0F
            if (storagePercentage > STORAGE_ALERT_MIN_PERCENTAGE) {
                this@NotEnoughStorageView.visibility = VISIBLE

                val usedStorage = FormatterFileSize.formatShortFileSize(context, usedSize, justValue = true)
                val totalStorage = FormatterFileSize.formatShortFileSize(context, size)
                progressIndicator.progress = (storagePercentage).toInt()
                title.text = "${"%.2f".format(usedStorage)} / $totalStorage"

                when (pack) {
                    Drive.DrivePack.SOLO.value, Drive.DrivePack.FREE.value -> {
                        description.setText(R.string.notEnoughStorageDescription1)
                        upgradeOffer.visibility = VISIBLE
                        upgradeOffer.setOnClickListener {
                            context.openUrl(ApiRoutes.upgradeDrive(AccountUtils.currentDriveId))
                        }
                    }
                    Drive.DrivePack.PRO.value, Drive.DrivePack.TEAM.value -> {
                        description.setText(R.string.notEnoughStorageDescription2)
                        upgradeOffer.visibility = GONE
                    }
                }

                close.setOnClickListener {
                    this@NotEnoughStorageView.visibility = GONE
                }
            } else {
                this@NotEnoughStorageView.visibility = GONE
            }
        }
    }
}