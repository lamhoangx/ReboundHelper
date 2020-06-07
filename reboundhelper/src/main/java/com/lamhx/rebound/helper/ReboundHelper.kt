package com.lamhx.rebound.helper

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import androidx.annotation.VisibleForTesting
import com.facebook.rebound.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Created by lamhx on 5/28/20.
 *
 * Helper to use lib Rebound
 * Calculate velocity and position to easy to use.
 * Calling [onResume] and [onPause] to control lifecycle
 */
class ReboundHelper(
    cxt: Context,
    private var reboundCallback: ReboundCallback,
    velocityUnits: Int
) : SpringListener, SpringSystemListener {

    private val context: Context = cxt
    private var xSpring: Spring
    private var ySpring: Spring
    private var springSystem: SpringSystem? = null
    private val springConfigDefault: SpringConfig =
        SpringConfig.fromOrigamiTensionAndFriction(50.0, 6.0)

    @get:SpringState
    @SpringState
    var currentState = SpringState.freeze

    @OvershootingState
    var overshootingState = OvershootingState.none

    //InterpolateHelper props
    private var moving = false
    private var lastX = 0f
    private var lastY = 0f
    private var tracking = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var mTouchSlop = 0f
    private var isDragging = false
    private val interpolateVelocityHelper: InterpolateVelocityHelper
    private var isUseVelocity: Boolean

    /**
     * `true` when ready to use and `false` when used
     * @See [onResume] and [onPause]
     */
    private val isResume = AtomicBoolean(false)

    /**
     * Rect bound to valid to moving
     */
    private val thresholdRect = RectF()

    companion object {
        private val TAG = ReboundHelper::class.java.simpleName
        private val DEBUG_SPRING_VIEW: Boolean = BuildConfig.DEBUG
        fun logInfo(msg: String) {
            if (DEBUG_SPRING_VIEW) {
                Log.d(TAG, msg)
            }
        }
    }

    init {
        isUseVelocity = true
        interpolateVelocityHelper = InterpolateVelocityHelper(velocityUnits)
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        springSystem = SpringSystem.create()
        xSpring = springSystem?.createSpring()!!
        ySpring = springSystem?.createSpring()!!
        xSpring.springConfig = springConfigDefault
        ySpring.springConfig = springConfigDefault
    }

    /**
     * Register listener for Spring
     * Must call when start to use.
     */
    fun onResume() {
        if (isResume.get()) return
        isResume.set(true)
        springSystem?.addListener(this)
        xSpring.addListener(this)
        ySpring.addListener(this)
    }

    /**
     * Remove all listener on Spring and stop an animation.
     * Must call when stop.
     */
    fun onPause() {
        springSystem?.removeAllListeners()
        xSpring.removeAllListeners()
        ySpring.removeAllListeners()
        isResume.set(false)
    }

    /**
     * Handle touch event to calculate position and velocity
     * @param event current motion [MotionEvent]
     * @return `true` when handle moving
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val touchX = event.rawX
        val touchY = event.rawY
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = touchX
                lastY = touchY
                tracking = true
                moving = false
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                interpolateVelocityHelper.clean()
                interpolateVelocityHelper.trackDown(reboundCallback.getX(), reboundCallback.getY())
                interpolateVelocityHelper.addDebugMotionEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking) {
                    val x = event.x
                    val y = event.y
                    if (!isDragging) {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        isDragging = abs(dx) > mTouchSlop || abs(dy) > mTouchSlop
                    }
                    if (isDragging) {
                        moving = true
                        val offsetX = lastX - touchX
                        val offsetY = lastY - touchY
                        val targetPos = prepareMovePosition(
                            (xSpring.currentValue - offsetX).toFloat(),
                            (ySpring.currentValue - offsetY).toFloat(),
                            event
                        )
                        xSpring.setCurrentValue(targetPos.x.toDouble()).setAtRest()
                        ySpring.setCurrentValue(targetPos.y.toDouble()).setAtRest()
                        lastTouchX = x
                        lastTouchY = y
                        interpolateVelocityHelper.trackMove(
                            reboundCallback.getX(),
                            reboundCallback.getY()
                        )
                        interpolateVelocityHelper.addDebugMotionEvent(event)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (tracking) {
                    setState(SpringState.prepareRelease)
                    tracking = false
                    var velocity: PointF? = null
                    if (isDragging) {
                        interpolateVelocityHelper.trackRelease(
                            reboundCallback.getX(),
                            reboundCallback.getY()
                        )
                        interpolateVelocityHelper.addDebugMotionEvent(event)
                        velocity = interpolateVelocityHelper.interpolateVelocity()
                    }
                    if (isUseVelocity) {
                        var v = reboundCallback.interpolateVelocity(velocity)
                        if (v == null) { //fallback
                            v = PointF(0f, 0f)
                        }
                        xSpring.velocity = v.x.toDouble()
                        ySpring.velocity = v.y.toDouble()
                    }
                    interpolateVelocityHelper.clean()
                }
            }
        }

        lastX = touchX
        lastY = touchY

        return tracking
    }

    override fun onSpringUpdate(spring: Spring?) {
        if (!isResume.get()) return
        reboundCallback.animationUpdate(xSpring, ySpring)
        val x = xSpring.velocity.toFloat()
        val y = ySpring.velocity.toFloat()
        if (x == 0f && y == 0f) {
            setState(SpringState.freeze)
        }
    }

    override fun onSpringAtRest(spring: Spring?) {}
    override fun onSpringActivate(spring: Spring?) {}
    override fun onSpringEndStateChange(spring: Spring?) {}
    override fun onBeforeIntegrate(springSystem: BaseSpringSystem?) {}
    override fun onAfterIntegrate(springSystem: BaseSpringSystem?) {
        if (currentState == SpringState.prepareRelease) {
            releaseSpring()
        }
    }

    fun setUseVelocity(useVelocity: Boolean) {
        isUseVelocity = useVelocity
    }

    /**
     * Get [xSpring] current X-position
     */
    val positionX: Double
        get() = xSpring.currentValue

    /**
     * Get [ySpring] current Y-position
     */
    val positionY: Double
        get() = ySpring.currentValue

    /**
     * Update value for Spring
     * @param x value for [xSpring]
     * @param y value for [ySpring]
     */
    fun updatePosition(x: Double, y: Double) {
        xSpring.setCurrentValue(x).setAtRest()
        ySpring.setCurrentValue(y).setAtRest()
    }

    /**
     * Apply new config for Spring
     * @param springConfig
     */
    fun updateSpringConfig(springConfig: SpringConfig?) {
        xSpring.springConfig = springConfig
        ySpring.springConfig = springConfig
    }

    /**
     * Set threshold rectangle to bound
     */
    fun setThresholdRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        thresholdRect.set(left, top, right, bottom)
    }

    /**
     * Set end value for [Spring]
     * @param endX endValue for [xSpring]
     * @param endY endValue for [ySpring]
     */
    fun setEndValue(endX: Double, endY: Double) {
        var tempEndX = endX
        var tempEndY = endY
        if (tempEndX < thresholdRect.left) {
            tempEndX = thresholdRect.left.toDouble()
        } else if (tempEndX > thresholdRect.right) {
            tempEndX = thresholdRect.right.toDouble()
        }
        if (tempEndY < thresholdRect.top) {
            tempEndY = thresholdRect.top.toDouble()
        } else if (tempEndY > thresholdRect.bottom) {
            tempEndY = thresholdRect.bottom.toDouble()
        }
        xSpring.endValue = tempEndX
        ySpring.endValue = tempEndY
    }

    /**
     * Set current state for FloatingController
     * @param state target
     */
    private fun setState(@SpringState state: Int) {
        currentState = state
        overshootingState = if (state == SpringState.overshooting) {
            OvershootingState.overshooting
        } else {
            OvershootingState.none
        }
    }

    /**
     * Set state overshooting for [xSpring]
     * Ref at [Spring.isOvershooting]
     */
    fun setXOvershooting() {
        setState(SpringState.overshooting)
        overshootingState = OvershootingState.xOvershooting
    }

    /**
     * Set state overshooting for [ySpring]
     * Ref at [Spring.isOvershooting]
     */
    fun setYOvershooting() {
        setState(SpringState.overshooting)
        overshootingState = OvershootingState.yOvershooting
    }

    private fun prepareMovePosition(
        x: Float,
        y: Float,
        motionEvent: MotionEvent
    ): PointF {
        var result: PointF?
        result = reboundCallback.prepareMovePosition(x, y, motionEvent)
        if (result == null) {
            result = PointF(x, y)
        }
        return result
    }

    /**
     * Event notify target is starting floating
     */
    private fun releaseSpring() {
        setState(SpringState.release)
        reboundCallback.releaseMotion(xSpring, ySpring)
        if (DEBUG_SPRING_VIEW) {
            logInfo(
                "REQUEST END ANIM: " + xSpring.endValue
                    .toString() + " | " + ySpring.endValue
                    .toString() + " === " + xSpring.velocity
                    .toString() + " | " + ySpring.velocity
            )
        }
    }

    /**
     * InterpolateVelocityHelper to interpolate orientation, prevent velocity errors.
     * @param velocity_units The units you would like the velocity in.
     * A value of 1 provides pixels per millisecond, 1000 provides pixels per second, etc.
     */
    private class InterpolateVelocityHelper internal constructor(
        private val velocity_units: Int
    ) {
        var down = CheckPoint(0f, 0f) //
        var moves: MutableList<CheckPoint> = LinkedList()
        var release = CheckPoint(0f, 0f)
        private var velocityTracker: VelocityTracker? = null
        private val velocityUnit: Long = velocity_units * 1000000.toLong()

        @VisibleForTesting
        fun addDebugMotionEvent(event: MotionEvent?) {
            if (DEBUG_VELOCITY_TRACKER) {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain()
                }
                velocityTracker!!.addMovement(event)
            }
        }

        /**
         * Add start pointer to the tracker.
         * Called this for the initial [MotionEvent.ACTION_DOWN]
         *
         * @param x The visual x position of view
         * @param y The visual y position of view
         */
        fun trackDown(x: Float, y: Float) {
            down.x = x
            down.y = y
            down.time = System.nanoTime()
        }

        /**
         * Called whenever for each a event that you receive on [MotionEvent.ACTION_MOVE]
         *
         * @param x The visual x position of view
         * @param y The visual y position of view
         */
        fun trackMove(x: Float, y: Float) {
            moves.add(CheckPoint(x, y))
        }

        /**
         * Add end pointer to the tracker.
         * Must call this for the final motion [MotionEvent.ACTION_UP]
         *
         * @param x The visual x position of view
         * @param y The visual y position of view
         */
        fun trackRelease(x: Float, y: Float) {
            release.x = x
            release.y = y
            release.time = System.nanoTime()
        }

        /**
         * Reset to be re-used by new session.
         * You must call after call [interpolateVelocity]
         */
        fun clean() {
            down.x = 0f
            down.y = 0f
            down.time = 0L
            moves.clear()
            release.x = 0f
            release.y = 0f
            release.time = 0L
            if (DEBUG_VELOCITY_TRACKER) {
                if (velocityTracker != null) {
                    velocityTracker!!.recycle()
                    velocityTracker = null
                }
            }
        }

        /**
         * Interpolate velocity with orientation using two newest points.
         *
         * @return Correct velocity
         */
        fun interpolateVelocity(): PointF {
            val result: PointF
            val checkPointSrc: CheckPoint
            val checkPointDst: CheckPoint
            when (val size = moves.size) {
                0 -> {
                    checkPointDst = release
                    checkPointSrc = down
                }
                1 -> {
                    checkPointDst = moves[0]
                    checkPointSrc = down
                }
                else -> {
                    checkPointSrc = moves[size - 2]
                    checkPointDst = moves[size - 1]
                }
            }
            result = calculateVelocity(checkPointSrc, checkPointDst)
            if (DEBUG_VELOCITY_TRACKER) {
                if (velocityTracker != null) {
                    velocityTracker!!.computeCurrentVelocity(velocity_units)
                    val vX = velocityTracker!!.xVelocity
                    val vY = velocityTracker!!.yVelocity
                    val tempXV = abs(vX)
                    val tempYV = abs(vY)
                    val resultTemp = PointF(tempXV, tempYV)
                    if (checkPointDst.x < checkPointSrc.x) {
                        resultTemp.x = -tempXV
                    }
                    if (checkPointDst.y < checkPointSrc.y) {
                        resultTemp.y = -tempYV
                    }
                    log(checkPointDst, checkPointSrc, resultTemp, result, "calculateVelocity")
                }
            }
            return result
        }

        /**
         * Calculate velocity by formula v = s/t.
         *
         * @param src source point
         * @param dst destination point
         * @return Correctly velocity
         */
        fun calculateVelocity(src: CheckPoint, dst: CheckPoint): PointF {
            val time = if (dst.time > src.time) dst.time - src.time else src.time - dst.time
            val x = if (src.x > dst.x) src.x - dst.x else dst.x - src.x
            val y = if (src.y > dst.y) src.y - dst.y else dst.y - src.y
            val vx = x * velocityUnit / time
            val vy = y * velocityUnit / time
            val result = PointF(vx, vy)
            if (dst.x < src.x) {
                result.x = -vx
            }
            if (dst.y < src.y) {
                result.y = -vy
            }
            return result
        }

        internal fun log(
            checkPoint1: CheckPoint,
            checkPoint2: CheckPoint,
            velocity: PointF,
            velocityManual: PointF,
            msg: String
        ) {
            val time =
                if (checkPoint2.time > checkPoint1.time)
                    checkPoint2.time - checkPoint1.time
                else
                    checkPoint1.time - checkPoint2.time
            val x =
                if (checkPoint1.x > checkPoint2.x)
                    checkPoint1.x - checkPoint2.x
                else
                    checkPoint2.x - checkPoint1.x
            val y =
                if (checkPoint1.y > checkPoint2.y)
                    checkPoint1.y - checkPoint2.y
                else
                    checkPoint2.y - checkPoint1.y

            logInfo(
                """
                    Interpolate :$msg
                    time(nn) : $time
                    posView  : $x - $y
                    velocityM: ${velocityManual.x} - ${velocityManual.y}
                    velocity : ${velocity.x} - ${velocity.y}
                    """
            )
        }

        internal inner class CheckPoint(x: Float, y: Float) :
            PointF(x, y) {
            var time: Long = System.nanoTime()

        }

        companion object {
            private val DEBUG_VELOCITY_TRACKER = DEBUG_SPRING_VIEW
        }
    }

    /**
     * Define current state
     */
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    annotation class SpringState {
        companion object {
            var freeze = 0
            var prepareRelease = 1
            var overshooting = 2
            var release = 3
        }
    }

    /**
     * Define current overshooting state
     */
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    annotation class OvershootingState {
        companion object {
            var none = 0
            var xOvershooting = 1
            var yOvershooting = 2
            var overshooting = 3 //both of x&y is overshooting
        }
    }

    interface ReboundCallback {
        /**
         * @return The visual x position of this target, in pixels.
         */
        fun getX(): Float

        /**
         * @return The visual y position of this target, in pixels.
         */
        fun getY(): Float

        /**
         * Change position if you want to apply condition for each motion [MotionEvent.ACTION_MOVE]
         * @param x X-position currently
         * @param y Y-position currently
         * @param event Motion event currently - [MotionEvent.ACTION_MOVE]
         * @return final position. Return null if don't want change.
         */
        fun prepareMovePosition(x: Float, y: Float, event: MotionEvent?): PointF?

        /**
         * For each an animation value update, it will be to calling.
         */
        fun animationUpdate(xSpring: Spring, ySpring: Spring)

        /**
         * Called whenever the TouchEvent was released.
         * At time, Spring's velocity was calculated by [InterpolateVelocityHelper]
         * You can set end value to Spring by [Spring.setEndValue]
         */
        fun releaseMotion(xSpring: Spring, ySpring: Spring)

        /**
         * Called whenever TouchEvent is finished.
         * Must call this for the final motion [MotionEvent.ACTION_UP] or [MotionEvent.ACTION_CANCEL]
         *
         * @param velocityInput Velocity currently
         * @return final velocity
         */
        fun interpolateVelocity(velocityInput: PointF?): PointF?
    }

}