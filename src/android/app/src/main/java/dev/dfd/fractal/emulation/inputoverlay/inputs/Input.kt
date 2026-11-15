package dev.dfd.fractal.emulation.inputoverlay.inputs

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.MotionEvent
import dev.dfd.fractal.common.settings.NormalizedRect
import dev.dfd.fractal.common.settings.OverlayInputConfig
import dev.dfd.fractal.emulation.inputoverlay.SkinColors

/**
 * Something drawn on the overlay. Geometry, paints and shaders are prepared in [configure],
 * which runs only when bounds, opacity or colors change; [draw] and touch handling don't allocate.
 */
abstract class Input protected constructor(alpha: Int) {
    /** Bounds on the overlay, in pixels. Change it with [setBounds]. */
    val rect: Rect = Rect(0, 0, 1, 1)

    protected var alpha: Int = alpha
        private set

    /** The built in input this is, or null for a macro or shortcut. */
    var config: OverlayInputConfig? = null

    /** Id of the macro this input runs, or null. */
    var macroId: String? = null

    /** Id of the shortcut this input is, or null. */
    var shortcutId: String? = null

    /** Resolution independent layout [rect] comes from, before the size settings. */
    var layout: NormalizedRect = NormalizedRect(0.5f, 0.5f, 0.1f, 0.1f)

    /** Size multiplier of this input on top of [layout] and the overlay size setting. */
    var styleScale: Float = 1f

    /** Opacity set for this input alone, or null to follow the overlay opacity. */
    var alphaOverride: Int? = null

    /** Hidden input shown faintly in edit mode so it can be selected and shown again. */
    var ghost: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            configure()
        }

    /** Pixels per dp, for strokes and badges. */
    var density: Float = 1f

    protected val colors = SkinColors()

    /** Round inputs keep their aspect ratio when resized. */
    open val keepsAspectRatio: Boolean = true

    /** Picks up a new [dev.dfd.fractal.emulation.inputoverlay.Colors.palette]. */
    fun refreshColors() = configure()

    /** Opacity the colors are made with: ghosts are faint. */
    protected val effectiveAlpha: Int get() = if (ghost) (alpha * 0.35f).toInt().coerceAtLeast(28) else alpha

    /**
     * Touch handling for inputs that follow their own pointer (sticks, d-pad).
     * Buttons are driven by the overlay through [Button.pointerDown]/[Button.pointerUp].
     */
    open fun onTouch(event: MotionEvent): Boolean = false

    fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        rect.set(left, top, right, bottom)
        configure()
    }

    fun setAlpha(alpha: Int) {
        if (this.alpha == alpha) return
        this.alpha = alpha
        configure()
    }

    /** Releases everything the input holds. */
    abstract fun resetInput()

    /** Recomputes geometry and colors; call [SkinColors.update] with [effectiveAlpha]. */
    protected abstract fun configure()

    abstract fun draw(canvas: Canvas)

    abstract fun isInside(x: Float, y: Float): Boolean

    companion object {
        val BoldTypeface: Typeface = Typeface.create(Typeface.DEFAULT, 700, false)

        fun fillPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        fun strokePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }
}
