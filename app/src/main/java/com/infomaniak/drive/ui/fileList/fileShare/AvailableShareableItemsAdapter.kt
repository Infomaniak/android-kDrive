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
package com.infomaniak.drive.ui.fileList.fileShare

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import coil.load
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.Invitation
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.data.models.Team
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.isEmail
import com.infomaniak.drive.utils.loadAvatar
import kotlinx.android.synthetic.main.item_user.view.*

/**
 * Note :
 * itemList property is the list used by adapter to display data
 * initialList is the list used to store the default state of the itemlist (in case of item removal, list-filtering, etc.)
 */
class AvailableShareableItemsAdapter(
    context: Context,
    private var itemList: ArrayList<Shareable>,
    var notShareableUserIds: ArrayList<Int> = arrayListOf(),
    var notShareableEmails: ArrayList<String> = arrayListOf(),
    var notShareableTeamIds: ArrayList<Int> = arrayListOf(),
    private val onItemClick: (item: Shareable) -> Unit,
) : ArrayAdapter<Shareable>(context, R.layout.item_user, itemList), Filterable {
    var initialList: ArrayList<Shareable> = ArrayList()

    init {
        cleanItemList()
        initialList = itemList
    }

    fun setAll(items: List<Shareable>) {
        itemList.clear()
        itemList.addAll(items)
        initialList = itemList
        notifyDataSetChanged()
    }

    fun removeFromNotShareables(item: Shareable) {
        when (item) {
            is DriveUser -> notShareableUserIds.remove(item.id)
            is Invitation -> notShareableEmails.remove(item.email)
            is Team -> notShareableTeamIds.remove(item.id)
        }
    }

    fun addFirstAvailableItem(): Boolean {
        val item = itemList.firstOrNull()
        return when {
            item?.isShareable() == true -> {
                onItemClick(item)
                true
            }
            else -> false
        }
    }

    private fun cleanItemList() {
        itemList.sortBy { it.getFilterValue() }
    }

    override fun notifyDataSetChanged() {
        cleanItemList()
        super.notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val item = itemList[position]

        return (convertView ?: inflater.inflate(R.layout.item_user, parent, false)).apply {
            when (item) {
                is DriveUser -> {
                    userAvatar.loadAvatar(item)
                    userName.text = item.displayName
                    userEmail.text = item.email
                    chevron.visibility = GONE
                }
                is Invitation -> {
                    userAvatar.load(R.drawable.ic_account)
                    userName.text = item.email
                    userEmail.text = context.getString(R.string.userInviteByEmail)
                    chevron.visibility = GONE
                }
                is Team -> {
                    val teamUsersCount = item.usersCount(AccountUtils.getCurrentDrive()!!)
                    userAvatar.load(R.drawable.ic_circle_team)
                    userAvatar.setBackgroundColor(item.getParsedColor())
                    userName.text = item.name
                    userEmail.text =
                        resources.getQuantityString(R.plurals.shareUsersCount, teamUsersCount, teamUsersCount)
                    chevron.visibility = GONE
                }
            }

            setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun getCount(): Int {
        return itemList.size
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchTerm = constraint?.standardize() ?: ""
                val finalUserList = initialList
                    .filter {
                        it.getFilterValue().standardize()
                            .contains(searchTerm) || ((it is DriveUser) && it.email.standardize().contains(searchTerm))
                    }.filterNot { displayedItem ->
                        notShareableUserIds.any { it == displayedItem.id } ||
                                notShareableEmails.any { displayedItem is DriveUser && it == displayedItem.email } ||
                                notShareableTeamIds.any { it == displayedItem.id }
                    }
                return FilterResults().apply {
                    values = finalUserList
                    count = finalUserList.size
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                val searchTerm = constraint?.standardize()
                if (searchTerm?.isEmail() == true && !searchTerm.existsInAvailableItems()) {
                    itemList = if (!notShareableEmails.contains(searchTerm)) {
                        arrayListOf(Invitation(email = searchTerm, status = context.getString(R.string.userInviteByEmail)))
                    } else arrayListOf()
                    notifyDataSetChanged()
                } else {
                    itemList = results.values as ArrayList<Shareable> // Normal warning
                    notifyDataSetChanged()
                }
            }
        }
    }

    private fun CharSequence.standardize(): String = this.toString().trim().lowercase()

    private fun String.existsInAvailableItems(): Boolean =
        initialList.any { availableItem -> availableItem is DriveUser && availableItem.email.standardize() == this }

    private fun Shareable.isShareable(): Boolean {
        return when (this) {
            is DriveUser -> !notShareableUserIds.contains(this.id) && !notShareableEmails.contains(this.email)
            is Invitation -> !notShareableUserIds.contains(this.userId) && !notShareableEmails.contains(this.email)
            is Team -> !notShareableTeamIds.contains(this.id)
            else -> true
        }
    }
}