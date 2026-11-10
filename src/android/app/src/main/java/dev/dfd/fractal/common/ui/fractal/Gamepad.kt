/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.fractal

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Buttons the app's menus use, with what the legend draws for each one. */
enum class PadButton(val keyCode: Int, val label: String) {
    A(KeyEvent.KEYCODE_BUTTON_A, "A"),
    B(KeyEvent.KEYCODE_BUTTON_B, "B"),
    X(KeyEvent.KEYCODE_BUTTON_X, "X"),
    Y(KeyEvent.KEYCODE_BUTTON_Y, "Y"),
    L1(KeyEvent.KEYCODE_BUTTON_L1, "L"),
    R1(KeyEvent.KEYCODE_BUTTON_R1, "R"),
    L2(KeyEvent.KEYCODE_BUTTON_L2, "L2"),
    R2(KeyEvent.KEYCODE_BUTTON_R2, "R2"),
    START(KeyEvent.KEYCODE_BUTTON_START, "START"),
    SELECT(KeyEvent.KEYCODE_BUTTON_SELECT, "SELECT"),

    /** Drawn only; the D-pad and the left stick move the focus. */
    DPAD(KeyEvent.KEYCODE_UNKNOWN, "");

    companion object {
        /** The button a key event is for; keyboards' Menu key counts as Start. */
        fun fromKeyCode(keyCode: Int): PadButton? = when (keyCode) {
            KeyEvent.KEYCODE_MENU -> START
            KeyEvent.KEYCODE_UNKNOWN -> null
            else -> entries.firstOrNull { it.keyCode == keyCode }
        }
    }
}

/**
 * Whether the UI is driven with a gamepad: one is connected, or the last input came from one.
 * The button legend and the automatic focus on screen entry follow it.
 *
 * Events injected with `adb shell input keyevent KEYCODE_BUTTON_*` come from a virtual keyboard,
 * so gamepad buttons count as gamepad input whatever device sent them.
 */
object GamepadUi {
    var connected by mutableStateOf(false)
        private set
    private var lastInputFromGamepad by mutableStateOf(false)

    val active: Boolean get() = connected || lastInputFromGamepad

    private var inputManager: InputManager? = null
    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh()
        override fun onInputDeviceRemoved(deviceId: Int) = refresh()
        override fun onInputDeviceChanged(deviceId: Int) = refresh()
    }

    /** Starts following gamepad connections; call from the UI activity's onStart. */
    fun start(context: Context) {
        val manager = context.getSystemService(InputManager::class.java) ?: return
        inputManager = manager
        manager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        inputManager?.unregisterInputDeviceListener(listener)
        inputManager = null
    }

    private fun refresh() {
        connected = InputDevice.getDeviceIds().any { id -> InputDevice.getDevice(id)?.isGamepad() == true }
    }

    fun onKey(event: KeyEvent) {
        if (KeyEvent.isGamepadButton(event.keyCode) || event.device?.isGamepad() == true) lastInputFromGamepad = true
    }

    fun onMotion(event: MotionEvent) {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)) lastInputFromGamepad = true
    }

    fun onTouch() {
        lastInputFromGamepad = false
    }

    private fun InputDevice.isGamepad(): Boolean =
        !isVirtual && (supportsSource(InputDevice.SOURCE_GAMEPAD) || supportsSource(InputDevice.SOURCE_JOYSTICK))
}
