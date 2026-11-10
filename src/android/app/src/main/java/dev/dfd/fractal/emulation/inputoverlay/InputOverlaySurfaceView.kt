package dev.dfd.fractal.emulation.inputoverlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.dfd.fractal.R
import dev.dfd.fractal.common.android.context.getDeviceVibrator
import dev.dfd.fractal.common.settings.InputOverlaySettings
import dev.dfd.fractal.common.settings.MacroKind
import dev.dfd.fractal.common.settings.MacroStep
import dev.dfd.fractal.common.settings.NormalizedRect
import dev.dfd.fractal.common.settings.OverlayControllerType
import dev.dfd.fractal.common.settings.OverlayInputConfig
import dev.dfd.fractal.common.settings.OverlayLayoutProfile
import dev.dfd.fractal.common.settings.OverlayMacro
import dev.dfd.fractal.common.settings.activeProfile
import dev.dfd.fractal.common.settings.getDefaultRectangle
import dev.dfd.fractal.common.settings.migrateLegacyRects
import dev.dfd.fractal.emulation.inputoverlay.inputs.Button
import dev.dfd.fractal.emulation.inputoverlay.inputs.ButtonOutput
import dev.dfd.fractal.emulation.inputoverlay.inputs.DPad
import dev.dfd.fractal.emulation.inputoverlay.inputs.Input
import dev.dfd.fractal.emulation.inputoverlay.inputs.Joystick
import dev.dfd.fractal.emulation.inputoverlay.inputs.RectangleButton
import dev.dfd.fractal.emulation.inputoverlay.inputs.RoundButton
import dev.dfd.fractal.emulation.inputoverlay.inputs.TickScheduler
import dev.dfd.fractal.emulation.inputoverlay.inputs.innerdrawing.BlowButtonInnerDrawing
import dev.dfd.fractal.emulation.inputoverlay.inputs.innerdrawing.ButtonInnerDrawing
import dev.dfd.fractal.emulation.inputoverlay.inputs.innerdrawing.HomeButtonInnerDrawing
import dev.dfd.fractal.emulation.inputoverlay.inputs.innerdrawing.TextButtonInnerDrawing
import dev.dfd.fractal.nativeinterface.NativeInput
import dev.dfd.fractal.nativeinterface.NativeInput.getControllerType
import dev.dfd.fractal.nativeinterface.NativeInput.isControllerDisabled
import dev.dfd.fractal.nativeinterface.NativeInput.onOverlayAxis
import dev.dfd.fractal.nativeinterface.NativeInput.onOverlayButton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class InputOverlaySurfaceView(context: Context) : SurfaceView(context), OnTouchListener,
    TickScheduler, Choreographer.FrameCallback {
    enum class InputMode {
        DEFAULT, EDIT_POSITION, EDIT_SIZE,
    }

    var onEditFinishedListener: ((OverlayEditResult) -> Unit)? = null
    var onInputLongPressListener: ((OverlayTarget) -> Unit)? = null

    /** Old absolute pixel layout found; called with the overlay size to convert it. */
    var onLegacyLayoutListener: ((width: Int, height: Int) -> Unit)? = null
    var controller: InputOverlayController? = null
        set(value) {
            field?.view = null
            field = value
            value?.view = this
            value?.controllerType = controllerType
        }

    private var inputMode = InputMode.DEFAULT
    private val pixelDensity: Int = context.resources.displayMetrics.densityDpi
    private var controllerType: OverlayControllerType? = null
    private var onJoystickChange: (OverlayInput, Float, Float, Float, Float) -> Unit =
        { _, _, _, _, _ -> }
    private var profile: OverlayLayoutProfile? = null

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

    private val vibrator: Vibrator = context.getDeviceVibrator()
    private val buttonTouchVibrationEffect =
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    private val inputsMinSize = dpToPx(INPUTS_MIN_SIZE_DP)
    private val gridSize = dpToPx(GRID_SIZE_DP)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val isVisible: Boolean
        get() = visibility == VISIBLE

    private var settings: InputOverlaySettings = InputOverlaySettings()
    private var previewAlpha: Int? = null
    private var previewScale: Float? = null
    private val controllerIndex get() = settings.controllerIndex
    private val currentAlpha get() = previewAlpha ?: settings.alpha
    private val currentScale get() = previewScale ?: settings.scale
    private val isVibrateOnTouchEnabled: Boolean
        get() = settings.isVibrateOnTouchEnabled && vibrator.hasVibrator()

    /** Moves/resizes of the current edit session, kept across rebuilds until saved. */
    private val pendingRects = HashMap<OverlayInputConfig, NormalizedRect>()
    private val pendingMacroRects = HashMap<String, NormalizedRect>()

    /** The saved layout was reset; ignore saved rects until new settings arrive. */
    private var showDefaults = false
    private var legacyMigrationRequested = false
    private var isTicking = false

    // edit gesture state
    private var editedInput: Input? = null
    private val editStartRect = Rect()
    private var editDownX = 0f
    private var editDownY = 0f
    private var editMoved = false
    private var editLongPressed = false
    private var pinchStartDistance = 0f
    private val editRect = Rect()
    private val longPressRunnable = Runnable {
        val input = editedInput ?: return@Runnable
        if (editMoved) return@Runnable
        editLongPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        input.toTarget()?.let { onInputLongPressListener?.invoke(it) }
    }

    private val selectedColor = resources.getColor(R.color.purple, context.theme)
    private val editOutlineColor = Color.argb(140, 255, 255, 255)
    private val gridPaint = Paint().apply {
        color = Color.argb(40, 255, 255, 255)
        strokeWidth = 1f
    }

    init {
        visibility = GONE
        setOnTouchListener(this)
    }

    private fun dpToPx(dp: Int) =
        (dp * pixelDensity.toFloat() / DisplayMetrics.DENSITY_DEFAULT).roundToInt()

    fun applySettings(inputOverlaySettings: InputOverlaySettings) {
        if (this.settings == inputOverlaySettings) {
            return
        }

        this.settings = inputOverlaySettings
        previewAlpha = null
        previewScale = null
        showDefaults = false

        setInputs()

        invalidate()
    }

    fun setVisible(isVisible: Boolean) {
        if (this.isVisible == isVisible) {
            return
        }

        visibility = if (isVisible) VISIBLE else GONE
        if (!isVisible) releaseAll()

        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // layouts are resolution independent: rebuild for the new size
        setInputs()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller?.view = this
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseAll()
        controller?.view = null
    }

    fun setInputMode(inputMode: InputMode) {
        if (this.inputMode == inputMode) {
            return
        }
        val wasEditing = this.inputMode != InputMode.DEFAULT
        this.inputMode = inputMode
        cancelEditGesture()

        if (inputMode != InputMode.DEFAULT) {
            // nothing stays held or latched while editing
            if (!wasEditing) releaseAll()
            updateEditOutlines()
            invalidate()
            return
        }

        for (input in inputs) input.disableDrawingBoundingRect()
        invalidate()

        commitEdits()
    }

    /** Hands the pending moves and resizes to [onEditFinishedListener] to be saved. */
    fun commitEdits() {
        val type = controllerType ?: return
        if (pendingRects.isEmpty() && pendingMacroRects.isEmpty()) return
        val result = OverlayEditResult(type, HashMap(pendingRects), HashMap(pendingMacroRects))
        pendingRects.clear()
        pendingMacroRects.clear()
        onEditFinishedListener?.invoke(result)
    }

    fun discardEdits() {
        cancelEditGesture()
        pendingRects.clear()
        pendingMacroRects.clear()
        setInputs()
        invalidate()
    }

    fun showDefaultLayout() {
        cancelEditGesture()
        pendingRects.clear()
        pendingMacroRects.clear()
        showDefaults = true
        setInputs()
        invalidate()
    }

    fun previewAppearance(alpha: Int?, scale: Float?) {
        if (alpha != null && alpha != currentAlpha) {
            previewAlpha = alpha
            for (input in inputs) input.setAlpha(alpha)
        }
        if (scale != null && scale != currentScale) {
            previewScale = scale
            for (input in inputs) {
                toPixels(input.layout, editRect)
                input.setBounds(editRect.left, editRect.top, editRect.right, editRect.bottom)
            }
        }
        invalidate()
    }

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

    // ---- joysticks ----

    private fun onOverlayAxis(axis: Int, value: Float) {
        onOverlayAxis(controllerIndex, axis, value)
    }

    private fun onVPADJoystickStateChange(joystick: OverlayInput, up: Float, down: Float, left: Float, right: Float) {
        if (joystick == OverlayJoystick.LEFT) {
            onOverlayAxis(NativeInput.VPADButton.STICKL_UP, up)
            onOverlayAxis(NativeInput.VPADButton.STICKL_DOWN, down)
            onOverlayAxis(NativeInput.VPADButton.STICKL_LEFT, left)
            onOverlayAxis(NativeInput.VPADButton.STICKL_RIGHT, right)
        } else if (joystick == OverlayJoystick.RIGHT) {
            onOverlayAxis(NativeInput.VPADButton.STICKR_UP, up)
            onOverlayAxis(NativeInput.VPADButton.STICKR_DOWN, down)
            onOverlayAxis(NativeInput.VPADButton.STICKR_LEFT, left)
            onOverlayAxis(NativeInput.VPADButton.STICKR_RIGHT, right)
        }
    }

    private fun onProJoystickStateChange(joystick: OverlayInput, up: Float, down: Float, left: Float, right: Float) {
        if (joystick == OverlayJoystick.LEFT) {
            onOverlayAxis(NativeInput.ProButton.STICKL_UP, up)
            onOverlayAxis(NativeInput.ProButton.STICKL_DOWN, down)
            onOverlayAxis(NativeInput.ProButton.STICKL_LEFT, left)
            onOverlayAxis(NativeInput.ProButton.STICKL_RIGHT, right)
        } else if (joystick == OverlayJoystick.RIGHT) {
            onOverlayAxis(NativeInput.ProButton.STICKR_UP, up)
            onOverlayAxis(NativeInput.ProButton.STICKR_DOWN, down)
            onOverlayAxis(NativeInput.ProButton.STICKR_LEFT, left)
            onOverlayAxis(NativeInput.ProButton.STICKR_RIGHT, right)
        }
    }

    private fun onClassicJoystickStateChange(joystick: OverlayInput, up: Float, down: Float, left: Float, right: Float) {
        if (joystick == OverlayJoystick.LEFT) {
            onOverlayAxis(NativeInput.ClassicButton.STICKL_UP, up)
            onOverlayAxis(NativeInput.ClassicButton.STICKL_DOWN, down)
            onOverlayAxis(NativeInput.ClassicButton.STICKL_LEFT, left)
            onOverlayAxis(NativeInput.ClassicButton.STICKL_RIGHT, right)
        } else if (joystick == OverlayJoystick.RIGHT) {
            onOverlayAxis(NativeInput.ClassicButton.STICKR_UP, up)
            onOverlayAxis(NativeInput.ClassicButton.STICKR_DOWN, down)
            onOverlayAxis(NativeInput.ClassicButton.STICKR_LEFT, left)
            onOverlayAxis(NativeInput.ClassicButton.STICKR_RIGHT, right)
        }
    }

    private fun onWiimoteJoystickStateChange(joystick: OverlayInput, up: Float, down: Float, left: Float, right: Float) {
        if (joystick == OverlayJoystick.RIGHT) {
            onOverlayAxis(NativeInput.WiimoteButton.NUNCHUCK_UP, up)
            onOverlayAxis(NativeInput.WiimoteButton.NUNCHUCK_DOWN, down)
            onOverlayAxis(NativeInput.WiimoteButton.NUNCHUCK_LEFT, left)
            onOverlayAxis(NativeInput.WiimoteButton.NUNCHUCK_RIGHT, right)
        }
    }

    private fun onJoystickStateChange(joystick: OverlayInput, x: Float, y: Float) {
        val up = if (y < 0) -y else 0f
        val down = if (y < 0) 0f else y
        val left = if (x < 0) -x else 0f
        val right = if (x < 0) 0f else x
        onJoystickChange(joystick, up, down, left, right)
    }

    // ---- layout ----

    /** Pixel bounds of [layout] on this surface with the global scale, kept inside the surface. */
    private fun toPixels(layout: NormalizedRect, out: Rect) {
        val w = width
        val h = height
        val shortSide = min(w, h).toFloat()
        val scale = currentScale
        val inputWidth = (layout.width * shortSide * scale).roundToInt().coerceIn(1, max(w, 1))
        val inputHeight = (layout.height * shortSide * scale).roundToInt().coerceIn(1, max(h, 1))
        val left = (layout.centerX * w - inputWidth * 0.5f).roundToInt().coerceIn(0, max(w - inputWidth, 0))
        val top = (layout.centerY * h - inputHeight * 0.5f).roundToInt().coerceIn(0, max(h - inputHeight, 0))
        out.set(left, top, left + inputWidth, top + inputHeight)
    }

    /** Inverse of [toPixels]: the layout that shows as [rect] with the current scale. */
    private fun toLayout(rect: Rect): NormalizedRect {
        val shortSide = max(min(width, height), 1).toFloat()
        val scale = currentScale
        return NormalizedRect(
            centerX = rect.exactCenterX() / max(width, 1),
            centerY = rect.exactCenterY() / max(height, 1),
            width = rect.width() / scale / shortSide,
            height = rect.height() / scale / shortSide,
        )
    }

    private fun defaultLayoutFor(config: OverlayInputConfig): NormalizedRect {
        val r = getDefaultRectangle(config, width, height, pixelDensity)
        return NormalizedRect.fromPixels(r.left, r.top, r.right, r.bottom, width, height)
    }

    private fun defaultLayoutForMacro(index: Int): NormalizedRect {
        val shortSide = max(min(width, height), 1).toFloat()
        val column = index / 4
        val row = index % 4
        return NormalizedRect(
            centerX = (0.5f + (column - 0.5f) * 0.14f).coerceIn(0.1f, 0.9f),
            centerY = 0.3f + row * 0.13f,
            width = dpToPx(MACRO_WIDTH_DP) / shortSide,
            height = dpToPx(MACRO_HEIGHT_DP) / shortSide,
        )
    }

    private fun layoutFor(config: OverlayInputConfig, profile: OverlayLayoutProfile): NormalizedRect =
        pendingRects[config]
            ?: (if (showDefaults) null else profile.rects[config])
            ?: defaultLayoutFor(config)

    private fun layoutFor(macro: OverlayMacro, index: Int): NormalizedRect =
        pendingMacroRects[macro.id]
            ?: (if (showDefaults) null else macro.rect)
            ?: defaultLayoutForMacro(index)

    private fun <T : Input> T.placed(layout: NormalizedRect): T {
        this.layout = layout
        toPixels(layout, editRect)
        setBounds(editRect.left, editRect.top, editRect.right, editRect.bottom)
        return this
    }

    private fun newButtonRect(): Rect = Rect(0, 0, 1, 1)

    private fun addButton(button: Button, config: OverlayInputConfig?, behaviorSource: OverlayLayoutProfile?) {
        button.config = config
        if (config != null && behaviorSource != null) {
            behaviorSource.behaviors[config]?.let { button.behavior = it }
        }
        button.scheduler = this
        inputs.add(button)
        buttons.add(button)
    }

    private fun addBuiltinButton(
        type: OverlayControllerType,
        profile: OverlayLayoutProfile,
        config: OverlayInputConfig,
        overlayButton: OverlayButton,
    ) {
        val innerDrawing: ButtonInnerDrawing = when (overlayButton) {
            OverlayButton.HOME -> HomeButtonInnerDrawing()
            OverlayButton.BLOW_MIC -> BlowButtonInnerDrawing()
            OverlayButton.MINUS -> TextButtonInnerDrawing("-")
            OverlayButton.PLUS -> TextButtonInnerDrawing("+")
            OverlayButton.ONE -> TextButtonInnerDrawing("1")
            OverlayButton.TWO -> TextButtonInnerDrawing("2")
            OverlayButton.L_STICK_CLICK -> TextButtonInnerDrawing("L3")
            OverlayButton.R_STICK_CLICK -> TextButtonInnerDrawing("R3")
            else -> TextButtonInnerDrawing(overlayButton.name)
        }
        val isRectangle = when (overlayButton) {
            OverlayButton.ZL, OverlayButton.ZR, OverlayButton.L, OverlayButton.R, OverlayButton.Z -> true
            else -> false
        }
        val button = if (isRectangle) {
            RectangleButton(innerDrawing, currentAlpha, newButtonRect())
        } else {
            RoundButton(innerDrawing, currentAlpha, newButtonRect())
        }
        button.output = ButtonsOutput(intArrayOf(nativeButtonFor(type, overlayButton)))
        addButton(button.placed(layoutFor(config, profile)), config, profile)
    }

    private fun addMacro(type: OverlayControllerType, macro: OverlayMacro, index: Int) {
        val label = macro.label.ifBlank { "M${index + 1}" }
        val button = RectangleButton(TextButtonInnerDrawing(label), currentAlpha, newButtonRect())
        button.macroId = macro.id
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
        addButton(button.placed(layoutFor(macro, index)), null, null)
    }

    private fun setInputs() {
        cancelEditGesture()
        releaseAll()
        inputs.clear()
        buttons.clear()
        pointerInputs.clear()

        if (width == 0 || height == 0) return

        val type = if (isControllerDisabled(controllerIndex)) null
        else OverlayControllerType.fromNative(getControllerType(controllerIndex))
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
        if (this.profile?.id != profile.id) {
            // edits belong to the profile they were made on
            pendingRects.clear()
            pendingMacroRects.clear()
        }
        this.profile = profile

        onJoystickChange = when (type) {
            OverlayControllerType.VPAD -> ::onVPADJoystickStateChange
            OverlayControllerType.PRO -> ::onProJoystickStateChange
            OverlayControllerType.CLASSIC -> ::onClassicJoystickStateChange
            OverlayControllerType.WIIMOTE -> ::onWiimoteJoystickStateChange
        }

        for (config in overlayInputsFor(type)) {
            if (settings.inputVisibilityMap[config] == false) continue
            when (config) {
                OverlayInputConfig.JOYSTICK_LEFT, OverlayInputConfig.JOYSTICK_RIGHT -> {
                    val joystick = if (config == OverlayInputConfig.JOYSTICK_LEFT) OverlayJoystick.LEFT else OverlayJoystick.RIGHT
                    val input = Joystick({ x, y -> onJoystickStateChange(joystick, x, y) }, currentAlpha, newButtonRect())
                    input.config = config
                    inputs.add(input.placed(layoutFor(config, profile)))
                    pointerInputs.add(input)
                }

                OverlayInputConfig.DPAD -> {
                    val input = DPad({ direction, pressed ->
                        val id = nativeButtonFor(type, direction)
                        if (pressed) {
                            pressNative(id)
                            vibrate()
                        } else {
                            releaseNative(id)
                        }
                    }, currentAlpha, newButtonRect())
                    input.config = config
                    inputs.add(input.placed(layoutFor(config, profile)))
                    pointerInputs.add(input)
                }

                else -> addBuiltinButton(type, profile, config, config.toOverlayButton() ?: continue)
            }
        }

        profile.macros.forEachIndexed { index, macro -> addMacro(type, macro, index) }

        if (inputMode != InputMode.DEFAULT) updateEditOutlines()
    }

    private fun Input.toTarget(): OverlayTarget? {
        macroId?.let { return OverlayTarget.Macro(it) }
        return config?.let { OverlayTarget.Builtin(it) }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        setWillNotDraw(false)
        requestFocus()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if (inputMode != InputMode.DEFAULT && settings.snapToGrid && gridSize > 0) {
            var x = gridSize.toFloat()
            while (x < width) {
                canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
                x += gridSize
            }
            var y = gridSize.toFloat()
            while (y < height) {
                canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
                y += gridSize
            }
        }

        for (i in 0 until inputs.size) {
            inputs[i].draw(canvas)
        }
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
                    if (pointerInputs[i].onTouch(event)) {
                        return true
                    }
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
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    if (pointerId >= MAX_POINTER_ID) continue
                    val current = pointerButtons[pointerId] ?: continue
                    handled = true
                    if (!settings.slideBetweenButtons) continue
                    val hovered = buttonAt(event.getX(i), event.getY(i))
                    // leaving every button keeps the last one held: the finger may just drift
                    if (hovered == null || hovered === current) continue
                    current.pointerUp(now)
                    pointerButtons[pointerId] = hovered
                    hovered.pointerDown(now)
                    vibrate()
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

    // ---- edit mode touch ----

    private fun updateEditOutlines() {
        for (input in inputs) {
            input.enableDrawingBoundingRect(if (input === editedInput) selectedColor else editOutlineColor)
        }
    }

    private fun cancelEditGesture() {
        removeCallbacks(longPressRunnable)
        editedInput = null
        pinchStartDistance = 0f
        editMoved = false
        editLongPressed = false
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

    private fun snap(value: Int): Int =
        if (settings.snapToGrid && gridSize > 0) (value.toFloat() / gridSize).roundToInt() * gridSize else value

    private fun moveEdited(input: Input, dx: Float, dy: Float) {
        val w = editStartRect.width()
        val h = editStartRect.height()
        val left = snap((editStartRect.left + dx).roundToInt()).coerceIn(0, max(width - w, 0))
        val top = snap((editStartRect.top + dy).roundToInt()).coerceIn(0, max(height - h, 0))
        input.setBounds(left, top, left + w, top + h)
    }

    private fun resizeEdited(input: Input, dx: Float, dy: Float) {
        var newWidth: Int
        var newHeight: Int
        if (input.keepsAspectRatio) {
            val startSum = (editStartRect.width() + editStartRect.height()).toFloat()
            val factor = (startSum + dx + dy) / startSum
            newWidth = (editStartRect.width() * factor).roundToInt()
            newHeight = (editStartRect.height() * factor).roundToInt()
        } else {
            newWidth = (editStartRect.width() + dx).roundToInt()
            newHeight = (editStartRect.height() + dy).roundToInt()
        }
        newWidth = (snap(editStartRect.left + newWidth) - editStartRect.left)
            .clampTo(inputsMinSize, width - editStartRect.left)
        newHeight = (snap(editStartRect.top + newHeight) - editStartRect.top)
            .clampTo(inputsMinSize, height - editStartRect.top)
        input.setBounds(
            editStartRect.left,
            editStartRect.top,
            editStartRect.left + newWidth,
            editStartRect.top + newHeight,
        )
    }

    private fun scaleEdited(input: Input, factor: Float) {
        val newWidth = (editStartRect.width() * factor).roundToInt().clampTo(inputsMinSize, width)
        val newHeight = (editStartRect.height() * factor).roundToInt().clampTo(inputsMinSize, height)
        val left = (editStartRect.exactCenterX() - newWidth * 0.5f).roundToInt().clampTo(0, width - newWidth)
        val top = (editStartRect.exactCenterY() - newHeight * 0.5f).roundToInt().clampTo(0, height - newHeight)
        input.setBounds(left, top, left + newWidth, top + newHeight)
    }

    private fun finishEdit(input: Input) {
        if (!editMoved) return
        val layout = toLayout(input.rect)
        input.layout = layout
        input.macroId?.let { pendingMacroRects[it] = layout }
            ?: input.config?.let { pendingRects[it] = layout }
    }

    private fun onEditTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelEditGesture()
                val input = inputAt(event.x, event.y) ?: return true
                editedInput = input
                editStartRect.set(input.rect)
                editDownX = event.x
                editDownY = event.y
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                updateEditOutlines()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val input = editedInput ?: return true
                if (event.pointerCount == 2 && !editLongPressed) {
                    // second finger: pinch to resize
                    removeCallbacks(longPressRunnable)
                    editStartRect.set(input.rect)
                    pinchStartDistance = max(
                        hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1)),
                        1f,
                    )
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val input = editedInput ?: return true
                if (editLongPressed) return true
                if (pinchStartDistance > 0f && event.pointerCount >= 2) {
                    val distance = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                    editMoved = true
                    scaleEdited(input, distance / pinchStartDistance)
                    return true
                }
                val dx = event.x - editDownX
                val dy = event.y - editDownY
                if (!editMoved && abs(dx) < touchSlop && abs(dy) < touchSlop) return true
                if (!editMoved) {
                    editMoved = true
                    removeCallbacks(longPressRunnable)
                }
                if (inputMode == InputMode.EDIT_SIZE) resizeEdited(input, dx, dy) else moveEdited(input, dx, dy)
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val input = editedInput ?: return true
                if (pinchStartDistance > 0f) {
                    // continue as a drag with the finger left, from where the input is now
                    pinchStartDistance = 0f
                    val remaining = if (event.actionIndex == 0) 1 else 0
                    editStartRect.set(input.rect)
                    editDownX = event.getX(remaining)
                    editDownY = event.getY(remaining)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val input = editedInput
                if (input != null) finishEdit(input)
                cancelEditGesture()
                updateEditOutlines()
                return true
            }
        }
        return false
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val touchEventProcessed = when (inputMode) {
            InputMode.DEFAULT -> onPlayTouch(event)
            InputMode.EDIT_POSITION, InputMode.EDIT_SIZE -> onEditTouch(event)
        }

        if (touchEventProcessed) {
            invalidate()
        }

        return touchEventProcessed
    }

    companion object {
        private const val INPUTS_MIN_SIZE_DP = 20
        private const val GRID_SIZE_DP = 16
        private const val MACRO_WIDTH_DP = 72
        private const val MACRO_HEIGHT_DP = 40
        private const val MAX_POINTER_ID = 32
        private const val MAX_NATIVE_BUTTON_ID = 64
    }
}

