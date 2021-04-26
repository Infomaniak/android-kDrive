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

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomappbar.BottomAppBarTopEdgeTreatment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.infomaniak.drive.R

@SuppressLint("RestrictedApi")
class FabBottomNavigationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BottomNavigationView(context, attrs, defStyleAttr) {

    private var materialShapeDrawable: MaterialShapeDrawable

    init {
        val typedArray =
            context.theme.obtainStyledAttributes(attrs, R.styleable.FabBottomNavigationView, 0, 0)
        val fabSize = typedArray.getDimension(R.styleable.FabBottomNavigationView_fab_size, 0F)
        val fabCradleMargin =
            typedArray.getDimension(R.styleable.FabBottomNavigationView_fab_cradle_margin, 0F)
        val fabCradleRoundedCornerRadius =
            typedArray.getDimension(
                R.styleable.FabBottomNavigationView_fab_cradle_rounded_corner_radius,
                0F
            )
        val cradleVerticalOffset =
            typedArray.getDimension(R.styleable.FabBottomNavigationView_cradle_vertical_offset, 0F)

        val topCurvedEdgeTreatment = BottomAppBarTopEdgeTreatment(
            fabCradleMargin,
            fabCradleRoundedCornerRadius,
            cradleVerticalOffset
        ).apply {
            fabDiameter = fabSize
        }

        val shapeAppearanceModel = ShapeAppearanceModel.Builder()
            .setTopEdge(topCurvedEdgeTreatment)
            .setAllCornerSizes(context.resources.getDimension(R.dimen.bottomNavigationRadius))
            .build()

        materialShapeDrawable = MaterialShapeDrawable(shapeAppearanceModel).apply {
            setTint(ContextCompat.getColor(context, R.color.appBar))
            paintStyle = Paint.Style.FILL_AND_STROKE
        }

        background = materialShapeDrawable
    }

    fun transform(fab: FloatingActionButton) {
        if (fab.isVisible) {
            fab.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                override fun onHidden(fab: FloatingActionButton?) {
                    super.onHidden(fab)
                    ValueAnimator.ofFloat(materialShapeDrawable.interpolation, 0F).apply {
                        addUpdateListener {
                            materialShapeDrawable.interpolation = it.animatedValue as Float
                        }
                        start()
                    }
                }
            })
        } else {
            ValueAnimator.ofFloat(materialShapeDrawable.interpolation, 1F).apply {
                addUpdateListener {
                    materialShapeDrawable.interpolation = it.animatedValue as Float
                }
                doOnEnd { fab.show() }
                start()
            }
        }
    }
}
