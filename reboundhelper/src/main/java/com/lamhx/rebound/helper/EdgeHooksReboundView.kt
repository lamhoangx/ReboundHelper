package com.lamhx.rebound.helper

import android.content.Context
import android.graphics.PointF
import android.view.MotionEvent
import com.facebook.rebound.Spring
import kotlin.math.abs

/**
 * Created by lamhx on 5/31/20.
 * View's anchor is prioritize left or right edge.
 * Default position is top|right.
 */
class EdgeHooksReboundView(context: Context) : ReboundView(context) {
    private var realAlpha : Float = 1F
    companion object {
        const val THRESHOLD_X_VELOCITY = 1500
        const val THRESHOLD_Y_VELOCITY = 2000
    }

    init {
        realAlpha = alpha
    }

    override fun animationUpdate(xSpring: Spring, ySpring: Spring) {
        val x: Float = xSpring.currentValue.toFloat()
        val y: Float = ySpring.currentValue.toFloat()

        setPosition(x, y)
    }

    override fun releaseMotion(xSpring: Spring, ySpring: Spring) {
        val yVelocity = ySpring.velocity
        val xVelocity = xSpring.velocity
        var xEndValue = 0.0
        var yEndValue: Double = floatingController.positionY

        xEndValue = if (xVelocity > THRESHOLD_X_VELOCITY) {
            thresholdRight.toDouble()
        } else if (abs(xVelocity) > THRESHOLD_X_VELOCITY) {
            0.0
        } else {
            if (xSpring.currentValue > thresholdRight / 2) {
                thresholdRight.toDouble()
            } else {
                0.0
            }
        }
        val distanceInterpolate: Int
        distanceInterpolate = if (abs(yVelocity) > THRESHOLD_Y_VELOCITY) {
            (yVelocity * thresholdBottom).toInt()
        } else {
            (yVelocity / 10).toInt()
        }
        yEndValue += distanceInterpolate.toDouble()
        if (yEndValue < 0) {
            yEndValue = 0.0
        } else if (yEndValue > thresholdBottom) {
            yEndValue = thresholdBottom.toDouble()
        }
        floatingController.setEndValue(xEndValue, yEndValue)
    }

    override fun interpolateVelocity(velocityInput: PointF?): PointF {
        val velocity: PointF?
        if (velocityInput != null && (velocityInput.x != 0f || velocityInput.y != 0f)) {
            velocity = velocityInput
        } else {
            //fallback
            velocity = PointF(X_VELOCITY_DEFAULT, 0f)
            if (floatingController.positionX < thresholdRight / 2) {
                velocity.x = -X_VELOCITY_DEFAULT
            }
        }
        return velocity
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var onTouchHandle = floatingController.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> alpha = realAlpha * 0.75f
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> alpha = realAlpha
        }
        return onTouchHandle
    }
}