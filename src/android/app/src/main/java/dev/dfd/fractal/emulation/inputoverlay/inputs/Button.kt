package dev.dfd.fractal.emulation.inputoverlay.inputs

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import dev.dfd.fractal.common.settings.ButtonBehavior
import dev.dfd.fractal.common.settings.TURBO_MAX_HZ
import dev.dfd.fractal.common.settings.TURBO_MIN_HZ
import kotlin.math.max
import kotlin.math.min

/** What an overlay button drives when its output changes. */
interface ButtonOutput {
    fun setPressed(pressed: Boolean, nowNanos: Long)

    /** Advances timed output; returns true while more ticks are needed. */
    fun tick(nowNanos: Long): Boolean = false

    /** Releases everything at once. */
    fun release()
}

/** Runs [Button.tick] on the next frames once a button asks for it. */
fun interface TickScheduler {
    fun requestTicks()
}

/**
 * A touch button. The overlay tells it when pointers press and leave it; [behavior]
 * turns that into the output: plain, latched (toggle) and/or repeated (turbo).
 */
abstract class Button(
    boundingRect: Rect,
    alpha: Int,
) : Input(boundingRect, alpha) {
    var behavior: ButtonBehavior = ButtonBehavior()
        set(value) {
            field = value
            turboHalfPeriodNanos = 500_000_000L / value.turboHz.coerceIn(TURBO_MIN_HZ, TURBO_MAX_HZ)
        }

    var output: ButtonOutput? = null
    var scheduler: TickScheduler? = null

    /** The button looks pressed: held, or latched by toggle. */
    protected var state = false
        private set

    private var heldPointers = 0
    private var latched = false
    private var outputPressed = false
    private var nextTurboFlipNanos = 0L
    private var turboHalfPeriodNanos = 500_000_000L / behavior.turboHz

    private val indicatorPaint = Paint().apply { isAntiAlias = true }
    private val indicatorTextPaint = Paint().apply {
        isAntiAlias = true
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    /** A pointer started touching the button. */
    fun pointerDown(nowNanos: Long) {
        heldPointers++
        if (heldPointers != 1) return
        if (behavior.toggle) latched = !latched
        updateState(nowNanos)
    }

    /** A pointer stopped touching the button. */
    fun pointerUp(nowNanos: Long) {
        if (heldPointers == 0) return
        heldPointers--
        if (heldPointers != 0 || behavior.toggle) return
        updateState(nowNanos)
    }

    private fun updateState(nowNanos: Long) {
        val active = if (behavior.toggle) latched else heldPointers > 0
        state = active
        if (active && behavior.turbo) {
            nextTurboFlipNanos = nowNanos + turboHalfPeriodNanos
            setOutput(true, nowNanos)
            scheduler?.requestTicks()
        } else {
            setOutput(active, nowNanos)
        }
    }

    private fun setOutput(pressed: Boolean, nowNanos: Long) {
        if (outputPressed == pressed) return
        outputPressed = pressed
        output?.setPressed(pressed, nowNanos)
        if (pressed) scheduler?.requestTicks()
    }

    /** Called every frame while ticks were requested; returns true while more are needed. */
    fun tick(nowNanos: Long): Boolean {
        var needsTicks = false
        if (state && behavior.turbo) {
            if (nowNanos >= nextTurboFlipNanos) {
                setOutput(!outputPressed, nowNanos)
                nextTurboFlipNanos += turboHalfPeriodNanos
                // fell behind (paused frames): restart the cadence from now
                if (nextTurboFlipNanos < nowNanos) nextTurboFlipNanos = nowNanos + turboHalfPeriodNanos
            }
            needsTicks = true
        }
        val output = output
        if (output != null && output.tick(nowNanos)) needsTicks = true
        return needsTicks
    }

    override fun resetInput() {
        heldPointers = 0
        latched = false
        state = false
        outputPressed = false
        output?.release()
    }

    override fun drawDecorations(canvas: Canvas) {
        if (behavior.isDefault) return
        val size = min(rect.width(), rect.height()).toFloat()
        val radius = max(size * 0.14f, 7f)
        val indicatorAlpha = (alpha * 2).coerceIn(110, 255)
        if (behavior.toggle) {
            // ring at the top left, filled while latched
            val cx = rect.left + radius
            val cy = rect.top + radius
            indicatorPaint.color = Color.argb(indicatorAlpha, 255, 196, 0)
            indicatorPaint.style = if (latched) Paint.Style.FILL else Paint.Style.STROKE
            indicatorPaint.strokeWidth = max(radius * 0.3f, 2f)
            canvas.drawCircle(cx, cy, radius * 0.8f, indicatorPaint)
        }
        if (behavior.turbo) {
            // "T" badge at the top right
            val cx = rect.right - radius
            val cy = rect.top + radius
            indicatorPaint.style = Paint.Style.FILL
            indicatorPaint.color = Color.argb(indicatorAlpha, 0, 200, 255)
            canvas.drawCircle(cx, cy, radius, indicatorPaint)
            indicatorTextPaint.color = Color.argb(indicatorAlpha, 0, 0, 0)
            indicatorTextPaint.textSize = radius * 1.4f
            canvas.drawText("T", cx, cy + radius * 0.5f, indicatorTextPaint)
        }
    }
}
