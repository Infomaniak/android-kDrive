/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2017–2020 刘振林 "lzls".
 * Copyright (C) 2022-2024 Infomaniak Network SA
 *
 * All rights reserved.
 * File initially licensed under Apache license 2.0 : http://www.apache.org/licenses/LICENSE-2.0
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
 *
 * The initial code comes from this repository : https://github.com/lzls/SlidingItemMenuRecyclerView
*/
package com.infomaniak.drive.views.com.liuzhenlin.simrv

import android.view.animation.Interpolator
import kotlin.math.exp

/**
 * Controls the viscous fluid effect (how much of it).
 */
class ViscousFluidInterpolator(private val mViscousFluidScale: Float) : Interpolator {

    private val mViscousFluidNormalize: Float
    private val mViscousFluidOffset: Float

    private fun viscousFluid(input: Float): Float {
        var x = input
        x *= mViscousFluidScale
        if (x < 1.0f) {
            x -= 1.0f - exp(-x.toDouble()).toFloat()
        } else {
            val start = 0.36787944117f // 1/e == exp(-1)
            x = 1.0f - exp((1.0f - x).toDouble()).toFloat()
            x = start + x * (1.0f - start)
        }
        return x
    }

    override fun getInterpolation(input: Float): Float {
        val interpolated = mViscousFluidNormalize * viscousFluid(input)
        return if (interpolated > 0) {
            interpolated + mViscousFluidOffset
        } else interpolated
    }

    init {
        // must be set to 1.0 (used in viscousFluid())
        mViscousFluidNormalize = 1.0f / viscousFluid(1.0f)
        // account for very small floating-point error
        mViscousFluidOffset = 1.0f - mViscousFluidNormalize * viscousFluid(1.0f)
    }
}
