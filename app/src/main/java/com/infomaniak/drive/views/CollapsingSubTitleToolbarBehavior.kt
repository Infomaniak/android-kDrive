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

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.appbar.AppBarLayout
import com.infomaniak.drive.R
import com.infomaniak.lib.core.utils.toPx
import kotlinx.android.synthetic.main.fragment_file_details.view.toolbar
import kotlinx.android.synthetic.main.view_subtitle_toolbar.view.subTitle
import kotlinx.android.synthetic.main.view_subtitle_toolbar.view.title
import kotlin.math.abs

class CollapsingSubTitleToolbarBehavior @JvmOverloads constructor(
    private val context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<SubtitleToolbarView>(context, attrs) {
    private val collapsedTitleColor = ContextCompat.getColor(context, R.color.title)
    private val collapsedSubTitleColor = ContextCompat.getColor(context, R.color.secondaryText)
    private val collapsedTitleFont = ResourcesCompat.getFont(context, R.font.suisseintl_medium)
    private val collapsedTitleMarginStart = 34.toPx()
    private val collapsedTitleSize = context.resources.getDimensionPixelSize(R.dimen.h3).toFloat()
    private var expandedTitleColor = Color.WHITE
    private var expandedSubTitleColor = Color.WHITE
    private val expandedTitleFont = ResourcesCompat.getFont(context, R.font.suisseintl_bold)
    private val expandedTitleMarginBottom: Int = context.resources.getDimensionPixelOffset(R.dimen.marginStandardMedium)
    private val expandedTitleMarginStart: Int = context.resources.getDimensionPixelOffset(R.dimen.marginStandard)
    private val expandedTitleSize = context.resources.getDimensionPixelSize(R.dimen.h1).toFloat()

    var isExpanded = true
    var isNewState = false

    fun setExpandedColor(title: Int, subTitle: Int) {
        expandedTitleColor = title
        expandedSubTitleColor = subTitle
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: SubtitleToolbarView, dependency: View): Boolean {
        return dependency is AppBarLayout
    }

    override fun onDependentViewChanged(
        coordinatorLayout: CoordinatorLayout,
        subtitleToolbarView: SubtitleToolbarView,
        appBarLayout: View
    ): Boolean {
        val maxScroll = (appBarLayout as AppBarLayout).totalScrollRange
        val appBarLayoutYPosition = abs(appBarLayout.getY())
        val percentage = appBarLayoutYPosition / maxScroll.toFloat()

        var childPosition = ((appBarLayout.getHeight() + appBarLayout.getY())
                - subtitleToolbarView.height
                - (getToolbarHeight(context) - subtitleToolbarView.height)
                * percentage / 2)
        childPosition -= expandedTitleMarginBottom * (1f - percentage)

        val layoutParams = subtitleToolbarView.layoutParams as CoordinatorLayout.LayoutParams

        val halfMaxScroll = maxScroll / 2
        if (appBarLayoutYPosition >= halfMaxScroll) {
            val layoutPercentage = (appBarLayoutYPosition - halfMaxScroll) / abs(halfMaxScroll)
            layoutParams.leftMargin = (layoutPercentage * collapsedTitleMarginStart).toInt() + expandedTitleMarginStart

            subtitleToolbarView.title.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                getTranslationOffset(layoutPercentage)
            )
        } else {
            layoutParams.leftMargin = expandedTitleMarginStart
        }

        subtitleToolbarView.layoutParams = layoutParams
        subtitleToolbarView.y = childPosition
        if (isNewState) {
            if (isExpanded) {
                textColorAnimation(subtitleToolbarView.title, collapsedTitleColor, expandedTitleColor)
                textColorAnimation(subtitleToolbarView.subTitle, collapsedSubTitleColor, expandedSubTitleColor)
            } else {
                textColorAnimation(subtitleToolbarView.title, expandedTitleColor, collapsedTitleColor)
                textColorAnimation(subtitleToolbarView.subTitle, expandedSubTitleColor, collapsedSubTitleColor)
            }

            appBarLayout.toolbar.setNavigationIconTint(if (isExpanded) expandedTitleColor else collapsedTitleColor)
            isNewState = false
        }

        subtitleToolbarView.title.typeface = if (percentage < 1) expandedTitleFont else collapsedTitleFont

        return true
    }

    private fun textColorAnimation(textView: TextView, startColor: Int, endColor: Int) {
        ObjectAnimator.ofInt(
            textView, "textColor",
            startColor, endColor
        ).apply {
            setEvaluator(ArgbEvaluator())
            start()
        }
    }

    private fun getTranslationOffset(ratio: Float): Float {
        return expandedTitleSize + ratio * (collapsedTitleSize - expandedTitleSize)
    }

    companion object {

        private fun getToolbarHeight(context: Context): Int {
            val typedValue = TypedValue()
            return if (context.theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
                TypedValue.complexToDimensionPixelSize(typedValue.data, context.resources.displayMetrics)
            } else 0
        }
    }
}
