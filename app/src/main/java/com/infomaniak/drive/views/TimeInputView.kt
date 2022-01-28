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
package com.infomaniak.drive.views

import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnCancelListener
import android.content.DialogInterface.OnDismissListener
import android.text.SpannableStringBuilder
import android.text.format.DateFormat.is24HourFormat
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.fragment.app.FragmentManager
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.hours
import com.infomaniak.drive.utils.isNightModeEnabled
import com.infomaniak.drive.utils.minutes
import com.infomaniak.lib.core.utils.FORMAT_DATE_HOUR_MINUTE
import com.infomaniak.lib.core.utils.format
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog.OnTimeSetListener
import com.wdullaer.materialdatetimepicker.time.Timepoint
import kotlinx.android.synthetic.main.view_time_input.view.*
import java.util.*

class TimeInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), OnTimeSetListener, OnDismissListener, OnCancelListener {

    private lateinit var mOnDatePicked: (hours: Int, minutes: Int) -> Unit
    private var hours: Int = 0
    private var minutes: Int = 0

    init {
        inflate(context, R.layout.view_time_input, this)
    }

    fun init(
        fragmentManager: FragmentManager,
        defaultDate: Date = Date(),
        onDateSet: (hours: Int, minutes: Int) -> Unit,
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
                        onDateSet(hours, minutes)
                    }
                }
                performClick()
            }
        }
    }

    override fun onTimeSet(view: TimePickerDialog?, hourOfDay: Int, minute: Int, second: Int) {
        this@TimeInputView.clearFocus()
        mOnDatePicked(hourOfDay, minute)
    }

    override fun onDismiss(dialog: DialogInterface?) {
        this@TimeInputView.clearFocus()
    }

    override fun onCancel(dialog: DialogInterface?) {
        this@TimeInputView.clearFocus()
    }

    private fun showDatePicker(fragmentManager: FragmentManager, onDatePicked: (hours: Int, minutes: Int) -> Unit) {

        mOnDatePicked = onDatePicked

        val acceptableTimes = mutableListOf<Timepoint>().apply {
            for (h in FIRST_HOUR..LAST_HOUR) {
                add(Timepoint(h, START_OF_HOUR))
                add(Timepoint(h, MIDDLE_OF_HOUR))
                if (h == LAST_HOUR)
                    add(Timepoint(h, END_OF_HOUR))
            }
        }.toTypedArray()

        // TODO : Waiting https://github.com/material-components/material-components-android/issues/366 (icon padding issue)
        TimePickerDialog.newInstance(this, hours, minutes, is24HourFormat(context)).apply {
            isThemeDark = this@TimeInputView.resources.isNightModeEnabled()
            setSelectableTimes(acceptableTimes)
            dismissOnPause(true)
            show(fragmentManager, this@TimeInputView.toString())
        }
    }

    private companion object {
        const val FIRST_HOUR = 0
        const val LAST_HOUR = 23
        const val START_OF_HOUR = 0
        const val MIDDLE_OF_HOUR = 30
        const val END_OF_HOUR = 59
    }
}
