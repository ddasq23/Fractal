package dev.dfd.fractal.emulation.inputoverlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.dfd.fractal.common.android.context.getDeviceVibrator
import dev.dfd.fractal.common.settings.AdaptiveOverlayLayout
import dev.dfd.fractal.common.settings.HotkeyAction
import dev.dfd.fractal.common.settings.InputOverlaySettings
import dev.dfd.fractal.common.settings.LayoutInsets
import dev.dfd.fractal.common.settings.MacroKind
import dev.dfd.fractal.common.settings.MacroStep
import dev.dfd.fractal.common.settings.NormalizedRect
import dev.dfd.fractal.common.settings.OverlayButtonShape
import dev.dfd.fractal.common.settings.OverlayControllerType
import dev.dfd.fractal.common.settings.OverlayInputConfig
import dev.dfd.fractal.common.settings.OverlayInputStyle
import dev.dfd.fractal.common.settings.OverlayLayoutProfile
import dev.dfd.fractal.common.settings.OverlayLayoutSpec
import dev.dfd.fractal.common.settings.OverlayMacro
import dev.dfd.fractal.common.settings.OverlayShortcut
import dev.dfd.fractal.common.settings.activeProfile
import dev.dfd.fractal.common.settings.computeAdaptiveLayout
import dev.dfd.fractal.common.settings.isInputVisible
import dev.dfd.fractal.common.settings.isRectangular
import dev.dfd.fractal.common.settings.legacyDefaultRect
import dev.dfd.fractal.common.settings.legacyMacroRect
import dev.dfd.fractal.common.settings.legacyShortcutRect
import dev.dfd.fractal.common.settings.migrateLegacyRects
import dev.dfd.fractal.common.settings.overlayInputsFor
import dev.dfd.fractal.common.settings.savedRect
import dev.dfd.fractal.common.settings.styleKey
import dev.dfd.fractal.common.settings.usesLegacyLayout
import dev.dfd.fractal.emulation.input.shortLabel
import dev.dfd.fractal.emulation.inputoverlay.inputs.Button
import dev.dfd.fractal.emulation.inputoverlay.inputs.ButtonGlyph
import dev.dfd.fractal.emulation.inputoverlay.inputs.ButtonOutput
import dev.dfd.fractal.emulation.inputoverlay.inputs.ButtonShape
import dev.dfd.fractal.emulation.inputoverlay.inputs.DPad
import dev.dfd.fractal.emulation.inputoverlay.inputs.Input
import dev.dfd.fractal.emulation.inputoverlay.inputs.Joystick
import dev.dfd.fractal.emulation.inputoverlay.inputs.TickScheduler
import dev.dfd.fractal.nativeinterface.NativeInput
import dev.dfd.fractal.nativeinterface.NativeInput.getControllerType
import dev.dfd.fractal.nativeinterface.NativeInput.isControllerDisabled
import dev.dfd.fractal.nativeinterface.NativeInput.onOverlayAxis
import dev.dfd.fractal.nativeinterface.NativeInput.onOverlayButton
import dev.dfd.fractal.nativeinterface.NativeSettings
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The on-screen controls, drawn over the game. In [InputMode.DEFAULT] touches drive the emulated
 * controller; in [InputMode.EDIT] they select, move and resize the inputs, and the results are
 * handed to [onInputMovedListener] to be saved by the editor.
 *
 * A plain view: it draws in the window, above the game surfaces. Nothing is allocated while drawing
 * or handling touches; inputs are rebuilt only when the settings, the size or the mode change.
 */
