package dev.dfd.fractal.settings.input.controller.emulatedcontroller

import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeInput.ClassicButton
import dev.dfd.fractal.nativeinterface.NativeInput.ProButton
import dev.dfd.fractal.nativeinterface.NativeInput.VPADButton
import dev.dfd.fractal.nativeinterface.NativeInput.WiimoteButton
import dev.dfd.fractal.settings.input.controller.ControllerStyle

/** Inputs of an emulated controller shown together, with their display names. */
data class InputGroup(val name: String, val inputs: List<Pair<Int, String>>)

private fun stickInputs(up: Int, down: Int, left: Int, right: Int, click: Int? = null) = buildList {
    if (click != null) add(click to tr("click"))
    add(up to tr("up"))
    add(down to tr("down"))
    add(left to tr("left"))
    add(right to tr("right"))
}

/** Mappable inputs of a controller drawn as [style], in the order "Map all, one by one" asks for them. */
fun inputGroupsFor(style: ControllerStyle, nunchuk: Boolean = false): List<InputGroup> = when (style) {
    ControllerStyle.GAMEPAD -> listOf(
        InputGroup(
            tr("Buttons"), listOf(
                VPADButton.A to "A", VPADButton.B to "B", VPADButton.X to "X", VPADButton.Y to "Y",
                VPADButton.L to "L", VPADButton.R to "R", VPADButton.ZL to "ZL", VPADButton.ZR to "ZR",
                VPADButton.PLUS to "+", VPADButton.MINUS to "−",
            )
        ),
        InputGroup(tr("D-pad"), stickInputs(VPADButton.UP, VPADButton.DOWN, VPADButton.LEFT, VPADButton.RIGHT)),
        InputGroup(tr("Left stick"), stickInputs(VPADButton.STICKL_UP, VPADButton.STICKL_DOWN, VPADButton.STICKL_LEFT, VPADButton.STICKL_RIGHT, VPADButton.STICKL)),
        InputGroup(tr("Right stick"), stickInputs(VPADButton.STICKR_UP, VPADButton.STICKR_DOWN, VPADButton.STICKR_LEFT, VPADButton.STICKR_RIGHT, VPADButton.STICKR)),
        InputGroup(
            tr("Extra"), listOf(
                VPADButton.HOME to tr("home"), VPADButton.MIC to tr("blow mic"), VPADButton.SCREEN to tr("show screen"),
            )
        ),
    )

    ControllerStyle.PRO -> listOf(
        InputGroup(
            tr("Buttons"), listOf(
                ProButton.A to "A", ProButton.B to "B", ProButton.X to "X", ProButton.Y to "Y",
                ProButton.L to "L", ProButton.R to "R", ProButton.ZL to "ZL", ProButton.ZR to "ZR",
                ProButton.PLUS to "+", ProButton.MINUS to "−", ProButton.HOME to tr("home"),
            )
        ),
        InputGroup(tr("D-pad"), stickInputs(ProButton.UP, ProButton.DOWN, ProButton.LEFT, ProButton.RIGHT)),
        InputGroup(tr("Left stick"), stickInputs(ProButton.STICKL_UP, ProButton.STICKL_DOWN, ProButton.STICKL_LEFT, ProButton.STICKL_RIGHT, ProButton.STICKL)),
        InputGroup(tr("Right stick"), stickInputs(ProButton.STICKR_UP, ProButton.STICKR_DOWN, ProButton.STICKR_LEFT, ProButton.STICKR_RIGHT, ProButton.STICKR)),
    )

    ControllerStyle.CLASSIC -> listOf(
        InputGroup(
            tr("Buttons"), listOf(
                ClassicButton.A to "A", ClassicButton.B to "B", ClassicButton.X to "X", ClassicButton.Y to "Y",
                ClassicButton.L to "L", ClassicButton.R to "R", ClassicButton.ZL to "ZL", ClassicButton.ZR to "ZR",
                ClassicButton.PLUS to "+", ClassicButton.MINUS to "−", ClassicButton.HOME to tr("home"),
            )
        ),
        InputGroup(tr("D-pad"), stickInputs(ClassicButton.UP, ClassicButton.DOWN, ClassicButton.LEFT, ClassicButton.RIGHT)),
        InputGroup(tr("Left stick"), stickInputs(ClassicButton.STICKL_UP, ClassicButton.STICKL_DOWN, ClassicButton.STICKL_LEFT, ClassicButton.STICKL_RIGHT)),
        InputGroup(tr("Right stick"), stickInputs(ClassicButton.STICKR_UP, ClassicButton.STICKR_DOWN, ClassicButton.STICKR_LEFT, ClassicButton.STICKR_RIGHT)),
    )

    ControllerStyle.GAMECUBE -> listOf(
        InputGroup(
            tr("Buttons"), listOf(
                ClassicButton.A to "A", ClassicButton.B to "B", ClassicButton.X to "X", ClassicButton.Y to "Y",
                ClassicButton.ZR to "Z", ClassicButton.L to "L", ClassicButton.R to "R", ClassicButton.PLUS to "Start",
            )
        ),
        InputGroup(tr("D-pad"), stickInputs(ClassicButton.UP, ClassicButton.DOWN, ClassicButton.LEFT, ClassicButton.RIGHT)),
        InputGroup(tr("Control stick"), stickInputs(ClassicButton.STICKL_UP, ClassicButton.STICKL_DOWN, ClassicButton.STICKL_LEFT, ClassicButton.STICKL_RIGHT)),
        InputGroup(tr("C stick"), stickInputs(ClassicButton.STICKR_UP, ClassicButton.STICKR_DOWN, ClassicButton.STICKR_LEFT, ClassicButton.STICKR_RIGHT)),
        InputGroup(
            tr("Classic Controller only"), listOf(
                ClassicButton.ZL to "ZL", ClassicButton.MINUS to "−", ClassicButton.HOME to tr("home"),
            )
        ),
    )

    ControllerStyle.WIIMOTE -> buildList {
        add(
            InputGroup(
                tr("Buttons"), listOf(
                    WiimoteButton.A to "A", WiimoteButton.B to "B", WiimoteButton.ONE to "1", WiimoteButton.TWO to "2",
                    WiimoteButton.PLUS to "+", WiimoteButton.MINUS to "−", WiimoteButton.HOME to tr("home"),
                )
            )
        )
        add(InputGroup(tr("D-pad"), stickInputs(WiimoteButton.UP, WiimoteButton.DOWN, WiimoteButton.LEFT, WiimoteButton.RIGHT)))
        if (nunchuk) {
            add(
                InputGroup(
                    tr("Nunchuk"),
                    listOf(WiimoteButton.NUNCHUCK_C to "C", WiimoteButton.NUNCHUCK_Z to "Z") +
                            stickInputs(WiimoteButton.NUNCHUCK_UP, WiimoteButton.NUNCHUCK_DOWN, WiimoteButton.NUNCHUCK_LEFT, WiimoteButton.NUNCHUCK_RIGHT),
                )
            )
        }
    }
}

/** "Left stick: up" style name of [id], or null when [style] has no such input. */
fun inputFullName(style: ControllerStyle, nunchuk: Boolean, id: Int): String? {
    for (group in inputGroupsFor(style, nunchuk)) {
        val name = group.inputs.firstOrNull { it.first == id }?.second ?: continue
        return if (group.name == tr("Buttons")) name else "${group.name}: $name"
    }
    return null
}
