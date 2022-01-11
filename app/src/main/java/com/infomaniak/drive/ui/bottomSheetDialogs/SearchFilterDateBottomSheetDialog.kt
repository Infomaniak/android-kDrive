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
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.SearchDateFilter.DateFilterKey
import com.infomaniak.drive.utils.intervalAsText
import com.infomaniak.drive.utils.endOfTheDay
import com.infomaniak.drive.utils.setBackNavigationResult
import com.infomaniak.drive.utils.startOfTheDay
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
            setBackNavResult(DateFilterKey.LAST_SEVEN_DAYS, start, end, start.intervalAsText(end))
        }
    }

    private fun setCustomDateClick() {
        customFilterLayout.setOnClickListener {
            setBackNavResult(DateFilterKey.CUSTOM)
        }
    }

    private fun setBackNavResult(key: DateFilterKey, start: Date? = null, end: Date? = null, text: String? = null) {
        setBackNavigationResult(
            SEARCH_FILTER_DATE_NAV_KEY, bundleOf(
                SEARCH_FILTER_DATE_KEY_BUNDLE_KEY to key,
                SEARCH_FILTER_DATE_START_BUNDLE_KEY to start?.time,
                SEARCH_FILTER_DATE_END_BUNDLE_KEY to end?.time,
                SEARCH_FILTER_DATE_TEXT_BUNDLE_KEY to text,
            )
        )
    }

    companion object {
        const val SEARCH_FILTER_DATE_NAV_KEY = "search_filter_date_nav_key"
        const val SEARCH_FILTER_DATE_KEY_BUNDLE_KEY = "search_filter_date_key_bundle_key"
        const val SEARCH_FILTER_DATE_START_BUNDLE_KEY = "search_filter_date_start_bundle_key"
        const val SEARCH_FILTER_DATE_END_BUNDLE_KEY = "search_filter_date_end_bundle_key"
        const val SEARCH_FILTER_DATE_TEXT_BUNDLE_KEY = "search_filter_date_text_bundle_key"
    }
}
