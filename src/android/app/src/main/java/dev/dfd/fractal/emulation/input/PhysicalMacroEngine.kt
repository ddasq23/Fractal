package dev.dfd.fractal.emulation.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.dfd.fractal.common.android.inputevent.isFromPhysicalController
import dev.dfd.fractal.common.input.InputDeviceListener
import dev.dfd.fractal.common.input.MacroPlayback
import dev.dfd.fractal.common.input.PhysicalMacro
import dev.dfd.fractal.common.input.analogVirtualKeys
import dev.dfd.fractal.common.input.macroButtonToNative
import dev.dfd.fractal.nativeinterface.NativeInput

/**
 * Runs the physical controller macros while a game is on screen: watches the trigger buttons,
 * plays each macro with [MacroPlayback] and presses the emulated buttons the way the on-screen
 * controls do ([NativeInput.onOverlayButton]). Everything runs on the main thread.
 */
object PhysicalMacroEngine {
    private const val TICK_MS = 8L

    private val handler = Handler(Looper.getMainLooper())
    private var playbacks: List<MacroPlayback> = emptyList()
    private val keysByDevice = HashMap<String, MutableSet<Int>>()
    private val analogKeysByDevice = HashMap<String, Set<Int>>()

    /** Emulated buttons this engine holds, as controllerIndex shl 32 or mapping id. */
    private var pressed: Set<Long> = emptySet()

    private val tick = Runnable { apply(SystemClock.uptimeMillis()) }

    fun setMacros(macros: List<PhysicalMacro>) {
        releaseAll()
        playbacks = macros.filter { it.isUsable }.map(::MacroPlayback)
    }

    /** Returns true when the key is a macro's own trigger and must not reach the game. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (playbacks.isEmpty() || !event.isFromPhysicalController()) return false
        val descriptor = event.device?.descriptor ?: return false
        val keyCode = event.keyCode
        val takesKey = playbacks.any { it.macro.triggerMatches(descriptor, keyCode) }
        if (event.repeatCount > 0) return takesKey

        val keys = keysByDevice.getOrPut(descriptor) { mutableSetOf() }
        val changed = when (event.action) {
            KeyEvent.ACTION_DOWN -> keys.add(keyCode)
            KeyEvent.ACTION_UP -> keys.remove(keyCode)
            else -> false
        }
        if (changed) update()
        return takesKey
    }

    /** Analog triggers and the hat can be part of a trigger; the event still goes to the game. */
    fun onMotionEvent(event: MotionEvent) {
        if (playbacks.isEmpty() || !event.isFromPhysicalController()) return
        val descriptor = event.device?.descriptor ?: return
        val keys = analogVirtualKeys(
            leftTrigger = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)),
            rightTrigger = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)),
            hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X),
            hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y),
        )
        if (analogKeysByDevice[descriptor].orEmpty() != keys) {
            analogKeysByDevice[descriptor] = keys
            update()
        }
    }

    /** Lets go of every macro and held key: pause, menus, focus loss. */
    fun releaseAll() {
        playbacks.forEach { it.release() }
        keysByDevice.clear()
        analogKeysByDevice.clear()
        apply(SystemClock.uptimeMillis())
    }

    private fun onDevicesChanged() {
        val connected = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it)?.descriptor }.toSet()
        val gone = (keysByDevice.keys + analogKeysByDevice.keys).filter { it !in connected }
        if (gone.isEmpty()) return
        gone.forEach {
            keysByDevice.remove(it)
            analogKeysByDevice.remove(it)
        }
        update()
    }

    private val deviceListener = object : InputDeviceListener {
        override fun onInputDeviceChanged() = onDevicesChanged()
    }

    fun register(context: Context) {
        (context.getSystemService(Context.INPUT_SERVICE) as InputManager?)?.registerInputDeviceListener(deviceListener, handler)
    }

    fun unregister(context: Context) {
        (context.getSystemService(Context.INPUT_SERVICE) as InputManager?)?.unregisterInputDeviceListener(deviceListener)
        releaseAll()
    }

    private fun update() {
        val now = SystemClock.uptimeMillis()
        val held = HashMap<String, Set<Int>>()
        for (descriptor in keysByDevice.keys + analogKeysByDevice.keys) {
            held[descriptor] = keysByDevice[descriptor].orEmpty() + analogKeysByDevice[descriptor].orEmpty()
        }
        playbacks.forEach { it.onTrigger(it.macro.isTriggerHeld(held), now) }
        apply(now)
    }

    private fun apply(nowMs: Long) {
        handler.removeCallbacks(tick)
        val wanted = HashSet<Long>()
        for (playback in playbacks) {
            val buttons = playback.buttonsAt(nowMs)
            if (buttons.isEmpty()) continue
            val controllerIndex = playback.macro.controllerIndex
            val type = NativeInput.getControllerType(controllerIndex)
            for (button in buttons) {
                val id = macroButtonToNative(type, button)
                if (id >= 0) wanted += (controllerIndex.toLong() shl 32) or id.toLong()
            }
        }
        for (key in pressed) {
            if (key !in wanted) NativeInput.onOverlayButton((key ushr 32).toInt(), key.toInt(), false)
        }
        for (key in wanted) {
            if (key !in pressed) NativeInput.onOverlayButton((key ushr 32).toInt(), key.toInt(), true)
        }
        pressed = wanted
        if (playbacks.any { it.needsTicks }) handler.postDelayed(tick, TICK_MS)
    }
}