@SuppressLint("ViewConstructor")
class InputOverlayView(context: Context) : View(context), View.OnTouchListener,
    TickScheduler, Choreographer.FrameCallback {
    enum class InputMode {
        DEFAULT, EDIT,
    }

    /** A shortcut button was pressed. */
    var onShortcutListener: ((HotkeyAction) -> Unit)? = null

    /** Old absolute pixel layout found; called with the overlay size to convert it. */
    var onLegacyLayoutListener: ((width: Int, height: Int) -> Unit)? = null

    /** An input was moved or resized in edit mode; portrait tells which layout it belongs to. */
    var onInputMovedListener: ((OverlayTarget, NormalizedRect, Boolean) -> Unit)? = null

    var controller: InputOverlayController? = null
        set(value) {
            field?.view = null
            field = value
            value?.view = this
            value?.controllerType = controllerType
        }

    /** Shows and edits the layout of this controller type instead of the emulated one (settings screen). */
    var forcedControllerType: OverlayControllerType? = null
        set(value) {
            if (field == value) return
            field = value
            setInputs()
            invalidate()
        }

    private var palette: OverlayPalette = OverlayPalette.CLASSIC
    private var inputMode = InputMode.DEFAULT
    private val density = resources.displayMetrics.density
    private var controllerType: OverlayControllerType? = null
    private var profile: OverlayLayoutProfile? = null
    private var isPortrait = false

    /** Everything drawn, in drawing order (last is on top). */
    private val inputs = ArrayList<Input>()

    /** Buttons, macros included; driven by the overlay's own pointer routing. */
    private val buttons = ArrayList<Button>()

    /** Sticks and d-pad: they follow their own pointer. */
    private val pointerInputs = ArrayList<Input>()

    /** Button each pointer id is holding, null when none. */
    private val pointerButtons = arrayOfNulls<Button>(MAX_POINTER_ID)

    /** How many overlay sources hold each native button, so combos and buttons can overlap. */
    private val nativePressCounts = IntArray(MAX_NATIVE_BUTTON_ID)

    /** Native axis ids of the left and right stick: up, down, left, right; -1 when unmapped. */
    private val leftStickAxes = IntArray(4) { -1 }
    private val rightStickAxes = IntArray(4) { -1 }

    private val vibrator: Vibrator = context.getDeviceVibrator()
    private val buttonTouchVibrationEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val windowLocation = IntArray(2)
    private var insets = LayoutInsets.NONE

    /** Bottom of the emulator's stats box (FPS, CPU...) in the top left corner, in px; the default layout keeps clear of it. */
    private var statsReservePx = 0f
    private val statsWatcher = object : Runnable {
        override fun run() {
            val reserve = runCatching { NativeSettings.getStatsOverlayTopLeftBottom() }.getOrDefault(0f) * height
            // only a real change relays out: the box grows and shrinks a few px as the numbers change
            if (kotlin.math.abs(reserve - statsReservePx) > 8f * density && inputMode != InputMode.EDIT) {
                statsReservePx = reserve
                if (width > 0 && height > 0) setInputs()
            }
            postDelayed(this, 1000)
        }
    }

    private var settings: InputOverlaySettings = InputOverlaySettings()
    private var previewAlpha: Int? = null
    private var previewScale: Float? = null
    private val controllerIndex get() = settings.controllerIndex
    private val currentAlpha get() = previewAlpha ?: settings.alpha
    private val currentScale get() = previewScale ?: settings.scale
    private val isVibrateOnTouchEnabled: Boolean
        get() = settings.isVibrateOnTouchEnabled && vibrator.hasVibrator()

    private var legacyMigrationRequested = false
    private var isTicking = false
    private val editRect = Rect()

    // ---- edit state ----
    private var selected: Input? = null
    private var selectedTarget: OverlayTarget? = null
    private var gesture = GESTURE_NONE
    private var activePointerId = -1
    private var downX = 0f
    private var downY = 0f
    private val startRect = Rect()
    private var resizeCorner = 0
    private var pinchStartDistance = 0f
    private var editChanged = false
    private var guideX = Float.NaN
    private var guideY = Float.NaN

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = Color.argb(150, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(8f * density, 6f * density), 0f)
    }
    private val gridPaint = Paint().apply {
        color = Color.argb(36, 255, 255, 255)
        strokeWidth = 1f
    }
    private val editShadePaint = Paint().apply { color = Color.argb(70, 0, 0, 0) }

    init {
        visibility = GONE
        isFocusable = true
        setOnTouchListener(this)
        applyAccent()
    }

    private fun dp(value: Float) = value * density

    private fun applyAccent() {
        val accent = palette.glow or 0xFF000000.toInt()
        // white handles need a colored outline; a white accent gets a dark one
        val visible = if (Color.luminance(accent) > 0.85f) Color.rgb(40, 40, 48) else accent
        selectionPaint.color = visible
        handleStroke.color = visible
        guidePaint.color = visible
    }

    fun applySettings(inputOverlaySettings: InputOverlaySettings) {
        if (this.settings == inputOverlaySettings) return
        this.settings = inputOverlaySettings
        previewAlpha = null
        previewScale = null
        setInputs()
        invalidate()
    }

    /** Recolors the inputs. */
    fun applyPalette(palette: OverlayPalette) {
        if (this.palette == palette) return
        this.palette = palette
        Colors.palette = palette
        applyAccent()
        for (input in inputs) input.refreshColors()
        invalidate()
    }

    /** Builds the inputs again, e.g. after the emulated controller type changed. */
    fun rebuild() {
        setInputs()
        invalidate()
    }

    fun setVisible(isVisible: Boolean) {
        val newVisibility = if (isVisible) VISIBLE else GONE
        if (visibility == newVisibility) return
        visibility = newVisibility
        if (!isVisible) releaseAll()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // layouts are resolution independent: rebuild for the new size
        setInputs()
    }

    override fun onApplyWindowInsets(windowInsets: WindowInsets): WindowInsets {
        val before = insets
        readInsets(windowInsets)
        if (before != insets) setInputs()
        return super.onApplyWindowInsets(windowInsets)
    }

    /** Cutouts the layout keeps clear of, in dp, relative to this view. */
    private fun readInsets(windowInsets: WindowInsets?) {
        val cutout = windowInsets?.getInsets(WindowInsets.Type.displayCutout())
        if (cutout == null) {
            insets = LayoutInsets.NONE
            return
        }
        getLocationInWindow(windowLocation)
        val windowWidth = rootView?.width ?: width
        val windowHeight = rootView?.height ?: height
        val left = max(0, cutout.left - windowLocation[0])
        val top = max(0, cutout.top - windowLocation[1])
        val right = max(0, cutout.right - max(0, windowWidth - windowLocation[0] - width))
        val bottom = max(0, cutout.bottom - max(0, windowHeight - windowLocation[1] - height))
        insets = LayoutInsets(left / density, top / density, right / density, bottom / density)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller?.view = this
        postDelayed(statsWatcher, 1000)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(statsWatcher)
        releaseAll()
        controller?.view = null
    }

    fun setInputMode(inputMode: InputMode) {
        if (this.inputMode == inputMode) return
        this.inputMode = inputMode
        // nothing stays held or latched across modes; hidden inputs show only while editing
        releaseAll()
        if (inputMode == InputMode.DEFAULT) select(null)
        setInputs()
        invalidate()
    }

    /** Shows [alpha]/[scale] without saving, while a slider is dragged. Null keeps the saved value. */
    fun previewAppearance(alpha: Int?, scale: Float?) {
        if (alpha != null && alpha != currentAlpha) {
            previewAlpha = alpha
            for (input in inputs) if (input.alphaOverride == null) input.setAlpha(alpha)
        }
        if (scale != null && scale != currentScale) {
            // the default layout is fitted with the size, so it is computed again
            previewScale = scale
            setInputs()
        }
        invalidate()
    }

    /** Shows [style] on the input with [key] without saving it; a new shape shows once saved. */
    fun previewInputStyle(key: String, style: OverlayInputStyle) {
        val input = inputs.firstOrNull { it.styleKey() == key } ?: return
        input.alphaOverride = style.opacity
        input.setAlpha(style.opacity ?: currentAlpha)
        if (input.styleScale != style.scale) {
            input.styleScale = style.scale
            toPixels(input.layout, editRect, input.styleScale)
            input.setBounds(editRect.left, editRect.top, editRect.right, editRect.bottom)
        }
        invalidate()
    }

    /** Where [target] is drawn now, in the saved form; null when it isn't on the overlay. */
    fun currentLayoutOf(target: OverlayTarget): NormalizedRect? =
        inputs.firstOrNull { it.toTarget() == target }?.layout

    private fun Input.styleKey(): String? = macroId ?: shortcutId ?: config?.let { styleKey(it) }

    // ---- native output ----

    private fun pressNative(id: Int) {
        if (id < 0 || id >= MAX_NATIVE_BUTTON_ID) return
        if (nativePressCounts[id]++ == 0) onOverlayButton(controllerIndex, id, true)
    }

    private fun releaseNative(id: Int) {
        if (id < 0 || id >= MAX_NATIVE_BUTTON_ID || nativePressCounts[id] == 0) return
        if (--nativePressCounts[id] == 0) onOverlayButton(controllerIndex, id, false)
    }

    private fun vibrate() {
        if (isVibrateOnTouchEnabled) vibrator.vibrate(buttonTouchVibrationEffect)
    }

    /** Presses a fixed set of native buttons together. */
    private inner class ButtonsOutput(private val ids: IntArray) : ButtonOutput {
        private var pressed = false

        override fun setPressed(pressed: Boolean, nowNanos: Long) {
            if (this.pressed == pressed) return
            this.pressed = pressed
            for (id in ids) if (pressed) pressNative(id) else releaseNative(id)
        }

        override fun release() = setPressed(false, 0L)
    }

    /** Plays timed steps; [loop] repeats them while the button stays active. */
    private inner class SequenceOutput(
        private val steps: Array<IntArray>,
        private val durationsNanos: LongArray,
        private val loop: Boolean,
    ) : ButtonOutput {
        private var requested = false
        private var playing = false
        private var index = 0
        private var stepEndNanos = 0L

        override fun setPressed(pressed: Boolean, nowNanos: Long) {
            requested = pressed
            if (pressed) {
                if (!playing && steps.isNotEmpty()) {
                    playing = true
                    index = 0
                    pressStep(0)
                    stepEndNanos = nowNanos + durationsNanos[0]
                    requestTicks()
                }
            } else if (loop) {
                stop()
            }
        }

        override fun tick(nowNanos: Long): Boolean {
            if (!playing) return false
            // one step per frame at most, so no step is skipped when frames come late
            if (nowNanos < stepEndNanos) return true
            releaseStep(index)
            index++
            if (index >= steps.size) {
                if (!loop || !requested) {
                    playing = false
                    return false
                }
                index = 0
            }
            pressStep(index)
            stepEndNanos = nowNanos + durationsNanos[index]
            return true
        }

        private fun pressStep(i: Int) {
            for (id in steps[i]) pressNative(id)
        }

        private fun releaseStep(i: Int) {
            for (id in steps[i]) releaseNative(id)
        }

        private fun stop() {
            if (playing) releaseStep(index)
            playing = false
        }

        override fun release() {
            requested = false
            stop()
        }
    }

    /** Runs its action once per press. */
    private inner class ShortcutOutput(private val action: HotkeyAction) : ButtonOutput {
        override fun setPressed(pressed: Boolean, nowNanos: Long) {
            if (pressed) onShortcutListener?.invoke(action)
        }

        override fun release() {}
    }

    override fun requestTicks() {
        if (isTicking) return
        isTicking = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        val now = System.nanoTime()
        var more = false
        for (i in 0 until buttons.size) {
            if (buttons[i].tick(now)) more = true
        }
        if (more) {
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            isTicking = false
        }
    }

    /** Releases every button, stick and d-pad direction the overlay holds. */
    private fun releaseAll() {
        if (isTicking) {
            Choreographer.getInstance().removeFrameCallback(this)
            isTicking = false
        }
        pointerButtons.fill(null)
        for (input in inputs) input.resetInput()
        for (id in 0 until MAX_NATIVE_BUTTON_ID) {
            if (nativePressCounts[id] != 0) {
                nativePressCounts[id] = 0
                onOverlayButton(controllerIndex, id, false)
            }
        }
        invalidate()
    }

    // ---- sticks ----

    private fun setStickAxes(type: OverlayControllerType) {
        leftStickAxes.fill(-1)
        rightStickAxes.fill(-1)
        when (type) {
            OverlayControllerType.VPAD -> {
                leftStickAxes.setAll(NativeInput.VPADButton.STICKL_UP, NativeInput.VPADButton.STICKL_DOWN, NativeInput.VPADButton.STICKL_LEFT, NativeInput.VPADButton.STICKL_RIGHT)
                rightStickAxes.setAll(NativeInput.VPADButton.STICKR_UP, NativeInput.VPADButton.STICKR_DOWN, NativeInput.VPADButton.STICKR_LEFT, NativeInput.VPADButton.STICKR_RIGHT)
            }

            OverlayControllerType.PRO -> {
                leftStickAxes.setAll(NativeInput.ProButton.STICKL_UP, NativeInput.ProButton.STICKL_DOWN, NativeInput.ProButton.STICKL_LEFT, NativeInput.ProButton.STICKL_RIGHT)
                rightStickAxes.setAll(NativeInput.ProButton.STICKR_UP, NativeInput.ProButton.STICKR_DOWN, NativeInput.ProButton.STICKR_LEFT, NativeInput.ProButton.STICKR_RIGHT)
            }

            OverlayControllerType.CLASSIC -> {
                leftStickAxes.setAll(NativeInput.ClassicButton.STICKL_UP, NativeInput.ClassicButton.STICKL_DOWN, NativeInput.ClassicButton.STICKL_LEFT, NativeInput.ClassicButton.STICKL_RIGHT)
                rightStickAxes.setAll(NativeInput.ClassicButton.STICKR_UP, NativeInput.ClassicButton.STICKR_DOWN, NativeInput.ClassicButton.STICKR_LEFT, NativeInput.ClassicButton.STICKR_RIGHT)
            }

            // the Wiimote's only stick is the nunchuk's, on the "right stick" input
            OverlayControllerType.WIIMOTE ->
                rightStickAxes.setAll(NativeInput.WiimoteButton.NUNCHUCK_UP, NativeInput.WiimoteButton.NUNCHUCK_DOWN, NativeInput.WiimoteButton.NUNCHUCK_LEFT, NativeInput.WiimoteButton.NUNCHUCK_RIGHT)
        }
    }

    private fun IntArray.setAll(up: Int, down: Int, left: Int, right: Int) {
        this[0] = up
        this[1] = down
        this[2] = left
        this[3] = right
    }

    private fun sendStick(axes: IntArray, x: Float, y: Float) {
        if (axes[0] < 0) return
        onOverlayAxis(controllerIndex, axes[0], if (y < 0) -y else 0f)
        onOverlayAxis(controllerIndex, axes[1], if (y < 0) 0f else y)
        onOverlayAxis(controllerIndex, axes[2], if (x < 0) -x else 0f)
        onOverlayAxis(controllerIndex, axes[3], if (x < 0) 0f else x)
    }

    // ---- layout ----

    /** Pixel bounds of [layout] on this surface with the size settings, kept inside the surface. */
    private fun toPixels(layout: NormalizedRect, out: Rect, styleScale: Float) {
        val w = width
        val h = height
        val shortSide = min(w, h).toFloat()
        val scale = currentScale * styleScale
        val inputWidth = (layout.width * shortSide * scale).roundToInt().coerceIn(1, max(w, 1))
        val inputHeight = (layout.height * shortSide * scale).roundToInt().coerceIn(1, max(h, 1))
        val left = (layout.centerX * w - inputWidth * 0.5f).roundToInt().coerceIn(0, max(w - inputWidth, 0))
        val top = (layout.centerY * h - inputHeight * 0.5f).roundToInt().coerceIn(0, max(h - inputHeight, 0))
        out.set(left, top, left + inputWidth, top + inputHeight)
    }

    /** Inverse of [toPixels]: the layout that shows as [rect] with the current size settings. */
    private fun toLayout(rect: Rect, styleScale: Float): NormalizedRect {
        val shortSide = max(min(width, height), 1).toFloat()
        val scale = currentScale * styleScale
        return NormalizedRect(
            centerX = rect.exactCenterX() / max(width, 1),
            centerY = rect.exactCenterY() / max(height, 1),
            width = rect.width() / scale / shortSide,
            height = rect.height() / scale / shortSide,
        )
    }

    private fun <T : Input> T.placed(layout: NormalizedRect): T {
        this.layout = layout
        toPixels(layout, editRect, styleScale)
        setBounds(editRect.left, editRect.top, editRect.right, editRect.bottom)
        return this
    }

    private fun alphaOf(style: OverlayInputStyle?): Int = style?.opacity ?: currentAlpha

    /** Applies the per input opacity and size of [style]; call before [placed]. */
    private fun <T : Input> T.styled(style: OverlayInputStyle?): T {
        density = this@InputOverlayView.density
        alphaOverride = style?.opacity
        styleScale = (style?.scale ?: 1f).coerceIn(0.1f, 10f)
        return this
    }

    private fun addButton(button: Button) {
        button.scheduler = this
        inputs.add(button)
        buttons.add(button)
    }

    private fun builtinShape(config: OverlayInputConfig, style: OverlayInputStyle?): ButtonShape = when (style?.shape) {
        OverlayButtonShape.ROUND -> ButtonShape.CIRCLE
        OverlayButtonShape.SQUARE -> ButtonShape.ROUNDED_SQUARE
        null -> when (config) {
            OverlayInputConfig.BUTTON_ZL, OverlayInputConfig.BUTTON_ZR, OverlayInputConfig.BUTTON_Z -> ButtonShape.TRIGGER
            else -> if (config.isRectangular()) ButtonShape.PILL else ButtonShape.CIRCLE
        }
    }

    private fun extraShape(style: OverlayInputStyle?): ButtonShape = when (style?.shape) {
        OverlayButtonShape.ROUND -> ButtonShape.CIRCLE
        OverlayButtonShape.SQUARE -> ButtonShape.ROUNDED_SQUARE
        null -> ButtonShape.PILL
    }

    private fun glyphOf(button: OverlayButton): ButtonGlyph = when (button) {
        OverlayButton.HOME -> ButtonGlyph.Home
        OverlayButton.BLOW_MIC -> ButtonGlyph.Mic
        OverlayButton.MINUS -> ButtonGlyph.Minus
        OverlayButton.PLUS -> ButtonGlyph.Plus
        OverlayButton.ONE -> ButtonGlyph.Text("1")
        OverlayButton.TWO -> ButtonGlyph.Text("2")
        OverlayButton.L_STICK_CLICK -> ButtonGlyph.Text("L3")
        OverlayButton.R_STICK_CLICK -> ButtonGlyph.Text("R3")
        else -> ButtonGlyph.Text(button.name)
    }

    private fun addBuiltinButton(
        type: OverlayControllerType,
        profile: OverlayLayoutProfile,
        config: OverlayInputConfig,
        overlayButton: OverlayButton,
        layout: NormalizedRect,
    ): Button {
        val style = profile.styles[styleKey(config)]
        val button = Button(builtinShape(config, style), glyphOf(overlayButton), alphaOf(style))
        button.config = config
        profile.behaviors[config]?.let { button.behavior = it }
        button.output = ButtonsOutput(intArrayOf(nativeButtonFor(type, overlayButton)))
        button.mirrored = layout.centerX > 0.5f
        addButton(button.styled(style).placed(layout))
        return button
    }

    private fun addMacro(type: OverlayControllerType, macro: OverlayMacro, index: Int, layout: NormalizedRect) {
        val style = profile?.styles?.get(macro.id)
        val button = Button(extraShape(style), ButtonGlyph.Text(macro.label.ifBlank { "M${index + 1}" }), alphaOf(style))
        button.macroId = macro.id
        button.isExtra = true
        when (macro.kind) {
            MacroKind.COMBO -> {
                button.output = ButtonsOutput(
                    macro.buttons.map { nativeButtonFor(type, it.toOverlayInput()) }.filter { it >= 0 }.toIntArray()
                )
                button.behavior = macro.behavior
            }

            MacroKind.SEQUENCE -> {
                val steps = macro.steps
                button.output = SequenceOutput(
                    steps = Array(steps.size) { i ->
                        steps[i].buttons.map { nativeButtonFor(type, it.toOverlayInput()) }.filter { it >= 0 }.toIntArray()
                    },
                    durationsNanos = LongArray(steps.size) { i ->
                        steps[i].durationMs.coerceIn(MacroStep.MIN_STEP_MS, MacroStep.MAX_STEP_MS) * 1_000_000L
                    },
                    loop = macro.behavior.turbo,
                )
                // for sequences turbo means "repeat"; the sequence itself is the timing
                button.behavior = macro.behavior.copy(turbo = false)
            }
        }
        addButton(button.styled(style).placed(layout))
    }

    private fun addShortcut(shortcut: OverlayShortcut, layout: NormalizedRect) {
        val style = profile?.styles?.get(shortcut.id)
        val label = shortcut.label.ifBlank { shortcut.action.shortLabel() }
        val button = Button(extraShape(style), ButtonGlyph.Text(label), alphaOf(style))
        button.shortcutId = shortcut.id
        button.isExtra = true
        button.output = ShortcutOutput(shortcut.action)
        addButton(button.styled(style).placed(layout))
    }

    private fun resolveControllerType(): OverlayControllerType? {
        forcedControllerType?.let { return it }
        if (isControllerDisabled(controllerIndex)) return null
        return OverlayControllerType.fromNative(getControllerType(controllerIndex))
    }

    private fun setInputs() {
        cancelEditGesture()
        releaseAll()
        inputs.clear()
        buttons.clear()
        pointerInputs.clear()
        selected = null

        if (width == 0 || height == 0) return

        val type = resolveControllerType()
        controllerType = type
        controller?.controllerType = type
        if (type == null) {
            profile = null
            return
        }

        var effectiveSettings = settings
        if (settings.inputOverlayRectMap.isNotEmpty()) {
            // old pixel layout: show it converted now and have it saved converted
            effectiveSettings = settings.migrateLegacyRects(width, height)
            if (!legacyMigrationRequested) {
                legacyMigrationRequested = true
                onLegacyLayoutListener?.invoke(width, height)
            }
        }
        val profile = effectiveSettings.activeProfile(type)
        this.profile = profile
        Colors.palette = palette
        isPortrait = height > width
        controller?.isPortrait = isPortrait
        if (insets == LayoutInsets.NONE) readInsets(rootWindowInsets)
        setStickAxes(type)

        val legacy = profile.usesLegacyLayout
        val adaptive: AdaptiveOverlayLayout? = if (legacy) {
            null
        } else {
            computeAdaptiveLayout(
                OverlayLayoutSpec(
                    widthDp = width / density,
                    heightDp = height / density,
                    controllerType = type,
                    density = density,
                    insets = insets,
                    sizeScale = currentScale,
                    reservedTopLeftDp = statsReservePx / density,
                )
            )
        }

        fun defaultFor(config: OverlayInputConfig): NormalizedRect =
            adaptive?.normalized(config) ?: legacyDefaultRect(config, width, height, density)

        val editing = inputMode == InputMode.EDIT
        for (config in overlayInputsFor(type)) {
            val visible = effectiveSettings.isInputVisible(config)
            if (!visible && !editing) continue
            val layout = profile.savedRect(styleKey(config), isPortrait) ?: defaultFor(config)
            val style = profile.styles[styleKey(config)]
            val input: Input = when (config) {
                OverlayInputConfig.JOYSTICK_LEFT, OverlayInputConfig.JOYSTICK_RIGHT -> {
                    val axes = if (config == OverlayInputConfig.JOYSTICK_LEFT) leftStickAxes else rightStickAxes
                    Joystick({ x, y -> sendStick(axes, x, y) }, alphaOf(style)).also {
                        it.config = config
                        inputs.add(it.styled(style).placed(layout))
                        pointerInputs.add(it)
                    }
                }

                OverlayInputConfig.DPAD -> DPad({ direction, pressed ->
                    val id = nativeButtonFor(type, direction)
                    if (pressed) {
                        pressNative(id)
                        vibrate()
                    } else {
                        releaseNative(id)
                    }
                }, alphaOf(style)).also {
                    it.config = config
                    inputs.add(it.styled(style).placed(layout))
                    pointerInputs.add(it)
                }

                else -> addBuiltinButton(type, profile, config, config.toOverlayButton() ?: continue, layout)
            }
            input.ghost = !visible
        }

        // user made buttons that were never moved take the free spots, shortcuts first
        var slot = 0
        profile.shortcuts.forEachIndexed { index, shortcut ->
            val layout = profile.savedRect(shortcut.id, isPortrait)
                ?: adaptive?.let { it.normalized(it.extraSlot(slot++)) }
                ?: legacyShortcutRect(index, width, height, density)
            addShortcut(shortcut, layout)
        }
        profile.macros.forEachIndexed { index, macro ->
            val layout = profile.savedRect(macro.id, isPortrait)
                ?: adaptive?.let { it.normalized(it.extraSlot(slot++)) }
                ?: legacyMacroRect(index, width, height, density)
            addMacro(type, macro, index, layout)
        }

        if (editing) {
            val found = selectedTarget?.let { target -> inputs.firstOrNull { it.toTarget() == target } }
            // tells the editor which side the input is on (it may have been added just now)
            if (found != null || selectedTarget != null) select(found)
        }
        updateGestureExclusion()
    }

    /** Keeps the back gesture from stealing touches on inputs at the side edges. */
    private fun updateGestureExclusion() {
        val edge = dp(EDGE_EXCLUSION_DP)
        val rects = ArrayList<Rect>()
        for (input in inputs) {
            val r = input.rect
            if (r.left < edge || r.right > width - edge) rects.add(Rect(r))
        }
        systemGestureExclusionRects = rects
    }

    private fun Input.toTarget(): OverlayTarget? {
        macroId?.let { return OverlayTarget.Macro(it) }
        shortcutId?.let { return OverlayTarget.Shortcut(it) }
        return config?.let { OverlayTarget.Builtin(it) }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        requestFocus()
    }

    override fun onDraw(canvas: Canvas) {
        val editing = inputMode == InputMode.EDIT
        if (editing) drawEditBackground(canvas)
        for (i in 0 until inputs.size) {
            inputs[i].draw(canvas)
        }
        if (editing) drawEditChrome(canvas)
    }

    // ---- play mode touch ----

    private fun buttonAt(x: Float, y: Float): Button? {
        for (i in buttons.size - 1 downTo 0) {
            val button = buttons[i]
            if (button.isInside(x, y)) return button
        }
        return null
    }

    private fun onPlayTouch(event: MotionEvent): Boolean {
        val now = System.nanoTime()
        var handled = false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                for (i in 0 until pointerInputs.size) {
                    if (pointerInputs[i].onTouch(event)) return true
                }
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                val button = buttonAt(event.getX(index), event.getY(index))
                if (button != null && pointerId < MAX_POINTER_ID) {
                    pointerButtons[pointerId]?.pointerUp(now)
                    pointerButtons[pointerId] = button
                    button.pointerDown(now)
                    vibrate()
                    handled = true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until pointerInputs.size) {
                    if (pointerInputs[i].onTouch(event)) handled = true
                }
                if (!settings.slideBetweenButtons) return handled
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    if (pointerId >= MAX_POINTER_ID) continue
                    val current = pointerButtons[pointerId] ?: continue
                    val hovered = buttonAt(event.getX(i), event.getY(i))
                    // leaving every button keeps the last one held: the finger may just drift
                    if (hovered == null || hovered === current) continue
                    current.pointerUp(now)
                    pointerButtons[pointerId] = hovered
                    hovered.pointerDown(now)
                    vibrate()
                    handled = true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                for (i in 0 until pointerInputs.size) {
                    if (pointerInputs[i].onTouch(event)) handled = true
                }
                val pointerId = event.getPointerId(event.actionIndex)
                if (pointerId < MAX_POINTER_ID) {
                    val button = pointerButtons[pointerId]
                    if (button != null) {
                        pointerButtons[pointerId] = null
                        button.pointerUp(now)
                        handled = true
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until pointerInputs.size) pointerInputs[i].onTouch(event)
                for (id in 0 until MAX_POINTER_ID) {
                    val button = pointerButtons[id] ?: continue
                    pointerButtons[id] = null
                    button.pointerUp(now)
                }
                handled = true
            }
        }
        return handled
    }

    // ---- edit mode ----

    /** Selects [target] from the editor UI, e.g. a button just added. */
    fun selectTarget(target: OverlayTarget?) {
        val input = target?.let { t -> inputs.firstOrNull { it.toTarget() == t } }
        if (input == null && target != null) {
            // just added: the inputs are rebuilt with it on the next settings update
            selected = null
            selectedTarget = target
            controller?.onSelection(target, false)
        } else {
            select(input)
        }
        invalidate()
    }

    private fun select(input: Input?) {
        selected = input
        selectedTarget = input?.toTarget()
        controller?.onSelection(selectedTarget, input?.let { it.rect.exactCenterX() < width / 2f } ?: false)
    }

    private fun cancelEditGesture() {
        gesture = GESTURE_NONE
        activePointerId = -1
        pinchStartDistance = 0f
        editChanged = false
        guideX = Float.NaN
        guideY = Float.NaN
    }

    private fun inputAt(x: Float, y: Float): Input? {
        for (i in inputs.size - 1 downTo 0) {
            if (inputs[i].isInside(x, y)) return inputs[i]
        }
        // small inputs: fall back to the bounding box
        for (i in inputs.size - 1 downTo 0) {
            if (inputs[i].rect.contains(x.toInt(), y.toInt())) return inputs[i]
        }
        return null
    }

    /** Corner handle of the selection under ([x], [y]): 0 top left, 1 top right, 2 bottom right, 3 bottom left; -1 none. */
    private fun handleAt(input: Input, x: Float, y: Float): Int {
        val r = input.rect
        val reach = dp(HANDLE_TOUCH_DP)
        for (corner in 0 until 4) {
            val cx = if (corner == 0 || corner == 3) r.left.toFloat() else r.right.toFloat()
            val cy = if (corner < 2) r.top.toFloat() else r.bottom.toFloat()
            if (abs(x - cx) <= reach && abs(y - cy) <= reach) return corner
        }
        return -1
    }

    private fun minInputSize() = dp(MIN_INPUT_DP).roundToInt()

    /**
     * Snaps an input spanning [start]..[start]+[size] on one axis to the nearest guide: the middle
     * of the screen, the edges and centers of the other inputs and their mirrored spots.
     * Returns the new start and sets [guideX]/[guideY].
     */
    private fun snapAxis(start: Float, size: Float, horizontal: Boolean): Float {
        val extent = if (horizontal) width.toFloat() else height.toFloat()
        snapBest = dp(SNAP_DP)
        snapDelta = Float.NaN
        snapLine = Float.NaN
        considerSnap(extent / 2f, start, size)
        val moving = selected
        for (i in 0 until inputs.size) {
            val other = inputs[i]
            if (other === moving) continue
            val r = other.rect
            if (horizontal) {
                considerSnap(r.left.toFloat(), start, size)
                considerSnap(r.exactCenterX(), start, size)
                considerSnap(r.right.toFloat(), start, size)
                // mirrored across the middle, for symmetric layouts
                considerSnap(extent - r.exactCenterX(), start, size)
            } else {
                considerSnap(r.top.toFloat(), start, size)
                considerSnap(r.exactCenterY(), start, size)
                considerSnap(r.bottom.toFloat(), start, size)
            }
        }
        if (horizontal) guideX = snapLine else guideY = snapLine
        return if (snapDelta.isNaN()) start else start + snapDelta
    }

    // state of snapAxis, kept in fields so moving an input allocates nothing
    private var snapBest = 0f
    private var snapDelta = Float.NaN
    private var snapLine = Float.NaN

    /** The moving input's start, center and end against [line]. */
    private fun considerSnap(line: Float, start: Float, size: Float) {
        considerSnapDelta(line, line - start)
        considerSnapDelta(line, line - (start + size / 2f))
        considerSnapDelta(line, line - (start + size))
    }

    private fun considerSnapDelta(line: Float, delta: Float) {
        if (abs(delta) < snapBest) {
            snapBest = abs(delta)
            snapDelta = delta
            snapLine = line
        }
    }

    private fun snapToGrid(value: Float): Float {
        val grid = dp(GRID_DP)
        return (value / grid).roundToInt() * grid
    }

    private fun moveSelected(input: Input, dx: Float, dy: Float) {
        val w = startRect.width()
        val h = startRect.height()
        var left = startRect.left + dx
        var top = startRect.top + dy
        guideX = Float.NaN
        guideY = Float.NaN
        if (settings.snapToGuides) {
            left = snapAxis(left, w.toFloat(), horizontal = true)
            top = snapAxis(top, h.toFloat(), horizontal = false)
        }
        if (settings.snapToGrid) {
            if (guideX.isNaN()) left = snapToGrid(left)
            if (guideY.isNaN()) top = snapToGrid(top)
        }
        val l = left.roundToInt().coerceIn(0, max(width - w, 0))
        val t = top.roundToInt().coerceIn(0, max(height - h, 0))
        input.setBounds(l, t, l + w, t + h)
    }

    /** Drags the [resizeCorner] handle; the opposite corner stays put. */
    private fun resizeSelected(input: Input, dx: Float, dy: Float) {
        val minSize = minInputSize()
        val signX = if (resizeCorner == 1 || resizeCorner == 2) 1 else -1
        val signY = if (resizeCorner >= 2) 1 else -1
        var newWidth = startRect.width() + signX * dx
        var newHeight = startRect.height() + signY * dy
        if (input.keepsAspectRatio) {
            val factor = max(newWidth / startRect.width(), newHeight / startRect.height())
            newWidth = startRect.width() * factor
            newHeight = startRect.height() * factor
        }
        if (settings.snapToGrid) {
            newWidth = max(snapToGrid(newWidth), dp(GRID_DP))
            newHeight = max(snapToGrid(newHeight), dp(GRID_DP))
        }
        var w = newWidth.roundToInt().coerceAtLeast(minSize)
        var h = newHeight.roundToInt().coerceAtLeast(minSize)
        val anchorX = if (signX > 0) startRect.left else startRect.right
        val anchorY = if (signY > 0) startRect.top else startRect.bottom
        w = min(w, if (signX > 0) width - anchorX else anchorX)
        h = min(h, if (signY > 0) height - anchorY else anchorY)
        val left = if (signX > 0) anchorX else anchorX - w
        val top = if (signY > 0) anchorY else anchorY - h
        input.setBounds(left, top, left + w, top + h)
    }

    private fun scaleSelected(input: Input, factor: Float) {
        val minSize = minInputSize()
        val newWidth = (startRect.width() * factor).roundToInt().coerceIn(minSize, max(width, minSize))
        val newHeight = (startRect.height() * factor).roundToInt().coerceIn(minSize, max(height, minSize))
        val left = (startRect.exactCenterX() - newWidth * 0.5f).roundToInt().coerceIn(0, max(width - newWidth, 0))
        val top = (startRect.exactCenterY() - newHeight * 0.5f).roundToInt().coerceIn(0, max(height - newHeight, 0))
        input.setBounds(left, top, left + newWidth, top + newHeight)
    }

    private fun finishEdit() {
        val input = selected
        if (input != null && editChanged) {
            val layout = toLayout(input.rect, input.styleScale)
            input.layout = layout
            input.toTarget()?.let { target ->
                // keep the selection panel on the side away from the input
                controller?.onSelection(target, input.rect.exactCenterX() < width / 2f)
                onInputMovedListener?.invoke(target, layout, isPortrait)
            }
        }
        cancelEditGesture()
    }

    private fun onEditTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelEditGesture()
                val x = event.x
                val y = event.y
                activePointerId = event.getPointerId(0)
                downX = x
                downY = y
                val current = selected
                if (current != null && handleAt(current, x, y) >= 0) {
                    resizeCorner = handleAt(current, x, y)
                    startRect.set(current.rect)
                    gesture = GESTURE_RESIZE
                    return true
                }
                val hit = inputAt(x, y)
                if (hit == null) {
                    select(null)
                    return true
                }
                if (hit !== current) select(hit)
                startRect.set(hit.rect)
                gesture = GESTURE_PENDING
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val input = selected ?: return true
                if (event.pointerCount == 2) {
                    // second finger: pinch to resize
                    startRect.set(input.rect)
                    pinchStartDistance = max(hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1)), 1f)
                    gesture = GESTURE_PINCH
                    guideX = Float.NaN
                    guideY = Float.NaN
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val input = selected ?: return true
                when (gesture) {
                    GESTURE_PINCH -> {
                        if (event.pointerCount < 2) return true
                        val distance = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                        scaleSelected(input, distance / pinchStartDistance)
                        editChanged = true
                    }

                    GESTURE_PENDING, GESTURE_DRAG, GESTURE_RESIZE -> {
                        val index = event.findPointerIndex(activePointerId)
                        if (index < 0) return true
                        val dx = event.getX(index) - downX
                        val dy = event.getY(index) - downY
                        if (gesture == GESTURE_PENDING) {
                            if (abs(dx) < touchSlop && abs(dy) < touchSlop) return true
                            gesture = GESTURE_DRAG
                        }
                        if (gesture == GESTURE_RESIZE) resizeSelected(input, dx, dy) else moveSelected(input, dx, dy)
                        editChanged = true
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val input = selected ?: return true
                if (gesture == GESTURE_PINCH) {
                    // go on dragging with the finger left, from where the input is now
                    val remaining = if (event.actionIndex == 0) 1 else 0
                    activePointerId = event.getPointerId(remaining)
                    startRect.set(input.rect)
                    downX = event.getX(remaining)
                    downY = event.getY(remaining)
                    gesture = GESTURE_DRAG
                } else if (event.getPointerId(event.actionIndex) == activePointerId) {
                    gesture = GESTURE_NONE
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finishEdit()
                return true
            }
        }
        return false
    }

    private fun drawEditBackground(canvas: Canvas) {
        // a light shade tells edit mode apart from play
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), editShadePaint)
        if (!settings.snapToGrid) return
        val grid = dp(GRID_DP)
        var x = grid
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += grid
        }
        var y = grid
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += grid
        }
    }

    private fun drawEditChrome(canvas: Canvas) {
        val selectedInput = selected
        for (i in 0 until inputs.size) {
            val input = inputs[i]
            if (input === selectedInput) continue
            val r = input.rect
            canvas.drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), outlinePaint)
        }
        if (!guideX.isNaN()) canvas.drawLine(guideX, 0f, guideX, height.toFloat(), guidePaint)
        if (!guideY.isNaN()) canvas.drawLine(0f, guideY, width.toFloat(), guideY, guidePaint)
        if (selectedInput != null) {
            val r = selectedInput.rect
            val pad = dp(3f)
            canvas.drawRect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad, selectionPaint)
            val handle = dp(HANDLE_DP)
            for (corner in 0 until 4) {
                val cx = if (corner == 0 || corner == 3) r.left - pad else r.right + pad
                val cy = if (corner < 2) r.top - pad else r.bottom + pad
                canvas.drawCircle(cx, cy, handle, handleFill)
                canvas.drawCircle(cx, cy, handle, handleStroke)
            }
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val processed = when (inputMode) {
            InputMode.DEFAULT -> onPlayTouch(event)
            InputMode.EDIT -> onEditTouch(event)
        }
        if (processed) invalidate()
        return processed
    }

    companion object {
        private const val MIN_INPUT_DP = 24f
        private const val GRID_DP = 16f
        private const val SNAP_DP = 8f
        private const val HANDLE_DP = 7f
        private const val HANDLE_TOUCH_DP = 22f
        private const val EDGE_EXCLUSION_DP = 40f
        private const val MAX_POINTER_ID = 32
        private const val MAX_NATIVE_BUTTON_ID = 64

        private const val GESTURE_NONE = 0
        private const val GESTURE_PENDING = 1
        private const val GESTURE_DRAG = 2
        private const val GESTURE_RESIZE = 3
        private const val GESTURE_PINCH = 4
    }
}

