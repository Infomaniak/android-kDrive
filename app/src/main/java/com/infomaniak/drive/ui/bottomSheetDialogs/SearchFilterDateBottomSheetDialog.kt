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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.SearchDateFilter
import com.infomaniak.drive.data.models.SearchDateFilterType
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.utils.format
import kotlinx.android.synthetic.main.fragment_bottom_sheet_search_filter_date.*
import java.util.*

open class SearchFilterDateBottomSheetDialog : BottomSheetDialogFragment() {

    private val navigationArgs: SearchFilterDateBottomSheetDialogArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_bottom_sheet_search_filter_date, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setStates()
        setListeners()
    }

    private fun setStates() {
        when (navigationArgs.date?.type) {
            SearchDateFilterType.TODAY -> todayFilterEndIcon.isVisible = true
            SearchDateFilterType.YESTERDAY -> yesterdayFilterEndIcon.isVisible = true
            SearchDateFilterType.LAST_SEVEN_DAYS -> lastSevenDaysFilterEndIcon.isVisible = true
            SearchDateFilterType.CUSTOM -> customFilterEndIcon.isVisible = true
            null -> {
                todayFilterEndIcon.isGone = true
                yesterdayFilterEndIcon.isGone = true
                lastSevenDaysFilterEndIcon.isGone = true
                customFilterEndIcon.isGone = true
            }
        }
    }

    private fun setListeners() {
        todayFilterLayout.setOnClickListener {
            with(Date()) {
                setBackNavResult(SearchDateFilterType.TODAY, startOfTheDay(), endOfTheDay(), getString(R.string.allToday))
            }
        }
        yesterdayFilterLayout.setOnClickListener {
            with(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time) {
                setBackNavResult(SearchDateFilterType.YESTERDAY, startOfTheDay(), endOfTheDay(), getString(R.string.allYesterday))
            }
        }
        lastSevenDaysFilterLayout.setOnClickListener {
            val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }.time.startOfTheDay()
            val end = Date().endOfTheDay()
            setBackNavResult(SearchDateFilterType.LAST_SEVEN_DAYS, start, end, dateIntervalText(start, end))
        }
        customFilterLayout.setOnClickListener {
            showDateRangePicker { startTime, endTime ->
                val start = Date(startTime).startOfTheDay()
                val end = Date(endTime).endOfTheDay()
                setBackNavResult(SearchDateFilterType.CUSTOM, start, end, dateIntervalText(start, end))
            }
        }
    }

    private fun setBackNavResult(type: SearchDateFilterType, start: Date, end: Date, text: String) {
        setBackNavigationResult(SEARCH_FILTER_DATE_NAV_KEY, SearchDateFilter(type, start, end, text))
    }

    private fun dateIntervalText(start: Date, end: Date): String {
        return if (start.day() == end.day()) {
            start.format("d MMM yyyy")
        } else {
            "${start.format(if (start.month() == end.month()) "d" else "d MMM")} - ${end.format("d MMM yyyy")}"
        }
    }

    private fun showDateRangePicker(onPositiveButtonClicked: (Long, Long) -> Unit) {
        activity?.supportFragmentManager?.let { fragmentManager ->
            with(
                MaterialDatePicker.Builder
                    .dateRangePicker()
                    .setTheme(R.style.MaterialCalendarThemeBackground)
                    .build()
            ) {
                show(fragmentManager, toString())
                addOnNegativeButtonClickListener { dismiss() }
                addOnPositiveButtonClickListener { onPositiveButtonClicked(it.first, it.second) }
            }
        }
    }

    companion object {
        const val SEARCH_FILTER_DATE_NAV_KEY = "search_filter_date_nav_key"
    }
}
