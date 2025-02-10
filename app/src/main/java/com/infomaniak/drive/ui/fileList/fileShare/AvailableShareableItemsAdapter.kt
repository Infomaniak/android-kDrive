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
package com.infomaniak.drive.ui.fileList.fileShare

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import androidx.core.view.isGone
import coil.load
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.Invitation
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.data.models.Team
import com.infomaniak.drive.databinding.ItemUserBinding
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.isEmail
import com.infomaniak.drive.utils.loadAvatar

/**
 * Note :
 * itemList property is the list used by adapter to display data
 * initialList is the list used to store the default state of the itemlist (in case of item removal, list-filtering, etc.)
 */
class AvailableShareableItemsAdapter(
    context: Context,
    private var itemList: ArrayList<Shareable>,
    var notShareableIds: ArrayList<Int> = arrayListOf(),
    var notShareableEmails: ArrayList<String> = arrayListOf(),
    private val getCurrentText: () -> CharSequence,
    private val onItemClick: (item: Shareable) -> Unit,
) : ArrayAdapter<Shareable>(context, R.layout.item_user, itemList), Filterable {

    private var initialList: ArrayList<Shareable> = ArrayList()

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
            is DriveUser, is Team -> notShareableIds.remove(item.id)
            is Invitation -> notShareableEmails.remove(item.email)
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
        val itemUserBinding = (convertView?.tag as? ItemUserBinding ?: inflateNewBinding(parent))

        return with(itemUserBinding) {

            val item = itemList[position]
            when (item) {
                is DriveUser -> {
                    userAvatar.loadAvatar(item)
                    userName.text = item.displayName
                    userEmail.text = item.email
                    rightIndicator.isGone = true
                }
                is Invitation -> {
                    userAvatar.load(R.drawable.ic_account)
                    userName.text = item.email
                    userEmail.text = context.getString(R.string.userInviteByEmail)
                    rightIndicator.isGone = true
                }
                is Team -> {
                    val teamUsersCount = item.usersCount(AccountUtils.getCurrentDrive()!!)
                    userAvatar.load(R.drawable.ic_circle_team)
                    userAvatar.setBackgroundColor(item.getParsedColor())
                    userName.text = item.name
                    userEmail.text =
                        context.resources.getQuantityString(R.plurals.shareUsersCount, teamUsersCount, teamUsersCount)
                    rightIndicator.isGone = true
                }
            }

            root.apply { setOnClickListener { onItemClick(item) } }
        }
    }

    private fun inflateNewBinding(parent: ViewGroup): ItemUserBinding {
        return ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply { root.tag = this }
    }

    override fun getCount() = itemList.size

    override fun getFilter(): Filter = object : Filter() {

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            if (constraint.isNullOrBlank()) {
                return FilterResults().apply {
                    values = arrayListOf<Shareable>()
                    count = 0
                }
            }

            val searchTerm = constraint.standardize()
            val finalUserList = initialList
                .filter {
                    it.getFilterValue().standardize().contains(searchTerm) ||
                            ((it is DriveUser) && it.email.standardize().contains(searchTerm))
                }.filterNot { displayedItem ->
                    notShareableIds.any { it == displayedItem.id } ||
                            notShareableEmails.any { displayedItem is DriveUser && it == displayedItem.email }
                }

            return FilterResults().apply {
                values = finalUserList
                count = finalUserList.size
            }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            val searchTerm = constraint?.standardize()

            itemList = if (searchTerm?.isEmail() == true && !searchTerm.existsInAvailableItems()) {
                if (!notShareableEmails.contains(searchTerm)) {
                    arrayListOf(Invitation(email = searchTerm, status = context.getString(R.string.userInviteByEmail)))
                } else {
                    arrayListOf()
                }
            } else {
                results.values as ArrayList<Shareable> // Normal warning
            }

            notifyDataSetChanged()
        }

        override fun convertResultToString(resultValue: Any?): CharSequence = getCurrentText()
    }

    private fun CharSequence.standardize(): String = this.toString().trim().lowercase()

    private fun String.existsInAvailableItems(): Boolean = initialList.any { availableItem ->
        availableItem is DriveUser && availableItem.email.standardize() == this
    }

    private fun Shareable.isShareable(): Boolean = when (this) {
        is DriveUser -> !notShareableIds.contains(this.id) && !notShareableEmails.contains(this.email)
        is Invitation -> !notShareableIds.contains(this.user?.id) && !notShareableEmails.contains(this.email)
        is Team -> !notShareableIds.contains(this.id)
        else -> true
    }
}
