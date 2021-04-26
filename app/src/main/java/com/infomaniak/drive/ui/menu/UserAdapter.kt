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
package com.infomaniak.drive.ui.menu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.setUserView
import com.infomaniak.lib.core.models.User
import com.infomaniak.lib.core.views.ViewHolder

class UserAdapter(
    private val users: ArrayList<User>,
    private val isCardview: Boolean = true,
    private val onItemClicked: (user: User) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    override fun getItemViewType(position: Int): Int {
        return if (isCardview) VIEW_TYPE_CARDVIEW
        else VIEW_TYPE_NORMAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutID = if (viewType == VIEW_TYPE_CARDVIEW) R.layout.cardview_user else R.layout.item_user
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(layoutID, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.itemView.setUserView(user, isCardview, onItemClicked)
    }

    override fun getItemCount() = users.size

    companion object {
        const val VIEW_TYPE_CARDVIEW = 1
        const val VIEW_TYPE_NORMAL = 2
    }
}