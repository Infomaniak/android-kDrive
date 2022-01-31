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
package com.infomaniak.drive.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.absoluteValue

/**
 * It's a custom of [AndroidPdfReader]
 * @see [AndroidPdfReader](https://github.com/aditya09tyagi/AndroidPdfReader/blob/master/app/src/main/java/com/personal/android/androidpdfreader/util/PinchToZoomRecyclerView.kt)
 */
@SuppressLint("ClickableViewAccessibility")
class PinchToZoomRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    private var activePointerId = INVALID_POINTER_ID
    private var scaleDetector: ScaleGestureDetector? = null
    private var scaleFactor = 1f
    private var maxWidth = 0.0f
    private var maxHeight = 0.0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var posX = 0f
    private var posY = 0f
    private var width = 0f
    private var height = 0f
    private var minScale = 1f
    private var maxScale = 1.5f
    private var gestureDetector: GestureDetector? = null
    private var disallowParentInterceptTouch: Boolean = false

    var onClicked: (() -> Unit)? = null

    init {
        if (!isInEditMode) {
            scaleDetector = ScaleGestureDetector(getContext(), ScaleListener())
            gestureDetector = GestureDetector(getContext(), GestureListener())
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        width = MeasureSpec.getSize(widthMeasureSpec).toFloat() - paddingLeft - paddingRight
        height = MeasureSpec.getSize(heightMeasureSpec).toFloat() - paddingTop - paddingBottom
        setMeasuredDimension(width.toInt(), height.toInt())
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        try {
            return super.onInterceptTouchEvent(ev)
        } catch (ex: IllegalArgumentException) {
            ex.printStackTrace()
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)
        val action = event.action
        scaleDetector?.onTouchEvent(event)
        gestureDetector?.onTouchEvent(event)
        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                lastTouchX = x
                lastTouchY = y
                activePointerId = event.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {

                /* this line is replaced because here came below issue
                java.lang.IllegalArgumentException: pointerIndex out of range
                 ref http://stackoverflow.com/questions/6919292/pointerindex-out-of-range-android-multitouch
                */
                //final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                val pointerIndex = (action and MotionEvent.ACTION_POINTER_INDEX_MASK
                        shr MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                posX += dx
                posY += dy

                if (posX > 0.0f) posX = 0.0f else if (posX < maxWidth) posX = maxWidth
                if (posY > 0.0f) posY = 0.0f else if (posY < maxHeight) posY = maxHeight
                lastTouchX = x
                lastTouchY = y

                val posXAbs = posX.absoluteValue
                val maxWidthAbs = maxWidth.absoluteValue
                val disallowInterceptTouch = posXAbs > 0.0f && posXAbs < maxWidthAbs || disallowParentInterceptTouch
                requestDisallowInterceptTouchEvent(disallowInterceptTouch)

                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                activePointerId = INVALID_POINTER_ID
            }
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = INVALID_POINTER_ID
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = action and MotionEvent.ACTION_POINTER_INDEX_MASK shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = event.getX(newPointerIndex)
                    lastTouchY = event.getY(newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(posX, posY)
        canvas.scale(scaleFactor, scaleFactor)
        canvas.restore()
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        if (scaleFactor == 1.0f) {
            posX = 0.0f
            posY = 0.0f
        }
        canvas.translate(posX, posY)
        canvas.scale(scaleFactor, scaleFactor)
        super.dispatchDraw(canvas)
        canvas.restore()
        invalidate()
    }

    private inner class ScaleListener : SimpleOnScaleGestureListener() {
        private var preScaleFactor = MIN_SCALE

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor = preScaleFactor * detector.scaleFactor
            scaleFactor = MIN_SCALE.coerceAtLeast(scaleFactor.coerceAtMost(MAX_SCALE))
            maxWidth = width - width * scaleFactor
            maxHeight = height - height * scaleFactor
            invalidate()
            return false
        }

        override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
            preScaleFactor = scaleFactor
            parent.requestDisallowInterceptTouchEvent(true)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector?) {
            disallowParentInterceptTouch = scaleFactor != MIN_SCALE
            parent.requestDisallowInterceptTouchEvent(disallowParentInterceptTouch)
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
            onClicked?.invoke()
            return super.onSingleTapConfirmed(e)
        }

        override fun onDoubleTap(e: MotionEvent?): Boolean {
            scaleFactor = if (scaleFactor == maxScale) minScale else maxScale
            disallowParentInterceptTouch = scaleFactor != MIN_SCALE

            maxWidth = width - width * scaleFactor
            maxHeight = height - height * scaleFactor
            invalidate()
            return false
        }
    }

    companion object {
        private const val INVALID_POINTER_ID = -1

        private const val MIN_SCALE = 1f
        private const val MAX_SCALE = 3f
    }
}