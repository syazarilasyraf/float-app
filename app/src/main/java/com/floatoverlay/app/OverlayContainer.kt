package com.floatoverlay.app

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

class OverlayContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    var dragStartListener: (() -> Unit)? = null
    var dragMoveListener: ((dx: Int, dy: Int) -> Unit)? = null
    var clickListener: (() -> Unit)? = null

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        return handleTouch(event)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        return handleTouch(event)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                dragStartListener?.invoke()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (abs(dx) > 10 || abs(dy) > 10) {
                    isDragging = true
                }
                if (isDragging) {
                    dragMoveListener?.invoke(dx, dy)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    clickListener?.invoke()
                }
                return true
            }
        }
        return false
    }
}
