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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.collection.SimpleArrayMap
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.roundToInt

class SlidingItemMenuRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {

    /**
     * Distance to travel before drag may begin
     */
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val touchX = FloatArray(2)
    private val touchY = FloatArray(2)

    /**
     * Minimum gesture speed along the x axis to automatically scroll item views
     * 200 dp/s
     */
    private val itemMinimumFlingVelocity = 200f * resources.displayMetrics.density

    /**
     * The bounds of the currently touched item View [.mActiveItem] (relative to current view).
     */
    private val activeItemBounds = Rect()

    /**
     * The bounds of the currently touched item view's menu (relative to current view).
     */
    private val activeItemMenuBounds = Rect()

    /**
     * The set of opened item views
     */
    private val openedItems: MutableList<ViewGroup> = LinkedList()

    /**
     * It is enabled to scroll item views in touch mode or not
     */
    var isItemDraggable = true

    /**
     * Time interval in milliseconds of automatically scrolling item views
     */
    var itemScrollDuration = DEFAULT_ITEM_SCROLL_DURATION

    /**
     * True, if an item view is being dragged by the user.
     */
    private var isItemBeingDragged = false

    /**
     * Whether or not some item view is fully open when this view receives the
     * [MotionEvent.ACTION_DOWN] event.
     */
    private var hasItemFullyOpenOnActionDown = false
    private var downX = 0
    private var downY = 0
    private var velocityTracker: VelocityTracker? = null

    /**
     * The item view that is currently being touched or dragged by the user
     */
    private var activeItem: ViewGroup? = null

    /**
     * The item view that is fully open or to be opened through the animator associated to it
     */
    private var fullyOpenedItem: ViewGroup? = null

    private fun childHasMenu(itemView: ViewGroup): Boolean {
        if (itemView.isGone) return false

        val itemChildCount = itemView.childCount
        val itemLastChild = itemView.getChildAt(if (itemChildCount > 1) itemChildCount - 1 else 1) as? FrameLayout ?: return false
        val menuItemCount = itemLastChild.childCount
        val menuItemWidths = IntArray(menuItemCount)
        var itemMenuWidth = 0
        for (i in 0 until menuItemCount) {
            menuItemWidths[i] = (itemLastChild.getChildAt(i) as FrameLayout)
                .getChildAt(0)
                .width
            itemMenuWidth += menuItemWidths[i]
        }

        if (itemMenuWidth > 0) {
            itemView.setTag(TAG_ITEM_MENU_WIDTH, itemMenuWidth)
            itemView.setTag(TAG_MENU_ITEM_WIDTHS, menuItemWidths)
            return true
        }
        return false
    }

    private fun resolveActiveItemMenuBounds() {
        activeItem?.let { viewGroup ->
            val itemMenuWidth = viewGroup.getTag(TAG_ITEM_MENU_WIDTH) as Int
            val left = viewGroup.right - itemMenuWidth
            val top = activeItemBounds.top
            val right = viewGroup.right
            val bottom = activeItemBounds.bottom
            activeItemMenuBounds[left, top, right] = bottom
        }
    }