private fun OverlayInputConfig.toOverlayButton(): OverlayButton? = when (this) {
    OverlayInputConfig.BUTTON_A -> OverlayButton.A
    OverlayInputConfig.BUTTON_B -> OverlayButton.B
    OverlayInputConfig.BUTTON_ONE -> OverlayButton.ONE
    OverlayInputConfig.BUTTON_TWO -> OverlayButton.TWO
    OverlayInputConfig.BUTTON_C -> OverlayButton.C
    OverlayInputConfig.BUTTON_Z -> OverlayButton.Z
    OverlayInputConfig.BUTTON_HOME -> OverlayButton.HOME
    OverlayInputConfig.BUTTON_L -> OverlayButton.L
    OverlayInputConfig.BUTTON_L_STICK_CLICK -> OverlayButton.L_STICK_CLICK
    OverlayInputConfig.BUTTON_MINUS -> OverlayButton.MINUS
    OverlayInputConfig.BUTTON_PLUS -> OverlayButton.PLUS
    OverlayInputConfig.BUTTON_R -> OverlayButton.R
    OverlayInputConfig.BUTTON_R_STICK_CLICK -> OverlayButton.R_STICK_CLICK
    OverlayInputConfig.BUTTON_X -> OverlayButton.X
    OverlayInputConfig.BUTTON_Y -> OverlayButton.Y
    OverlayInputConfig.BUTTON_ZL -> OverlayButton.ZL
    OverlayInputConfig.BUTTON_ZR -> OverlayButton.ZR
    OverlayInputConfig.BUTTON_BLOW_MIC -> OverlayButton.BLOW_MIC
    OverlayInputConfig.JOYSTICK_LEFT, OverlayInputConfig.JOYSTICK_RIGHT, OverlayInputConfig.DPAD -> null
}

