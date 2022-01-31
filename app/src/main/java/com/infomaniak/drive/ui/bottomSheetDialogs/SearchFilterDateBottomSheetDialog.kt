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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.SearchDateFilter
import com.infomaniak.drive.data.models.SearchDateFilter.DateFilterKey
import com.infomaniak.drive.ui.fileList.SearchFiltersViewModel
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.utils.format
import kotlinx.android.synthetic.main.fragment_bottom_sheet_search_filter_date.*
import java.util.*
import androidx.core.util.Pair as AndroidPair

class SearchFilterDateBottomSheetDialog : BottomSheetDialogFragment() {

    private val searchFiltersViewModel: SearchFiltersViewModel by navGraphViewModels(R.id.searchFiltersFragment)
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
                updateDateFilter(DateFilterKey.TODAY, startOfTheDay(), endOfTheDay(), getString(R.string.allToday))
            }
        }
    }

    private fun setYesterdayClick() {
        yesterdayFilterLayout.setOnClickListener {
            with(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time) {
                updateDateFilter(DateFilterKey.YESTERDAY, startOfTheDay(), endOfTheDay(), getString(R.string.allYesterday))
            }
        }
    }

    private fun setLastSevenDaysClick() {
        lastSevenDaysFilterLayout.setOnClickListener {
            val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }.time.startOfTheDay()
            val end = Date().endOfTheDay()
            updateDateFilter(DateFilterKey.LAST_SEVEN_DAYS, start, end, intervalAsText(start, end))
        }
    }

    private fun setCustomDateClick() {
        customFilterLayout.setOnClickListener {
            showDateRangePicker { startTime, endTime ->
                val start = Date(startTime).startOfTheDay()
                val end = Date(endTime).endOfTheDay()
                updateDateFilter(DateFilterKey.CUSTOM, start, end, intervalAsText(start, end))
            }
        }
    }

    private fun updateDateFilter(key: DateFilterKey, start: Date, end: Date, text: String) {
        searchFiltersViewModel.date.value = SearchDateFilter(key, start, end, text)
        dismiss()
    }

    private fun showDateRangePicker(onPositiveButtonClicked: (Long, Long) -> Unit) {
        with(dateRangePicker()) {
            addOnNegativeButtonClickListener { dismiss() }
            addOnPositiveButtonClickListener { onPositiveButtonClicked(it.first, it.second) }
            show(this@SearchFilterDateBottomSheetDialog.childFragmentManager, toString())
        }
    }

    private fun dateRangePicker(): MaterialDatePicker<AndroidPair<Long, Long>> {
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

    private fun intervalAsText(start: Date, end: Date): String {
        val longDate = "d MMM yyyy"
        val separator = " - "
        val startFormat = when {
            start.year() != end.year() -> start.format(longDate) + separator
            start.month() != end.month() -> start.format("d MMM") + separator
            start.day() != end.day() -> start.format("d") + separator
            else -> ""
        }

        return startFormat + end.format(longDate)
    }
}
