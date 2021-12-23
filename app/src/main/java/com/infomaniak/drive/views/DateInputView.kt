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
package com.infomaniak.drive.views

import android.content.Context
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.fragment.app.FragmentManager
import com.google.android.material.datepicker.*
import com.infomaniak.drive.R
import com.infomaniak.lib.core.utils.format
import kotlinx.android.synthetic.main.view_date_input.view.*
import java.util.*

class DateInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var currentCalendarDate: Date

    init {
        inflate(context, R.layout.view_date_input, this)
    }

    fun init(fragmentManager: FragmentManager, defaultDate: Date = Date(), onDateSet: ((timestamp: Long) -> Unit)? = null) {
        currentCalendarDate = defaultDate

        dateValueInput.apply {
            text = SpannableStringBuilder(currentCalendarDate.format())
            keyListener = null
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    showDatePicker(fragmentManager, currentCalendarDate) { calendarResult ->
                        currentCalendarDate = Date(calendarResult)
                        text = SpannableStringBuilder(currentCalendarDate.format())
                        onDateSet?.invoke(calendarResult)
                    }
                }
                performClick()
            }
        }
    }

    fun getCurrentTimestampValue(): Long? =
        if (this::currentCalendarDate.isInitialized) currentCalendarDate.time / 1_000L else null

    private fun showDatePicker(fragmentManager: FragmentManager, defaultDate: Date, onDateSet: (timestamp: Long) -> Unit) {

        val startDate = Date().time
        // TODO before the year 2038: https://en.wikipedia.org/wiki/Year_2038_problem
        val endDate = Calendar.getInstance().apply { set(2038, 0, 0) }.timeInMillis

        val calendarConstraints = CalendarConstraints.Builder()
            .setStart(startDate)
            .setEnd(endDate)
            .setValidator(DateValidatorPointForward.now())
            .build()

        val materialDatePickerBuilder = MaterialDatePicker.Builder
            .datePicker()
            .setTheme(R.style.MaterialCalendarThemeBackground)
            .setSelection(defaultDate.time)
            .setCalendarConstraints(calendarConstraints)

        // TODO : Waiting https://github.com/material-components/material-components-android/issues/366 (icon padding issue)
        materialDatePickerBuilder.build().apply {
            addOnNegativeButtonClickListener { this@DateInputView.clearFocus() }
            addOnCancelListener { this@DateInputView.clearFocus() }
            addOnPositiveButtonClickListener {
                this@DateInputView.clearFocus()
                onDateSet(it)
            }
            show(fragmentManager, materialDatePickerBuilder.toString())
        }
    }
}