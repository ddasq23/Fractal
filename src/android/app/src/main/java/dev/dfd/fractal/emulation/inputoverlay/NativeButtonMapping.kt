package dev.dfd.fractal.emulation.inputoverlay

import dev.dfd.fractal.common.settings.OverlayControllerType
import dev.dfd.fractal.common.settings.OverlayInputConfig
import dev.dfd.fractal.common.settings.OverlayMacroInput
import dev.dfd.fractal.nativeinterface.NativeInput

/** Native mapping id of [button] on a controller of [type], or -1 when it has none. */
fun nativeButtonFor(type: OverlayControllerType?, button: OverlayInput): Int = when (type) {
    OverlayControllerType.VPAD -> overlayButtonToVPADButton(button)
    OverlayControllerType.PRO -> overlayButtonToProButton(button)
    OverlayControllerType.CLASSIC -> overlayButtonToClassicButton(button)
    OverlayControllerType.WIIMOTE -> overlayButtonToWiimoteButton(button)
    null -> -1
}

fun OverlayMacroInput.toOverlayInput(): OverlayInput = when (this) {
    OverlayMacroInput.A -> OverlayButton.A
    OverlayMacroInput.B -> OverlayButton.B
    OverlayMacroInput.X -> OverlayButton.X
    OverlayMacroInput.Y -> OverlayButton.Y
    OverlayMacroInput.ONE -> OverlayButton.ONE
    OverlayMacroInput.TWO -> OverlayButton.TWO
    OverlayMacroInput.C -> OverlayButton.C
    OverlayMacroInput.Z -> OverlayButton.Z
    OverlayMacroInput.HOME -> OverlayButton.HOME
    OverlayMacroInput.L -> OverlayButton.L
    OverlayMacroInput.R -> OverlayButton.R
    OverlayMacroInput.ZL -> OverlayButton.ZL
    OverlayMacroInput.ZR -> OverlayButton.ZR
    OverlayMacroInput.PLUS -> OverlayButton.PLUS
    OverlayMacroInput.MINUS -> OverlayButton.MINUS
    OverlayMacroInput.L_STICK_CLICK -> OverlayButton.L_STICK_CLICK
    OverlayMacroInput.R_STICK_CLICK -> OverlayButton.R_STICK_CLICK
    OverlayMacroInput.DPAD_UP -> OverlayDpad.DPAD_UP
    OverlayMacroInput.DPAD_DOWN -> OverlayDpad.DPAD_DOWN
    OverlayMacroInput.DPAD_LEFT -> OverlayDpad.DPAD_LEFT
    OverlayMacroInput.DPAD_RIGHT -> OverlayDpad.DPAD_RIGHT
    OverlayMacroInput.BLOW_MIC -> OverlayButton.BLOW_MIC
}

/** Buttons a macro can use on a controller of [type]. */
fun macroInputsFor(type: OverlayControllerType): List<OverlayMacroInput> =
    OverlayMacroInput.entries.filter { nativeButtonFor(type, it.toOverlayInput()) != -1 }

/** Short name shown on chips and macro labels. */
fun OverlayMacroInput.displayName(): String = when (this) {
    OverlayMacroInput.ONE -> "1"
    OverlayMacroInput.TWO -> "2"
    OverlayMacroInput.HOME -> "HOME"
    OverlayMacroInput.PLUS -> "+"
    OverlayMacroInput.MINUS -> "-"
    OverlayMacroInput.L_STICK_CLICK -> "L3"
    OverlayMacroInput.R_STICK_CLICK -> "R3"
    OverlayMacroInput.DPAD_UP -> "↑"
    OverlayMacroInput.DPAD_DOWN -> "↓"
    OverlayMacroInput.DPAD_LEFT -> "←"
    OverlayMacroInput.DPAD_RIGHT -> "→"
    OverlayMacroInput.BLOW_MIC -> "MIC"
    else -> name
}

