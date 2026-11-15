package dev.dfd.fractal.emulation.inputoverlay.inputs

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

fun interface StickListener {
    /** [x] and [y] in -1..1, y down. */
    fun onStick(x: Float, y: Float)
}

/**
 * Analog stick. The touch that grabs it becomes its center, so the thumb never starts off
 * center; the knob follows the thumb up to the edge of the well.
 */
class Joystick(
    private val listener: StickListener,
    alpha: Int,
) : Input(alpha) {
    private var currentPointerId = -1
    private var pressed = false
    private var centerX = 0f
    private var centerY = 0f
    private var originalCenterX = 0f
    private var originalCenterY = 0f
    private var radius = 0f
    private var knobRadius = 0f
    private var stickX = 0f
    private var stickY = 0f

    private val fill = fillPaint()
    private val stroke = strokePaint()
    private val markerPaint = strokePaint()
    private val sheenPaint = strokePaint()
    private val chevrons = Path()
    private val gateRect = RectF()
    private val knobSheenRect = RectF()
    private var wellShader: Shader? = null
    private var knobShader: Shader? = null
    private var knobPressedShader: Shader? = null

    init {
        configure()
    }

    override fun configure() {
        colors.update(effectiveAlpha)
        originalCenterX = rect.exactCenterX()
        originalCenterY = rect.exactCenterY()
        if (currentPointerId == -1) {
            centerX = originalCenterX
            centerY = originalCenterY
        }
        radius = min(rect.width(), rect.height()) * 0.5f
        knobRadius = radius * 0.46f
        val rimWidth = max(1.5f * density, radius * 0.03f)
        stroke.strokeWidth = rimWidth
        markerPaint.strokeWidth = max(3f * density, radius * 0.07f)
        sheenPaint.strokeWidth = max(1f * density, knobRadius * 0.1f)
        sheenPaint.color = colors.sheen

        // everything is drawn around (0, 0) and translated, so the floating center needs no new shaders
        wellShader = RadialGradient(0f, 0f, radius, intArrayOf(colors.shadow, colors.base), floatArrayOf(0.35f, 1f), Shader.TileMode.CLAMP)
        knobShader = LinearGradient(0f, -knobRadius, 0f, knobRadius, colors.bodyTop, colors.body, Shader.TileMode.CLAMP)
        knobPressedShader = LinearGradient(
            0f, -knobRadius, 0f, knobRadius, colors.bodyPressedTop, colors.bodyPressed, Shader.TileMode.CLAMP,
        )
        val gate = radius * 0.86f
        gateRect.set(-gate, -gate, gate, gate)
        val sheen = knobRadius * 0.72f
        knobSheenRect.set(-sheen, -sheen, sheen, sheen)

        // small chevrons at the four directions, near the edge of the well
        chevrons.reset()
        val out = radius * 0.86f
        val inn = radius * 0.74f
        val half = radius * 0.08f
        for (i in 0 until 4) {
            val (ax, ay) = when (i) {
                0 -> 0f to -1f
                1 -> 1f to 0f
                2 -> 0f to 1f
                else -> -1f to 0f
            }
            // perpendicular
            val px = -ay
            val py = ax
            chevrons.moveTo(ax * inn + px * half, ay * inn + py * half)
            chevrons.lineTo(ax * out, ay * out)
            chevrons.lineTo(ax * inn - px * half, ay * inn - py * half)
        }
    }

    private fun updateState(pressed: Boolean, x: Float, y: Float) {
        this.pressed = pressed
        stickX = x
        stickY = y
        listener.onStick(x, y)
    }

    override fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (currentPointerId != -1) return false
                val pointerIndex = event.actionIndex
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                if (isInside(x, y)) {
                    centerX = x
                    centerY = y
                    currentPointerId = event.getPointerId(pointerIndex)
                    updateState(true, 0f, 0f)
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (currentPointerId != -1) {
                    release()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (currentPointerId == event.getPointerId(event.actionIndex)) {
                    release()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentPointerId == -1) return false
                val index = event.findPointerIndex(currentPointerId)
                if (index < 0) return false
                var x = (event.getX(index) - centerX) / radius
                var y = (event.getY(index) - centerY) / radius
                val norm = sqrt(x * x + y * y)
                if (norm > 1f) {
                    x /= norm
                    y /= norm
                }
                updateState(true, x, y)
                return true
            }
        }
        return false
    }

    private fun release() {
        centerX = originalCenterX
        centerY = originalCenterY
        currentPointerId = -1
        updateState(false, 0f, 0f)
    }

    override fun resetInput() = release()

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(centerX, centerY)

        // well
        fill.shader = wellShader
        canvas.drawCircle(0f, 0f, radius - stroke.strokeWidth, fill)
        fill.shader = null
        stroke.color = colors.baseRim
        canvas.drawCircle(0f, 0f, radius - stroke.strokeWidth, stroke)
        stroke.color = colors.glyph
        canvas.drawPath(chevrons, stroke)

        val knobX = stickX * (radius - knobRadius)
        val knobY = stickY * (radius - knobRadius)
        val magnitude = sqrt(stickX * stickX + stickY * stickY)
        if (pressed && magnitude > 0.08f) {
            // arc on the gate pointing where the stick goes, longer the further it is pushed
            val angle = Math.toDegrees(atan2(stickY, stickX).toDouble()).toFloat()
            val sweep = 30f + 50f * magnitude
            markerPaint.color = colors.glow
            canvas.drawArc(gateRect, angle - sweep / 2f, sweep, false, markerPaint)
        }

        // knob
        canvas.translate(knobX, knobY)
        if (!pressed) {
            fill.color = colors.shadow
            canvas.drawCircle(0f, knobRadius * 0.12f, knobRadius, fill)
        }
        fill.shader = if (pressed) knobPressedShader else knobShader
        canvas.drawCircle(0f, 0f, knobRadius, fill)
        fill.shader = null
        stroke.color = if (pressed) colors.glow else colors.rim
        canvas.drawCircle(0f, 0f, knobRadius, stroke)
        if (!pressed) canvas.drawArc(knobSheenRect, 210f, 120f, false, sheenPaint)
        // grip ring
        stroke.color = if (pressed) colors.glyphPressed else colors.baseRim
        canvas.drawCircle(0f, 0f, knobRadius * 0.55f, stroke)
        canvas.restore()
    }

    override fun isInside(x: Float, y: Float): Boolean {
        val dx = x - originalCenterX
        val dy = y - originalCenterY
        return dx * dx + dy * dy <= radius * radius
    }
}
