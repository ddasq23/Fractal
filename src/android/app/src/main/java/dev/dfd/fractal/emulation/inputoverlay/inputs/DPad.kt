package dev.dfd.fractal.emulation.inputoverlay.inputs

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import dev.dfd.fractal.emulation.inputoverlay.OverlayDpad
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun interface DPadListener {
    fun onDirection(direction: OverlayDpad, pressed: Boolean)
}

/** Cross pad on a round plate; diagonals press two directions. */
class DPad(
    private val listener: DPadListener,
    alpha: Int,
) : Input(alpha) {
    private var dpadState: Int = NONE
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private var radius2 = 0f
    private var currentPointerId = -1

    private val crossPath = Path()
    private val armPaths = Array(4) { Path() }
    private val arrowPaths = Array(4) { Path() }
    private var crossShader: Shader? = null
    private var pressedShader: Shader? = null
    private var dimpleRadius = 0f

    private val fill = fillPaint()
    private val stroke = strokePaint()
    private val arrowPaint = fillPaint()

    init {
        configure()
    }

    override fun configure() {
        colors.update(effectiveAlpha)
        centerX = rect.exactCenterX()
        centerY = rect.exactCenterY()
        radius = min(rect.width(), rect.height()) * 0.5f
        radius2 = radius * radius
        val rimWidth = max(1.5f * density, radius * 0.04f)
        stroke.strokeWidth = rimWidth

        val arm = radius * 0.68f
        val armHalfWidth = radius * 0.3f
        val corner = armHalfWidth * 0.45f
        crossPath.reset()
        val horizontal = Path().apply {
            addRoundRect(RectF(centerX - arm, centerY - armHalfWidth, centerX + arm, centerY + armHalfWidth), corner, corner, Path.Direction.CW)
        }
        val vertical = Path().apply {
            addRoundRect(RectF(centerX - armHalfWidth, centerY - arm, centerX + armHalfWidth, centerY + arm), corner, corner, Path.Direction.CW)
        }
        crossPath.op(horizontal, vertical, Path.Op.UNION)

        // one lit arm per direction: up, down, left, right
        val inner = armHalfWidth * 0.35f
        val armRects = arrayOf(
            RectF(centerX - armHalfWidth, centerY - arm, centerX + armHalfWidth, centerY - inner),
            RectF(centerX - armHalfWidth, centerY + inner, centerX + armHalfWidth, centerY + arm),
            RectF(centerX - arm, centerY - armHalfWidth, centerX - inner, centerY + armHalfWidth),
            RectF(centerX + inner, centerY - armHalfWidth, centerX + arm, centerY + armHalfWidth),
        )
        for (i in 0 until 4) {
            armPaths[i].reset()
            armPaths[i].addRoundRect(armRects[i], corner, corner, Path.Direction.CW)
        }

        // arrows two thirds out on each arm
        val tip = arm * 0.78f
        val back = arm * 0.48f
        val half = armHalfWidth * 0.42f
        for (i in 0 until 4) {
            val p = arrowPaths[i]
            p.reset()
            when (i) {
                UP_INDEX -> {
                    p.moveTo(centerX, centerY - tip)
                    p.lineTo(centerX + half, centerY - back)
                    p.lineTo(centerX - half, centerY - back)
                }

                DOWN_INDEX -> {
                    p.moveTo(centerX, centerY + tip)
                    p.lineTo(centerX - half, centerY + back)
                    p.lineTo(centerX + half, centerY + back)
                }

                LEFT_INDEX -> {
                    p.moveTo(centerX - tip, centerY)
                    p.lineTo(centerX - back, centerY - half)
                    p.lineTo(centerX - back, centerY + half)
                }

                else -> {
                    p.moveTo(centerX + tip, centerY)
                    p.lineTo(centerX + back, centerY + half)
                    p.lineTo(centerX + back, centerY - half)
                }
            }
            p.close()
        }
        dimpleRadius = armHalfWidth * 0.42f

        crossShader = LinearGradient(0f, centerY - arm, 0f, centerY + arm, colors.bodyTop, colors.body, Shader.TileMode.CLAMP)
        pressedShader = LinearGradient(
            0f, centerY - arm, 0f, centerY + arm, colors.bodyPressedTop, colors.bodyPressed, Shader.TileMode.CLAMP,
        )
    }

    private fun updateState(nextDpadState: Int) {
        if (nextDpadState == dpadState) return
        notify(dpadState and nextDpadState.inv(), false)
        notify(nextDpadState and dpadState.inv(), true)
        dpadState = nextDpadState
    }

    private fun notify(changed: Int, pressed: Boolean) {
        if (changed == NONE) return
        if ((changed and UP) != 0) listener.onDirection(OverlayDpad.DPAD_UP, pressed)
        if ((changed and DOWN) != 0) listener.onDirection(OverlayDpad.DPAD_DOWN, pressed)
        if ((changed and LEFT) != 0) listener.onDirection(OverlayDpad.DPAD_LEFT, pressed)
        if ((changed and RIGHT) != 0) listener.onDirection(OverlayDpad.DPAD_RIGHT, pressed)
    }

    /** Eight sectors of 45°; the dead zone in the middle presses nothing. */
    private fun stateAt(x: Float, y: Float): Int {
        val dx = x - centerX
        val dy = y - centerY
        if (dx * dx + dy * dy <= radius2 * DEAD_ZONE) return NONE
        val ax = abs(dx)
        val ay = abs(dy)
        val vertical = if (dy < 0) UP else DOWN
        val horizontal = if (dx < 0) LEFT else RIGHT
        // within 22.5° of an axis the touch is a straight direction, otherwise a diagonal
        return when {
            ay <= ax * TAN_22_5 -> horizontal
            ax <= ay * TAN_22_5 -> vertical
            else -> vertical or horizontal
        }
    }

    override fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (currentPointerId != -1) return false
                val pointerIndex = event.actionIndex
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                if (isInside(x, y)) {
                    currentPointerId = event.getPointerId(pointerIndex)
                    updateState(stateAt(x, y))
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (currentPointerId != -1) {
                    currentPointerId = -1
                    updateState(NONE)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == currentPointerId) {
                    currentPointerId = -1
                    updateState(NONE)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentPointerId == -1) return false
                val index = event.findPointerIndex(currentPointerId)
                if (index < 0) return false
                val before = dpadState
                updateState(stateAt(event.getX(index), event.getY(index)))
                return before != dpadState
            }
        }
        return false
    }

    override fun resetInput() {
        currentPointerId = -1
        updateState(NONE)
    }

    override fun isInside(x: Float, y: Float): Boolean {
        val dx = x - centerX
        val dy = y - centerY
        return dx * dx + dy * dy <= radius2
    }

    override fun draw(canvas: Canvas) {
        // plate
        fill.shader = null
        fill.color = colors.base
        canvas.drawCircle(centerX, centerY, radius - stroke.strokeWidth, fill)
        stroke.color = colors.baseRim
        canvas.drawCircle(centerX, centerY, radius - stroke.strokeWidth, stroke)

        // cross with its shadow
        fill.color = colors.shadow
        canvas.save()
        canvas.translate(0f, stroke.strokeWidth * 1.5f)
        canvas.drawPath(crossPath, fill)
        canvas.restore()
        fill.shader = crossShader
        canvas.drawPath(crossPath, fill)
        fill.shader = pressedShader
        for (i in 0 until 4) {
            if ((dpadState and DIRECTION_BITS[i]) != 0) canvas.drawPath(armPaths[i], fill)
        }
        fill.shader = null
        stroke.color = colors.rim
        canvas.drawPath(crossPath, stroke)

        for (i in 0 until 4) {
            arrowPaint.color = if ((dpadState and DIRECTION_BITS[i]) != 0) colors.glyphPressed else colors.glyph
            canvas.drawPath(arrowPaths[i], arrowPaint)
        }
        fill.color = colors.shadow
        canvas.drawCircle(centerX, centerY, dimpleRadius, fill)
    }

    companion object {
        private const val NONE = 0
        private const val UP = 1 shl 0
        private const val DOWN = 1 shl 1
        private const val LEFT = 1 shl 2
        private const val RIGHT = 1 shl 3
        private const val UP_INDEX = 0
        private const val DOWN_INDEX = 1
        private const val LEFT_INDEX = 2
        private val DIRECTION_BITS = intArrayOf(UP, DOWN, LEFT, RIGHT)

        /** Fraction of the squared radius in the middle that presses nothing. */
        private const val DEAD_ZONE = 0.04f
        private const val TAN_22_5 = 0.41421357f
    }
}
