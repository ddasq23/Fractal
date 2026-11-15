package dev.dfd.fractal.emulation.inputoverlay.inputs

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
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

enum class ButtonShape {
    CIRCLE,

    /** Rectangle with fully round ends: shoulder buttons, macros, shortcuts. */
    PILL,
    ROUNDED_SQUARE,

    /** Shoulder trigger: the outer top corner strongly rounded. */
    TRIGGER,
}

/** What is drawn on a button. */
sealed interface ButtonGlyph {
    data class Text(val text: String) : ButtonGlyph
    data object Plus : ButtonGlyph
    data object Minus : ButtonGlyph
    data object Home : ButtonGlyph
    data object Mic : ButtonGlyph
}

/**
 * A touch button. The overlay tells it when pointers press and leave it; [behavior]
 * turns that into the output: plain, latched (toggle) and/or repeated (turbo).
 */
class Button(
    private val shape: ButtonShape,
    private val glyph: ButtonGlyph,
    alpha: Int,
) : Input(alpha) {
    var behavior: ButtonBehavior = ButtonBehavior()
        set(value) {
            field = value
            turboHalfPeriodNanos = 500_000_000L / value.turboHz.coerceIn(TURBO_MIN_HZ, TURBO_MAX_HZ)
        }

    var output: ButtonOutput? = null
    var scheduler: TickScheduler? = null

    /** A trigger on the right side: its rounded corner is the top right one. */
    var mirrored: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            configure()
        }

    /** Macros and shortcuts get a small mark so they read apart from the controller's buttons. */
    var isExtra: Boolean = false

    /** The button looks pressed: held, or latched by toggle. */
    private var state = false

    private var heldPointers = 0
    private var latched = false
    private var outputPressed = false
    private var nextTurboFlipNanos = 0L
    private var turboHalfPeriodNanos = 500_000_000L / behavior.turboHz

    override val keepsAspectRatio: Boolean get() = shape == ButtonShape.CIRCLE

    // geometry, rebuilt in configure
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private var cornerRadius = 0f
    private val bounds = RectF()
    private val sheenRect = RectF()
    private val shapePath = Path()
    private val triggerRadii = FloatArray(8)
    private var rimWidth = 0f
    private var shadowOffset = 0f
    private var glowScaleX = 1f
    private var glowScaleY = 1f
    private var idleShader: Shader? = null
    private var pressedShader: Shader? = null

    private val fill = fillPaint()
    private val stroke = strokePaint()
    private val glowPaint = strokePaint()
    private val sheenPaint = strokePaint()
    private val textPaint = fillPaint().apply {
        typeface = BoldTypeface
        textAlign = Paint.Align.CENTER
    }
    private val glyphStroke = strokePaint()
    private val glyphPath = Path()
    private var textY = 0f

    // badges
    private val badgePaint = fillPaint()
    private val badgeRing = strokePaint()
    private val badgeText = fillPaint().apply {
        typeface = BoldTypeface
        textAlign = Paint.Align.CENTER
    }
    private var badgeRadius = 0f
    private var badgeTextY = 0f

    init {
        configure()
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

    override fun configure() {
        colors.update(effectiveAlpha)
        val w = rect.width().toFloat()
        val h = rect.height().toFloat()
        val size = min(w, h)
        centerX = rect.exactCenterX()
        centerY = rect.exactCenterY()
        rimWidth = max(1.5f * density, size * 0.045f)
        shadowOffset = max(1.5f * density, size * 0.05f)
        val inset = rimWidth / 2f
        when (shape) {
            ButtonShape.CIRCLE -> {
                radius = size / 2f - inset
                bounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
            }

            else -> {
                bounds.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
                cornerRadius = when (shape) {
                    ButtonShape.PILL -> bounds.height().coerceAtMost(bounds.width()) / 2f
                    else -> size * 0.26f
                }
            }
        }
        shapePath.reset()
        if (shape == ButtonShape.TRIGGER) {
            val bh = bounds.height()
            val big = min(bh * 0.95f, bounds.width() * 0.45f)
            val small = bh * 0.26f
            val round = bh * 0.5f
            // top left, top right, bottom right, bottom left (x, y pairs)
            val tl = if (mirrored) small else big
            val tr = if (mirrored) big else small
            val br = if (mirrored) small else round
            val bl = if (mirrored) round else small
            triggerRadii[0] = tl; triggerRadii[1] = tl
            triggerRadii[2] = tr; triggerRadii[3] = tr
            triggerRadii[4] = br; triggerRadii[5] = br
            triggerRadii[6] = bl; triggerRadii[7] = bl
            shapePath.addRoundRect(bounds, triggerRadii, Path.Direction.CW)
        }
        val glowOutset = rimWidth * 1.6f
        glowScaleX = (bounds.width() + 2 * glowOutset) / max(bounds.width(), 1f)
        glowScaleY = (bounds.height() + 2 * glowOutset) / max(bounds.height(), 1f)

        idleShader = LinearGradient(0f, bounds.top, 0f, bounds.bottom, colors.bodyTop, colors.body, Shader.TileMode.CLAMP)
        pressedShader = LinearGradient(
            0f, bounds.top, 0f, bounds.bottom, colors.bodyPressedTop, colors.bodyPressed, Shader.TileMode.CLAMP,
        )
        stroke.strokeWidth = rimWidth
        glowPaint.strokeWidth = rimWidth * 2.2f
        glowPaint.color = colors.glow
        sheenPaint.strokeWidth = max(1f * density, size * 0.05f)
        sheenPaint.color = colors.sheen
        val sheenInset = size * 0.14f
        sheenRect.set(bounds.left + sheenInset, bounds.top + sheenInset, bounds.right - sheenInset, bounds.bottom - sheenInset)

        configureGlyph(w, h, size)

        badgeRadius = max(size * 0.13f, 6f * density)
        badgeText.textSize = badgeRadius * 1.3f
        badgeTextY = -(badgeText.ascent() + badgeText.descent()) / 2f
        badgeRing.strokeWidth = max(badgeRadius * 0.28f, 1.5f * density)
        badgeRing.color = colors.glow
        badgePaint.color = colors.glow
        badgeText.color = colors.glyphPressed
    }

    private fun configureGlyph(w: Float, h: Float, size: Float) {
        glyphPath.reset()
        when (glyph) {
            is ButtonGlyph.Text -> {
                val text = glyph.text
                textPaint.textSize = size * when {
                    text.length <= 1 -> 0.5f
                    text.length == 2 -> 0.4f
                    else -> 0.34f
                }
                // long labels (macros) shrink to fit the width
                val maxWidth = w * if (shape == ButtonShape.CIRCLE) 0.7f else 0.8f
                val textWidth = textPaint.measureText(text)
                if (textWidth > maxWidth && textWidth > 0f) textPaint.textSize *= maxWidth / textWidth
                textY = centerY - (textPaint.ascent() + textPaint.descent()) / 2f
            }

            ButtonGlyph.Plus, ButtonGlyph.Minus -> {
                val half = size * 0.19f
                glyphPath.moveTo(centerX - half, centerY)
                glyphPath.lineTo(centerX + half, centerY)
                if (glyph == ButtonGlyph.Plus) {
                    glyphPath.moveTo(centerX, centerY - half)
                    glyphPath.lineTo(centerX, centerY + half)
                }
                glyphStroke.strokeWidth = size * 0.085f
            }

            ButtonGlyph.Home -> {
                val s = size * 0.2f
                val baseY = centerY + s * 0.95f
                glyphPath.moveTo(centerX - s * 1.15f, centerY - s * 0.05f)
                glyphPath.lineTo(centerX, centerY - s * 1.15f)
                glyphPath.lineTo(centerX + s * 1.15f, centerY - s * 0.05f)
                glyphPath.moveTo(centerX - s * 0.8f, centerY - s * 0.3f)
                glyphPath.lineTo(centerX - s * 0.8f, baseY)
                glyphPath.lineTo(centerX + s * 0.8f, baseY)
                glyphPath.lineTo(centerX + s * 0.8f, centerY - s * 0.3f)
                glyphStroke.strokeWidth = size * 0.07f
            }

            ButtonGlyph.Mic -> {
                val s = size * 0.2f
                val capsule = RectF(centerX - s * 0.45f, centerY - s * 1.2f, centerX + s * 0.45f, centerY + s * 0.3f)
                glyphPath.addRoundRect(capsule, s * 0.45f, s * 0.45f, Path.Direction.CW)
                val arc = RectF(centerX - s * 0.85f, centerY - s * 0.75f, centerX + s * 0.85f, centerY + s * 0.75f)
                glyphPath.addArc(arc, 10f, 160f)
                glyphPath.moveTo(centerX, centerY + s * 0.75f)
                glyphPath.lineTo(centerX, centerY + s * 1.2f)
                glyphStroke.strokeWidth = size * 0.065f
            }
        }
    }

    private fun drawShape(canvas: Canvas, paint: Paint) {
        when (shape) {
            ButtonShape.CIRCLE -> canvas.drawCircle(centerX, centerY, radius, paint)
            ButtonShape.TRIGGER -> canvas.drawPath(shapePath, paint)
            else -> canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint)
        }
    }

    override fun draw(canvas: Canvas) {
        val pressed = state
        if (pressed) {
            // halo around the pressed button
            canvas.save()
            canvas.scale(glowScaleX, glowScaleY, centerX, centerY)
            drawShape(canvas, glowPaint)
            canvas.restore()
        } else {
            fill.shader = null
            fill.color = colors.shadow
            canvas.save()
            canvas.translate(0f, shadowOffset)
            drawShape(canvas, fill)
            canvas.restore()
        }

        canvas.save()
        if (pressed) canvas.scale(PRESS_SCALE, PRESS_SCALE, centerX, centerY)
        fill.shader = if (pressed) pressedShader else idleShader
        drawShape(canvas, fill)
        fill.shader = null
        stroke.color = if (pressed) colors.glow else colors.rim
        drawShape(canvas, stroke)
        if (!pressed && shape == ButtonShape.CIRCLE) {
            canvas.drawArc(sheenRect, 210f, 120f, false, sheenPaint)
        }
        drawGlyph(canvas, pressed)
        canvas.restore()

        drawBadges(canvas)
    }

    private fun drawGlyph(canvas: Canvas, pressed: Boolean) {
        val color = if (pressed) colors.glyphPressed else colors.glyph
        if (glyph is ButtonGlyph.Text) {
            textPaint.color = color
            canvas.drawText(glyph.text, centerX, textY, textPaint)
        } else {
            glyphStroke.color = color
            canvas.drawPath(glyphPath, glyphStroke)
        }
    }

    private fun drawBadges(canvas: Canvas) {
        if (behavior.toggle) {
            // ring at the top left, filled while latched
            val cx = bounds.left + badgeRadius * 0.9f
            val cy = bounds.top + badgeRadius * 0.9f
            if (latched) canvas.drawCircle(cx, cy, badgeRadius * 0.75f, badgePaint)
            canvas.drawCircle(cx, cy, badgeRadius * 0.75f, badgeRing)
        }
        if (behavior.turbo) {
            val cx = bounds.right - badgeRadius * 0.9f
            val cy = bounds.top + badgeRadius * 0.9f
            canvas.drawCircle(cx, cy, badgeRadius, badgePaint)
            canvas.drawText("T", cx, cy + badgeTextY, badgeText)
        }
        if (isExtra && !behavior.turbo) {
            // small corner dot: a user made button
            canvas.drawCircle(bounds.right - badgeRadius * 0.7f, bounds.top + badgeRadius * 0.7f, badgeRadius * 0.35f, badgePaint)
        }
    }

    override fun isInside(x: Float, y: Float): Boolean {
        if (shape == ButtonShape.CIRCLE) {
            val dx = x - centerX
            val dy = y - centerY
            val r = radius * HIT_SLOP
            return dx * dx + dy * dy <= r * r
        }
        val slopX = bounds.width() * (HIT_SLOP - 1f) / 2f
        val slopY = bounds.height() * (HIT_SLOP - 1f) / 2f
        return x >= bounds.left - slopX && x < bounds.right + slopX && y >= bounds.top - slopY && y < bounds.bottom + slopY
    }

    companion object {
        private const val PRESS_SCALE = 0.94f

        /** Touches a little outside the drawn shape still count. */
        private const val HIT_SLOP = 1.08f
    }
}
