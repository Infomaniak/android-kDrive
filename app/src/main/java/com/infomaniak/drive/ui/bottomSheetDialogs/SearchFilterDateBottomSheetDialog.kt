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
import androidx.core.util.Pair
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.SearchDateFilter
import com.infomaniak.drive.data.models.SearchDateFilter.DateFilterKey
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
        setCheckIconsVisibility()
        setTodayClick()
        setYesterdayClick()
        setLastSevenDaysClick()
        setCustomDateClick()
    }

    private fun setCheckIconsVisibility() {
        when (navigationArgs.date?.key) {
            DateFilterKey.TODAY -> todayFilterEndIcon.isVisible = true
            DateFilterKey.YESTERDAY -> yesterdayFilterEndIcon.isVisible = true
            DateFilterKey.LAST_SEVEN_DAYS -> lastSevenDaysFilterEndIcon.isVisible = true
            DateFilterKey.CUSTOM -> customFilterEndIcon.isVisible = true
            null -> {
                todayFilterEndIcon.isGone = true
                yesterdayFilterEndIcon.isGone = true
                lastSevenDaysFilterEndIcon.isGone = true
                customFilterEndIcon.isGone = true
            }
        }
    }

    private fun setTodayClick() {
        todayFilterLayout.setOnClickListener {
            with(Date()) {
                setBackNavResult(DateFilterKey.TODAY, startOfTheDay(), endOfTheDay(), getString(R.string.allToday))
            }
        }
    }

    private fun setYesterdayClick() {
        yesterdayFilterLayout.setOnClickListener {
            with(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time) {
                setBackNavResult(DateFilterKey.YESTERDAY, startOfTheDay(), endOfTheDay(), getString(R.string.allYesterday))
            }
        }
    }

    private fun setLastSevenDaysClick() {
        lastSevenDaysFilterLayout.setOnClickListener {
            val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }.time.startOfTheDay()
            val end = Date().endOfTheDay()
            setBackNavResult(DateFilterKey.LAST_SEVEN_DAYS, start, end, dateIntervalText(start, end))
        }
    }

    private fun setCustomDateClick() {
        customFilterLayout.setOnClickListener {
            showDateRangePicker { startTime, endTime ->
                val start = Date(startTime).startOfTheDay()
                val end = Date(endTime).endOfTheDay()
                setBackNavResult(DateFilterKey.CUSTOM, start, end, dateIntervalText(start, end))
            }
        }
    }

    private fun setBackNavResult(key: DateFilterKey, start: Date, end: Date, text: String) {
        setBackNavigationResult(SEARCH_FILTER_DATE_NAV_KEY, SearchDateFilter(key, start, end, text))
    }

    private fun dateIntervalText(start: Date, end: Date): String {
        val startFormat = when {
            start.year() != end.year() -> "${start.format(FORMAT_LONG)} - "
            start.month() != end.month() -> "${start.format(FORMAT_MEDIUM)} - "
            start.day() != end.day() -> "${start.format(FORMAT_SHORT)} - "
            else -> ""
        }
        return startFormat + end.format(FORMAT_LONG)
    }

    private fun showDateRangePicker(onPositiveButtonClicked: (Long, Long) -> Unit) {
        activity?.supportFragmentManager?.let { fragmentManager ->
            with(dateRangePicker()) {
                addOnNegativeButtonClickListener { dismiss() }
                addOnPositiveButtonClickListener { onPositiveButtonClicked(it.first, it.second) }
                show(fragmentManager, toString())
            }
        }
    }

    private fun dateRangePicker(): MaterialDatePicker<Pair<Long, Long>> {
        return MaterialDatePicker.Builder
            .dateRangePicker()
            .setTheme(R.style.MaterialCalendarThemeBackground)
            .setCalendarConstraints(constraintsUntilNow())
            .build()
    }

    private fun constraintsUntilNow(): CalendarConstraints {
        return CalendarConstraints.Builder()
            .setEnd(Date().time)
            .setValidator(DateValidatorPointBackward.now())
            .build()
    }

    companion object {
        const val SEARCH_FILTER_DATE_NAV_KEY = "search_filter_date_nav_key"
        private const val FORMAT_LONG = "d MMM yyyy"
        private const val FORMAT_MEDIUM = "d MMM"
        private const val FORMAT_SHORT = "d"
    }
}