/**
 * The overlay in Compose. [settings] are drawn as given: the editor passes its working copy.
 * [forcedControllerType] shows another controller type's layout (settings screen, no game running).
 */
@Composable
fun InputOverlaySurface(
    isVisible: Boolean,
    inputOverlaySettings: InputOverlaySettings,
    inputMode: InputOverlayView.InputMode,
    controller: InputOverlayController,
    palette: OverlayPalette,
    modifier: Modifier = Modifier.fillMaxSize(),
    onLegacyLayout: (width: Int, height: Int) -> Unit = { _, _ -> },
    onShortcut: (HotkeyAction) -> Unit = {},
    onInputMoved: (OverlayTarget, NormalizedRect, Boolean) -> Unit = { _, _, _ -> },
    forcedControllerType: OverlayControllerType? = null,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            InputOverlayView(context).apply {
                this.forcedControllerType = forcedControllerType
                this.controller = controller
                onLegacyLayoutListener = onLegacyLayout
                onShortcutListener = onShortcut
                onInputMovedListener = onInputMoved
                applyPalette(palette)
                setVisible(isVisible)
                setInputMode(inputMode)
                applySettings(inputOverlaySettings)
            }
        },
        update = { view ->
            if (view.controller !== controller) view.controller = controller
            view.forcedControllerType = forcedControllerType
            view.onLegacyLayoutListener = onLegacyLayout
            view.onShortcutListener = onShortcut
            view.onInputMovedListener = onInputMoved
            view.applyPalette(palette)
            view.setVisible(isVisible)
            view.setInputMode(inputMode)
            view.applySettings(inputOverlaySettings)
        }
    )
}
