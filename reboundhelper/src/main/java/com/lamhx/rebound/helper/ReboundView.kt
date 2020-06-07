package com.lamhx.rebound.helper

import android.content.Context
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener

/**
 * Created by lamhx on 5/28/20.
 */
abstract class ReboundView(
    context: Context?)
    : View(context), ReboundHelper.ReboundCallback {
    var mWidth = 0
    var mHeight = 0
    var mParentWidth = 0
    var mParentHeight = 0
    var thresholdLeft : Int = 0
    var thresholdRight : Int = 0
    var thresholdTop : Int = 0
    var thresholdBottom : Int = 0
    var floatingController =  ReboundHelper(getContext(), this, 1000)

    companion object {
        var X_VELOCITY_DEFAULT = 1000f
    }

    init {
        val globalLayoutListener: OnGlobalLayoutListener = object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeGlobalOnLayoutListener(this)
                performReady()
            }
        }
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        floatingController.onResume()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        floatingController.onPause()
    }

    /**
     * Initialize width/height of view and parent view.
     * Calculate threshold bound of view.
     */
    private fun performReady() {
        mWidth = width
        mHeight = height
        val parent = parent as View
        mParentWidth = parent.width
        mParentHeight = parent.height
        thresholdLeft = 0
        thresholdRight = mParentWidth - mWidth
        thresholdTop = 0
        thresholdBottom = mParentHeight - mHeight
        floatingController.setThresholdRect(
            thresholdLeft.toFloat(),
            thresholdTop.toFloat(),
            thresholdRight.toFloat(),
            thresholdBottom.toFloat()
        )
        onReady()
    }

    /**
     * Calling when this view is ready to use.
     */
    protected open fun onReady() {}
    protected fun setPosition(x: Float, y: Float) {
        animate().x(x).y(y).setDuration(0).start()
    }

    override fun prepareMovePosition(x: Float, y: Float, event: MotionEvent?): PointF? {
        return null
    }
}