/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.core.graphics.Insets
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.infomaniak.core.legacy.utils.toPx
import com.infomaniak.drive.R
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

    private var leftInsets = 0

    fun setExpandedColor(title: Int, subTitle: Int) {
        expandedTitleColor = title
        expandedSubTitleColor = subTitle
    }

    fun onWindowInsetsChanged(insets: Insets, subtitleToolbarView: SubtitleToolbarView, appBarLayout: View) {
        leftInsets = insets.left
        animateView(subtitleToolbarView, appBarLayout)
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: SubtitleToolbarView, dependency: View): Boolean {
        return dependency is AppBarLayout
    }

    override fun onDependentViewChanged(
        coordinatorLayout: CoordinatorLayout,
        subtitleToolbarView: SubtitleToolbarView,
        appBarLayout: View,
    ): Boolean {
        animateView(subtitleToolbarView, appBarLayout)
        return true
    }

    private fun animateView(subtitleToolbarView: SubtitleToolbarView, appBarLayout: View) {
        val maxScroll = (appBarLayout as AppBarLayout).totalScrollRange
        val appBarLayoutYPosition = abs(appBarLayout.y)
        val percentage = appBarLayoutYPosition / maxScroll.toFloat()

        val toolbar = appBarLayout.findViewById<MaterialToolbar>(R.id.toolbar)
        val toolbarTitle = subtitleToolbarView.findViewById<TextView>(R.id.title)
        val toolbarSubTitle = subtitleToolbarView.findViewById<TextView>(R.id.subTitle)

        var childPosition = ((appBarLayout.height + appBarLayout.y)
                - subtitleToolbarView.height
                - (getToolbarHeight(context) - subtitleToolbarView.height)
                * percentage / 2)
        childPosition -= expandedTitleMarginBottom * (1f - percentage)

        val layoutParams = subtitleToolbarView.layoutParams as CoordinatorLayout.LayoutParams

        val halfMaxScroll = maxScroll / 2
        if (appBarLayoutYPosition >= halfMaxScroll) {
            val layoutPercentage = (appBarLayoutYPosition - halfMaxScroll) / abs(halfMaxScroll)
            layoutParams.leftMargin =
                (layoutPercentage * collapsedTitleMarginStart).toInt() + leftInsets + expandedTitleMarginStart
            toolbarTitle.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                getTranslationOffset(layoutPercentage)
            )
        } else {
            layoutParams.leftMargin = leftInsets + expandedTitleMarginStart
        }

        subtitleToolbarView.layoutParams = layoutParams
        subtitleToolbarView.y = childPosition
        if (isNewState) {
            if (isExpanded) {
                textColorAnimation(toolbarTitle, collapsedTitleColor, expandedTitleColor)
                textColorAnimation(toolbarSubTitle, collapsedSubTitleColor, expandedSubTitleColor)
            } else {
                textColorAnimation(toolbarTitle, expandedTitleColor, collapsedTitleColor)
                textColorAnimation(toolbarSubTitle, expandedSubTitleColor, collapsedSubTitleColor)
            }

            toolbar.setNavigationIconTint(if (isExpanded) expandedTitleColor else collapsedTitleColor)
            isNewState = false
        }

        toolbarTitle.typeface = if (percentage < 1) expandedTitleFont else collapsedTitleFont
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