    override fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {

        // Reset things for a new event stream, just in case we didn't get the whole previous stream.
        if (motionEvent.action == MotionEvent.ACTION_DOWN) resetTouch()

        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(motionEvent)

        var intercept = false

        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = motionEvent.x.roundToInt()
                downY = motionEvent.y.roundToInt()
                markCurrentTouchPoint(downX.toFloat(), downY.toFloat())
                var i = childCount - 1
                while (i >= 0) {
                    val child = getChildAt(i)
                    if (child !is ViewGroup) {
                        i--
                        continue
                    }
                    var itemView = child
                    itemView.getHitRect(activeItemBounds)
                    if (!activeItemBounds.contains(downX, downY)) {
                        i--
                        continue
                    }
                    itemView = itemView.getChildAt(0) as ViewGroup //kDrive
                    if (childHasMenu(itemView)) activeItem = itemView
                    break
                }
                if (openedItems.size == 0) return intercept || super.onInterceptTouchEvent(motionEvent)
                // Disallow our parent Views to intercept the touch events so long as there is
                // at least one item view in the open or being closed state.
                requestParentDisallowInterceptTouchEvent()
                if (fullyOpenedItem != null) {
                    hasItemFullyOpenOnActionDown = true
                    if (activeItem === fullyOpenedItem) {
                        resolveActiveItemMenuBounds()
                        // If the user's finger downs on the completely opened itemView's menu area,
                        // do not intercept the subsequent touch events (ACTION_MOVE, ACTION_UP, etc.)
                        // as we receive the ACTION_DOWN event.
                        if (activeItemMenuBounds.contains(downX, downY)) {
                            return intercept || super.onInterceptTouchEvent(motionEvent)
                            // If the user's finger downs on the fully opened itemView but not on
                            // its menu, then we need to intercept them.
                        } else if (activeItemBounds.contains(downX, downY)) {
                            return true
                        }
                    }
                    // If 1) the fully opened itemView is not the current one or 2) the user's
                    // finger downs outside of the area in which this view displays the itemViews,
                    // make the itemView's menu hidden and intercept the subsequent touch events.
                    releaseItemViewInternal(fullyOpenedItem, itemScrollDuration)
                }
                // Intercept the next touch events as long as there exists some item view open
                // (full open is not necessary for it). This prevents the onClick() method of
                // the pressed child from being called in the pending ACTION_UP event.
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                markCurrentTouchPoint(motionEvent.x, motionEvent.y)
                intercept = tryHandleItemScrollingEvent()
                // If the user initially put his/her finger down on the fully opened itemView's menu,
                // disallow our parent class to intercept the touch events since we will do that
                // as the user tends to scroll the current touched itemView horizontally.
                if (hasItemFullyOpenOnActionDown && activeItemMenuBounds.contains(downX, downY)) return intercept
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // If the user initially placed his/her finger on the fully opened itemView's menu
                // and has clicked it or has not scrolled that itemView, hide it as his/her last
                // finger touching the screen lifts.
                if (hasItemFullyOpenOnActionDown && activeItemMenuBounds.contains(downX, downY)) releaseItemView(true)
                clearTouch()
            }
        }
        return intercept || super.onInterceptTouchEvent(motionEvent)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {

        // Makes the vertical scroll bar disappear while an itemView is being dragged.
        if (isVerticalScrollBarEnabled) super.setVerticalScrollBarEnabled(!isItemBeingDragged)

        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(motionEvent)

        when (motionEvent.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_POINTER_DOWN -> whenPointerUpOrDown()?.let { return it }
            MotionEvent.ACTION_MOVE -> whenMove(motionEvent)?.let { return it }
            MotionEvent.ACTION_UP -> whenUp(motionEvent)?.let { return it }
            MotionEvent.ACTION_CANCEL -> cancelTouch()
        }

        return super.onTouchEvent(motionEvent)
    }

    private fun whenPointerUpOrDown(): Boolean? {
        if (isItemBeingDragged || hasItemFullyOpenOnActionDown || openedItems.size > 0) return true
        return null
    }

    private fun whenMove(motionEvent: MotionEvent): Boolean? {
        markCurrentTouchPoint(motionEvent.x, motionEvent.y)
        if (!isItemDraggable && cancelTouch()) return true

        if (isItemBeingDragged) {
            // Positive when the user's finger slides towards the right.
            var dx = touchX[touchX.size - 1] - touchX[touchX.size - 2]
            activeItem?.let {
                // Positive when the itemView scrolls towards the right.
                val translationX = it.getChildAt(0).translationX
                val finalXFromEndToStart = -(it.getTag(TAG_ITEM_MENU_WIDTH) as Int)
                // Swipe the itemView towards the horizontal start over the width of the itemView's menu.
                if (dx + translationX < finalXFromEndToStart) {
                    dx /= 3f
                    // Swipe the itemView towards the end of horizontal to (0,0).
                } else if (dx + translationX > 0) {
                    dx = 0 - translationX
                }
                translateItemViewXBy(it, dx)
            }

            // Consume this touch event and do not invoke the method onTouchEvent(e) of
            // the parent class to temporarily make this view unable to scroll up or down.
            return true
        } else {
            // If there existed itemView whose menu was fully open when the user initially
            // put his/her finger down, always consume the touch event and only when the
            // item has a tend of scrolling horizontally will we handle the next events.
            if (hasItemFullyOpenOnActionDown or tryHandleItemScrollingEvent()) return true

            // Disallow current view to scroll while an/some item view(s) is/are scrolling.
            if (openedItems.size > 0) return true
        }

        return null
    }

    private fun whenUp(motionEvent: MotionEvent): Boolean? {

        if (isItemDraggable && isItemBeingDragged) activeItem?.let { currentItem ->

            val translationX = currentItem.getChildAt(0).translationX
            val itemMenuWidth = currentItem.getTag(TAG_ITEM_MENU_WIDTH) as Int
            when (translationX) {
                0.0f -> Unit // itemView's menu is closed
                -itemMenuWidth.toFloat() -> { // itemView's menu is totally opened
                    fullyOpenedItem = currentItem
                }
                else -> {
                    handleItemViewMenuPartiallyOpened(currentItem, itemMenuWidth, motionEvent, translationX)?.let { return it }
                }
            }

            clearTouch()
            cancelParentTouch(motionEvent)
            return true // Returns true here in case of a fling started in this up event.
        }

        cancelTouch()
        return null
    }

    private fun handleItemViewMenuPartiallyOpened(
        currentItem: ViewGroup,
        itemMenuWidth: Int,
        motionEvent: MotionEvent,
        translationX: Float,
    ): Boolean? {

        val dx = touchX[touchX.size - 1] - touchX[touchX.size - 2]
        velocityTracker?.computeCurrentVelocity(1000)
        val velocityX = abs(velocityTracker?.xVelocity ?: 0F)

        // If the speed at which the user's finger lifted is greater than 200 dp/s
        // while user was scrolling itemView towards the horizontal start,
        // make it automatically scroll to open and show its menu.
        if (dx < 0 && velocityX >= itemMinimumFlingVelocity) {
            smoothTranslateItemViewXTo(currentItem, -itemMenuWidth.toFloat(), itemScrollDuration)
            fullyOpenedItem = currentItem
            clearTouch()
            cancelParentTouch(motionEvent)
            return true

            // If the speed at which the user's finger lifted is greater than 200 dp/s
            // while user was scrolling itemView towards the end of horizontal,
            // make its menu hidden.
        } else if (dx > 0 && velocityX >= itemMinimumFlingVelocity) {
            releaseItemView(true)
            clearTouch()
            cancelParentTouch(motionEvent)
            return true
        }

        val middle = itemMenuWidth / 2.0f
        // If the sliding distance is less than half of its slideable distance, hide its menu,
        if (abs(translationX) < middle) {
            releaseItemView(true)
        } else { // else open its menu.
            smoothTranslateItemViewXTo(currentItem, -itemMenuWidth.toFloat(), itemScrollDuration)
            fullyOpenedItem = currentItem
        }

        return null
    }

    private fun markCurrentTouchPoint(x: Float, y: Float) {
        System.arraycopy(touchX, 1, touchX, 0, touchX.size - 1)
        touchX[touchX.size - 1] = x
        System.arraycopy(touchY, 1, touchY, 0, touchY.size - 1)
        touchY[touchY.size - 1] = y
    }

    private fun tryHandleItemScrollingEvent(): Boolean {
        if (activeItem == null /* There's no scrollable itemView being touched by user */
            || !isItemDraggable /* Unable to scroll it */
            || scrollState != SCROLL_STATE_IDLE /* The list may be currently scrolling */) {
            return false
        }
        // The layout's orientation may not be vertical.
        if (layoutManager?.canScrollHorizontally() == true) return false
        val absDy = abs(touchY[touchY.size - 1] - downY)
        if (absDy <= touchSlop) {
            val dx = touchX[touchX.size - 1] - downX
            isItemBeingDragged = if (openedItems.size == 0) dx < -touchSlop else abs(dx) > touchSlop
            if (isItemBeingDragged) {
                requestParentDisallowInterceptTouchEvent()
                return true
            }
        }
        return false
    }

    private fun requestParentDisallowInterceptTouchEvent() {
        val parent = parent
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun cancelTouch(animate: Boolean = true): Boolean {
        if (isItemBeingDragged) {
            releaseItemView(animate)
            clearTouch()
            return true
        }
        // 1. If the itemView previously opened equals the current touched one and
        //    the user hasn't scrolled it since he/she initially put his/her finger down,
        //    hide it on the movements canceled.
        // 2. If the previously opened itemView differs from the one currently touched,
        //    and the current one has not been scrolled at all, set 'mActiveItem' to null.
        if (hasItemFullyOpenOnActionDown) {
            if (activeItem === fullyOpenedItem) releaseItemView(animate)
            clearTouch()
            return true
        }
        return false
    }

    private fun clearTouch() {
        velocityTracker?.recycle()
        velocityTracker = null
        resetTouch()
    }

    private fun resetTouch() {
        activeItem = null
        hasItemFullyOpenOnActionDown = false
        activeItemBounds.setEmpty()
        activeItemMenuBounds.setEmpty()
        isItemBeingDragged = false
        velocityTracker?.clear()
    }

    private fun cancelParentTouch(motionEvent: MotionEvent) {
        val action = motionEvent.action
        motionEvent.action = MotionEvent.ACTION_CANCEL
        super.onTouchEvent(motionEvent)
        motionEvent.action = action
    }

    /**
     * Smoothly scrolls the current item view whose menu is open back to its original position.
     *
     * @param animate whether this scroll should be smooth
     */
    @JvmOverloads
    fun releaseItemView(animate: Boolean = true) {
        releaseItemViewInternal(
            if (isItemBeingDragged) activeItem else fullyOpenedItem,
            if (animate) itemScrollDuration else 0,
        )
    }

    private fun releaseItemViewInternal(itemView: ViewGroup?, duration: Int) {
        if (itemView != null) {
            if (duration > 0) {
                smoothTranslateItemViewXTo(itemView, 0f, duration)
            } else {
                translateItemViewXBy(itemView, itemView.getChildAt(0).translationX)
            }
            if (fullyOpenedItem === itemView) fullyOpenedItem = null
        }
    }

    /**
     * Opens the menu of the item view at the specified adapter position
     *
     * @param position the position of the item in the data set of the adapter
     * @param animate  whether this scroll should be smooth
     * @return true if the menu of the child view that represents the given position can be opened;
     * false if the position is not laid out or the item does not have a menu.
     * Smoothly opens the menu of the item view at the specified adapter position
     */
    @JvmOverloads
    fun openItemAtPosition(position: Int, animate: Boolean = true): Boolean {
        var itemView = layoutManager?.findViewByPosition(position) as? ViewGroup ?: return false
        itemView = itemView.getChildAt(0) as ViewGroup //kDrive
        if (fullyOpenedItem !== itemView && childHasMenu(itemView)) {
            // First, cancels the item view being touched or previously fully opened (if any)
            if (!cancelTouch(animate)) releaseItemView(animate)
            smoothTranslateItemViewXTo(
                itemView, -(itemView.getTag(TAG_ITEM_MENU_WIDTH) as Int).toFloat(),
                if (animate) itemScrollDuration else 0
            )
            fullyOpenedItem = itemView
            return true
        }
        return false
    }

    private fun smoothTranslateItemViewXTo(itemView: ViewGroup, x: Float, duration: Int) {
        smoothTranslateItemViewXBy(itemView, x - itemView.getChildAt(0).translationX, duration)
    }

    private fun smoothTranslateItemViewXBy(itemView: ViewGroup, dx: Float, duration: Int) {
        var animator = itemView.getTag(TAG_ITEM_ANIMATOR) as TranslateItemViewXAnimator?
        if (dx != 0f && duration > 0) {
            var canceled = false
            if (animator == null) {
                animator = TranslateItemViewXAnimator(this, itemView)
                itemView.setTag(TAG_ITEM_ANIMATOR, animator)
            } else if (animator.isRunning) {
                animator.removeListener(animator.listener)
                animator.cancel()
                canceled = true
            }
            animator.setFloatValues(0f, dx)
            val interpolator = if (dx < 0) sOvershootInterpolator else sViscousFluidInterpolator
            animator.interpolator = interpolator
            animator.duration = duration.toLong()
            animator.start()
            if (canceled) animator.addListener(animator.listener)
        } else {
            // Checks if there is an animator running for the given item view even if dx == 0
            if (animator != null && animator.isRunning) animator.cancel()
            // If duration <= 0, then scroll the 'itemView' directly to prevent a redundant call
            // to the animator.
            baseTranslateItemViewXBy(itemView, dx)
        }
    }

    private fun translateItemViewXBy(itemView: ViewGroup, dx: Float) {
        val animator = itemView.getTag(TAG_ITEM_ANIMATOR) as TranslateItemViewXAnimator?
        if (animator != null && animator.isRunning) {
            // Cancels the running animator associated to the 'itemView' as we horizontally
            // scroll it to a position immediately to avoid inconsistencies in its translation X.
            animator.cancel()
        }
        baseTranslateItemViewXBy(itemView, dx)
    }

    /*
     * This method does not cancel the translation animator of the 'itemView', for which it is used
     * to update the item view's horizontal scrolled position.
     */
    /*synthetic*/
    fun baseTranslateItemViewXBy(itemView: ViewGroup, dx: Float) {
        if (dx == 0f) return

        val translationX = itemView.getChildAt(0).translationX + dx
        val itemMenuWidth = itemView.getTag(TAG_ITEM_MENU_WIDTH) as Int
        if (translationX > -itemMenuWidth * 0.05f) {
            openedItems.remove(itemView)
        } else if (!openedItems.contains(itemView)) {
            openedItems.add(itemView)
        }

        val itemChildCount = itemView.childCount
        for (i in 0 until itemChildCount) itemView.getChildAt(i).translationX = translationX

        val itemMenu = itemView.getChildAt(itemChildCount - 1) as FrameLayout
        val menuItemWidths = itemView.getTag(TAG_MENU_ITEM_WIDTHS) as IntArray
        var menuItemFrameDx = 0f
        var i = 1
        val menuItemCount = itemMenu.childCount
        while (i < menuItemCount) {
            val menuItemFrame = itemMenu.getChildAt(i) as FrameLayout
            menuItemFrameDx -= dx * menuItemWidths[i - 1].toFloat() / itemMenuWidth.toFloat()
            menuItemFrame.translationX = menuItemFrame.translationX + menuItemFrameDx
            i++
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseItemViewInternal(fullyOpenedItem, 0)
        if (openedItems.size > 0) {
            val openedItems = openedItems.toTypedArray()
            for (openedItem in openedItems) {
                val animator = openedItem.getTag(TAG_ITEM_ANIMATOR) as Animator?
                if (animator != null && animator.isRunning) animator.end()
            }
            this.openedItems.clear()
        }
    }

    private class TranslateItemViewXAnimator(parent: SlidingItemMenuRecyclerView, itemView: ViewGroup) : ValueAnimator() {

        val listener: AnimatorListener
        var cachedDeltaTransX = 0f

        override fun start() {
            // NOTE: 'cachedDeltaTransX' MUST be reset before super.start() is invoked
            // for the reason that 'onAnimationUpdate' will be called in the super method
            // on platforms prior to Nougat.
            cachedDeltaTransX = 0f
            super.start()
        }

        init {
            listener = object : AnimatorListenerAdapter() {
                val childrenLayerTypes = SimpleArrayMap<View, Int>(0)
                fun ensureChildrenLayerTypes() {
                    val itemChildCount = itemView.childCount
                    val itemMenu = itemView.getChildAt(itemChildCount - 1) as ViewGroup
                    val menuItemCount = itemMenu.childCount

                    // We do not know whether the cached children are valid or not, so just
                    // clear the Map and re-put some children into it, of which the layer types
                    // will also be up-to-date.
                    childrenLayerTypes.clear()
                    childrenLayerTypes.ensureCapacity(itemChildCount - 1 + menuItemCount)
                    for (i in 0 until itemChildCount - 1) {
                        val itemChild = itemView.getChildAt(i)
                        childrenLayerTypes.put(itemChild, itemChild.layerType)
                    }
                    for (i in 0 until menuItemCount) {
                        val menuItemFrame = itemMenu.getChildAt(i)
                        childrenLayerTypes.put(menuItemFrame, menuItemFrame.layerType)
                    }
                }

                @SuppressLint("ObsoleteSdkInt")
                override fun onAnimationStart(animation: Animator) {
                    ensureChildrenLayerTypes()
                    for (i in childrenLayerTypes.size() - 1 downTo 0) {
                        childrenLayerTypes.keyAt(i).apply {
                            post {
                                this.setLayerType(LAYER_TYPE_HARDWARE, null)
                                if (ViewCompat.isAttachedToWindow(this)) this.buildLayer()
                            }
                        }
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    for (i in childrenLayerTypes.size() - 1 downTo 0) {
                        childrenLayerTypes.keyAt(i).apply {
                            post { setLayerType(childrenLayerTypes.valueAt(i), null) }
                        }

                    }
                }
            }
            addListener(listener)
            addUpdateListener { animation ->
                val deltaTransX = animation.animatedValue as Float
                parent.baseTranslateItemViewXBy(itemView, deltaTransX - cachedDeltaTransX)
                cachedDeltaTransX = deltaTransX
            }
        }
    }

    companion object {
        /**
         * Default value of [.mItemScrollDuration] if no value is set for it
         */
        const val DEFAULT_ITEM_SCROLL_DURATION = 500 // ms

        /**
         * Tag used to get the width of an item view's menu
         */
        private const val TAG_ITEM_MENU_WIDTH = R.id.tag_itemMenuWidth

        /**
         * Tag used to get the widths of the menu items of an item view
         */
        private const val TAG_MENU_ITEM_WIDTHS = R.id.tag_menuItemWidths

        /**
         * Tag used to get the animator of the item view to which it associated
         */
        private const val TAG_ITEM_ANIMATOR = R.id.tag_itemAnimator

        private val sViscousFluidInterpolator: Interpolator = ViscousFluidInterpolator(6.66f)
        private val sOvershootInterpolator: Interpolator = OvershootInterpolator(1.0f)
    }
}
