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
import com.infomaniak.drive.utils.isEmail
import com.infomaniak.drive.utils.loadAvatar
import kotlinx.android.synthetic.main.item_user.view.*
import java.util.*
import kotlin.collections.ArrayList

/**
 * Note :
 * itemList property is the list used by adapter to display data
 * initialList is the list used to store the default state of the itemlist (in case of item removal, list-filtering, etc.)
 */
class AvailableShareableItemsAdapter(
    context: Context,
    private var itemList: ArrayList<Shareable>,
    private val onItemClick: (item: Any) -> Unit,
) : ArrayAdapter<Shareable>(context, R.layout.item_user, itemList), Filterable {
    private var initialList: ArrayList<Shareable> = ArrayList()

    init {
        cleanItemList()
        initialList = ArrayList(itemList)
    }

    fun setAll(items: ArrayList<Shareable>) {
        itemList = items
        initialList = items
        notifyDataSetChanged()
    }

    fun addItem(item: Shareable) {
        itemList.add(item)
        notifyDataSetChanged()
    }

    fun removeItem(itemId: Int) {
        initialList.remove(initialList.find { item -> item.id == itemId })
        notifyDataSetChanged()
    }

    fun removeItemList(itemIdList: Iterable<Int>) {
        itemList.removeAll {
            itemIdList.contains(it.id)
        }
        notifyDataSetChanged()
    }

    private fun cleanItemList() {
        itemList = ArrayList(itemList
            .sortedBy { it.getFilterValue() }
            .distinct()
        )
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
                val searchTerm = constraint.toString().lowercase(Locale.ROOT)
                val finalUserList: ArrayList<Shareable> = ArrayList(
                    initialList.filter { item ->
                        item.getFilterValue().lowercase(Locale.ROOT).contains(searchTerm) ||
                                ((item is DriveUser) && item.email.lowercase(Locale.ROOT).contains(searchTerm))
                    }
                )
                return FilterResults().apply {
                    values = finalUserList
                    count = finalUserList.size
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                if (constraint.isNullOrBlank()) {
                    itemList = initialList
                    notifyDataSetInvalidated()
                } else {
                    itemList = if (constraint.toString().isEmail()) {
                        arrayListOf(
                            Invitation(
                                email = constraint.toString(),
                                status = context.getString(R.string.userInviteByEmail)
                            )
                        )
                    } else results.values as ArrayList<Shareable>
                    notifyDataSetChanged()
                }
            }
        }
    }
}