/** Like coerceIn, but [max] wins when the range is empty (tiny surfaces, tiny inputs). */
private fun Int.clampTo(min: Int, max: Int): Int = if (max < min) max else coerceIn(min, max)

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

@Composable
fun InputOverlaySurface(
    isVisible: Boolean,
    inputOverlaySettings: InputOverlaySettings,
    inputMode: InputOverlaySurfaceView.InputMode,
    controller: InputOverlayController,
    onEditFinished: (OverlayEditResult) -> Unit,
    onInputLongPress: (OverlayTarget) -> Unit,
    onLegacyLayout: (width: Int, height: Int) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            InputOverlaySurfaceView(context).apply {
                this.controller = controller
                onEditFinishedListener = onEditFinished
                onInputLongPressListener = onInputLongPress
                onLegacyLayoutListener = onLegacyLayout
                setVisible(isVisible)
                setInputMode(inputMode)
                applySettings(inputOverlaySettings)
            }
        },
        update = { view ->
            if (view.controller !== controller) view.controller = controller
            view.onEditFinishedListener = onEditFinished
            view.onInputLongPressListener = onInputLongPress
            view.onLegacyLayoutListener = onLegacyLayout
            view.setVisible(isVisible)
            view.setInputMode(inputMode)
            view.applySettings(inputOverlaySettings)
        }
    )
}