private fun overlayButtonToVPADButton(button: OverlayInput): Int = when (button) {
    OverlayButton.A -> NativeInput.VPADButton.A
    OverlayButton.B -> NativeInput.VPADButton.B
    OverlayButton.X -> NativeInput.VPADButton.X
    OverlayButton.Y -> NativeInput.VPADButton.Y
    OverlayButton.PLUS -> NativeInput.VPADButton.PLUS
    OverlayButton.MINUS -> NativeInput.VPADButton.MINUS
    OverlayDpad.DPAD_UP -> NativeInput.VPADButton.UP
    OverlayDpad.DPAD_DOWN -> NativeInput.VPADButton.DOWN
    OverlayDpad.DPAD_LEFT -> NativeInput.VPADButton.LEFT
    OverlayDpad.DPAD_RIGHT -> NativeInput.VPADButton.RIGHT
    OverlayButton.L_STICK_CLICK -> NativeInput.VPADButton.STICKL
    OverlayButton.R_STICK_CLICK -> NativeInput.VPADButton.STICKR
    OverlayButton.L -> NativeInput.VPADButton.L
    OverlayButton.R -> NativeInput.VPADButton.R
    OverlayButton.ZR -> NativeInput.VPADButton.ZR
    OverlayButton.ZL -> NativeInput.VPADButton.ZL
    OverlayButton.BLOW_MIC -> NativeInput.VPADButton.MIC
    else -> -1
}

private fun overlayButtonToClassicButton(button: OverlayInput): Int = when (button) {
    OverlayButton.A -> NativeInput.ClassicButton.A
    OverlayButton.B -> NativeInput.ClassicButton.B
    OverlayButton.X -> NativeInput.ClassicButton.X
    OverlayButton.Y -> NativeInput.ClassicButton.Y
    OverlayButton.PLUS -> NativeInput.ClassicButton.PLUS
    OverlayButton.MINUS -> NativeInput.ClassicButton.MINUS
    OverlayDpad.DPAD_UP -> NativeInput.ClassicButton.UP
    OverlayDpad.DPAD_DOWN -> NativeInput.ClassicButton.DOWN
    OverlayDpad.DPAD_LEFT -> NativeInput.ClassicButton.LEFT
    OverlayDpad.DPAD_RIGHT -> NativeInput.ClassicButton.RIGHT
    OverlayButton.L -> NativeInput.ClassicButton.L
    OverlayButton.R -> NativeInput.ClassicButton.R
    OverlayButton.ZR -> NativeInput.ClassicButton.ZR
    OverlayButton.ZL -> NativeInput.ClassicButton.ZL
    else -> -1
}

private fun overlayButtonToProButton(button: OverlayInput): Int = when (button) {
    OverlayButton.A -> NativeInput.ProButton.A
    OverlayButton.B -> NativeInput.ProButton.B
    OverlayButton.X -> NativeInput.ProButton.X
    OverlayButton.Y -> NativeInput.ProButton.Y
    OverlayButton.PLUS -> NativeInput.ProButton.PLUS
    OverlayButton.MINUS -> NativeInput.ProButton.MINUS
    OverlayDpad.DPAD_UP -> NativeInput.ProButton.UP
    OverlayDpad.DPAD_DOWN -> NativeInput.ProButton.DOWN
    OverlayDpad.DPAD_LEFT -> NativeInput.ProButton.LEFT
    OverlayDpad.DPAD_RIGHT -> NativeInput.ProButton.RIGHT
    OverlayButton.L_STICK_CLICK -> NativeInput.ProButton.STICKL
    OverlayButton.R_STICK_CLICK -> NativeInput.ProButton.STICKR
    OverlayButton.L -> NativeInput.ProButton.L
    OverlayButton.R -> NativeInput.ProButton.R
    OverlayButton.ZR -> NativeInput.ProButton.ZR
    OverlayButton.ZL -> NativeInput.ProButton.ZL
    else -> -1
}

private fun overlayButtonToWiimoteButton(button: OverlayInput): Int = when (button) {
    OverlayButton.A -> NativeInput.WiimoteButton.A
    OverlayButton.B -> NativeInput.WiimoteButton.B
    OverlayButton.ONE -> NativeInput.WiimoteButton.ONE
    OverlayButton.TWO -> NativeInput.WiimoteButton.TWO
    OverlayButton.PLUS -> NativeInput.WiimoteButton.PLUS
    OverlayButton.MINUS -> NativeInput.WiimoteButton.MINUS
    OverlayButton.HOME -> NativeInput.WiimoteButton.HOME
    OverlayDpad.DPAD_UP -> NativeInput.WiimoteButton.UP
    OverlayDpad.DPAD_DOWN -> NativeInput.WiimoteButton.DOWN
    OverlayDpad.DPAD_LEFT -> NativeInput.WiimoteButton.LEFT
    OverlayDpad.DPAD_RIGHT -> NativeInput.WiimoteButton.RIGHT
    OverlayButton.C -> NativeInput.WiimoteButton.NUNCHUCK_C
    OverlayButton.Z -> NativeInput.WiimoteButton.NUNCHUCK_Z
    else -> -1
}
