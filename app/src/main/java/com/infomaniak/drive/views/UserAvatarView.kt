/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.databinding.ItemUserAvatarBinding
import com.infomaniak.drive.utils.isPositive
import com.infomaniak.drive.utils.loadAvatar

class UserAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ItemUserAvatarBinding.inflate(LayoutInflater.from(context), this, true) }

    fun setUserAvatarOrHide(user: DriveUser? = null) = with(binding) {
        user?.let {
            root.isVisible = true
            remainingText.isGone = true
            avatarImageView.setBackgroundColor(
                ContextCompat.getColor(
                    context,
                    R.color.backgroundCardview
                )
            ) // in case of transparent pics
            avatarImageView.loadAvatar(user)
            TooltipCompat.setTooltipText(root, user.displayName)
        } ?: run {
            root.isGone = true
        }
    }

    fun setUsersNumber(number: Int) = with(binding) {
        if (number.isPositive()) {
            root.isVisible = true
            avatarImageView.setBackgroundColor(ContextCompat.getColor(context, R.color.primaryText))
            remainingText.text = "+$number"
            remainingText.isVisible = true
        } else {
            root.isGone = true
        }
    }
}
