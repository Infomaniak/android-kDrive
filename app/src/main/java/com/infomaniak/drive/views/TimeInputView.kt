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
import android.text.format.DateFormat.is24HourFormat
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.fragment.app.FragmentManager
import com.google.android.material.datepicker.*
import com.google.android.material.datepicker.MaterialDatePicker.Builder.*
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.MaterialTimePicker.INPUT_MODE_CLOCK
import com.google.android.material.timepicker.TimeFormat
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.hours
import com.infomaniak.drive.utils.minutes
import com.infomaniak.lib.core.utils.FORMAT_DATE_HOUR_MINUTE
import com.infomaniak.lib.core.utils.format
import kotlinx.android.synthetic.main.view_time_input.view.*
import java.util.*

class TimeInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var hours: Int = 0
    private var minutes: Int = 0

    init {
        inflate(context, R.layout.view_time_input, this)
    }

    fun init(
        fragmentManager: FragmentManager,
        defaultDate: Date = Date(),
        onDateSet: ((hours: Int, minutes: Int) -> Unit)? = null
    ) {

        hours = defaultDate.hours()
        minutes = defaultDate.minutes()

        timeValueInput.apply {
            text = SpannableStringBuilder(defaultDate.format(FORMAT_DATE_HOUR_MINUTE))
            keyListener = null
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    showDatePicker(fragmentManager) { hours, minutes ->
                        this@TimeInputView.hours = hours
                        this@TimeInputView.minutes = minutes
                        val newDate = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hours)
                            set(Calendar.MINUTE, minutes)
                        }.time
                        text = SpannableStringBuilder(newDate.format(FORMAT_DATE_HOUR_MINUTE))
                        onDateSet?.invoke(hours, minutes)
                    }
                }
                performClick()
            }
        }
    }

    private fun showDatePicker(fragmentManager: FragmentManager, onDateSet: (hours: Int, minutes: Int) -> Unit) {

        val isSystem24Hour = is24HourFormat(context)
        val timeFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H

        val picker = MaterialTimePicker.Builder().run {
            setTimeFormat(timeFormat)
            setHour(hours)
            setMinute(minutes)
            setTitleText(R.string.inputExpirationTime)
            setInputMode(INPUT_MODE_CLOCK)
            build()
        }

        // TODO : Waiting https://github.com/material-components/material-components-android/issues/366 (icon padding issue)
        picker.apply {
            addOnNegativeButtonClickListener { this@TimeInputView.clearFocus() }
            addOnCancelListener { this@TimeInputView.clearFocus() }
            addOnDismissListener { this@TimeInputView.clearFocus() }
            addOnPositiveButtonClickListener {
                this@TimeInputView.clearFocus()
                onDateSet(picker.hour, picker.minute)
            }
            show(fragmentManager, picker.toString())
        }
    }
}