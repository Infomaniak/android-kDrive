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
package com.infomaniak.drive.utils

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButtonToggleGroup

object TabViewPagerUtils {

    fun Fragment.setup(
        tabsViewPager: ViewPager2,
        tabsGroup: MaterialButtonToggleGroup,
        tabs: ArrayList<FragmentTab>,
        onCheckedButton: ((position: Position) -> Unit)? = null
    ) {
        tabsViewPager.apply {
            adapter = ViewPagerAdapter(childFragmentManager, lifecycle, tabs)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    val button = (tabsViewPager.adapter as ViewPagerAdapter).tabs[position].button
                    tabsGroup.check(button)
                }
            })
        }

        tabsGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val position = (tabsViewPager.adapter as ViewPagerAdapter).tabs.find { it.button == checkedId }!!.position
                tabsViewPager.setCurrentItem(position, true)
                onCheckedButton?.invoke(position)
            }
        }
    }

    fun ViewPager2.getFragment(position: Int): Fragment = (adapter as ViewPagerAdapter).tabs[position].fragment

    private class ViewPagerAdapter(
        manager: FragmentManager,
        lifecycle: Lifecycle,
        val tabs: ArrayList<FragmentTab>
    ) : FragmentStateAdapter(manager, lifecycle) {

        override fun getItemCount() = tabs.size

        override fun createFragment(position: Int): Fragment {
            return tabs[position].fragment
        }
    }

    data class FragmentTab(
        val position: Int,
        val fragment: Fragment,
        @IdRes val button: Int
    )
}