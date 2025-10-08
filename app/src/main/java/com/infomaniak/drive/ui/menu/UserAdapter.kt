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
package com.infomaniak.drive.ui.menu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import com.infomaniak.core.legacy.models.user.User
import com.infomaniak.drive.databinding.CardviewUserBinding
import com.infomaniak.drive.databinding.ItemUserBinding
import com.infomaniak.drive.ui.menu.UserAdapter.UserViewHolder
import com.infomaniak.drive.utils.setUserView

class UserAdapter(
    private val users: List<User>,
    private val isCardView: Boolean = true,
    private val onItemClicked: (user: User) -> Unit,
) : Adapter<UserViewHolder>() {

    override fun getItemViewType(position: Int): Int {
        return if (isCardView) VIEW_TYPE_CARDVIEW
        else VIEW_TYPE_NORMAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = if (viewType == VIEW_TYPE_CARDVIEW) {
            CardviewUserBinding.inflate(layoutInflater, parent, false)
        } else {
            ItemUserBinding.inflate(layoutInflater, parent, false)
        }

        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) = with(holder.binding) {
        val user = users[position]

        val itemUserBinding = if (getItemViewType(position) == VIEW_TYPE_CARDVIEW) {
            (this as CardviewUserBinding).itemViewUser
        } else {
            this as ItemUserBinding
        }

        itemUserBinding.setUserView(
            user,
            showRightIndicator = isCardView,
            showCurrentUser = true,
            withForceClick = !isCardView,
            onItemClicked = onItemClicked,
        )
    }

    override fun getItemCount() = users.size

    companion object {
        const val VIEW_TYPE_CARDVIEW = 1
        const val VIEW_TYPE_NORMAL = 2
    }

    class UserViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)
}
