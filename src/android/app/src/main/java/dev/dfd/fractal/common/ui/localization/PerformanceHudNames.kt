/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.localization

import dev.dfd.fractal.common.settings.HudAnchor
import dev.dfd.fractal.common.settings.HudItem

/** Names shared by the performance HUD's settings and its block in the in-game menu. */
fun hudItemName(item: HudItem): String = when (item) {
    HudItem.FPS -> tr("FPS")
    HudItem.FRAME_TIME -> tr("Frame time")
    HudItem.FRAME_GEN -> tr("Frame generation")
    HudItem.GPU_NAME -> tr("GPU model")
    HudItem.GPU_USAGE -> tr("GPU usage")
    HudItem.CPU_USAGE -> tr("CPU usage")
    HudItem.CPU_TEMP -> tr("CPU temperature")
    HudItem.RAM -> tr("RAM usage")
    HudItem.EMU_CPU -> tr("Fractal CPU usage")
    HudItem.EMU_RAM -> tr("Fractal RAM usage")
    HudItem.DRAW_CALLS -> tr("Draw calls per frame")
    HudItem.POWER -> tr("Power draw")
    HudItem.BATTERY_LEVEL -> tr("Battery level")
    HudItem.BATTERY_TEMP -> tr("Battery temperature")
    HudItem.CHARGING -> tr("Charging")
}

fun hudItemDescription(item: HudItem): String = when (item) {
    HudItem.FPS -> tr("Frames the game shows per second")
    HudItem.FRAME_TIME -> tr("Average time of a game frame, in milliseconds")
    HudItem.FRAME_GEN -> tr("FG and the frames shown per second, while frame generation adds frames")
    HudItem.GPU_NAME -> tr("The device's graphics chip")
    HudItem.GPU_USAGE -> tr("How busy the GPU is with the game, measured by Fractal itself, so it works on any phone and driver")
    HudItem.CPU_USAGE -> tr("Of the whole device. Where Android hides the load, it is estimated from the clock speeds")
    HudItem.CPU_TEMP -> tr("From a CPU sensor the system lets apps read")
    HudItem.RAM -> tr("Memory in use on the device, in percent")
    HudItem.EMU_CPU -> tr("Share of the whole CPU used by Fractal itself (EMU)")
    HudItem.EMU_RAM -> tr("Memory used by Fractal itself, in MB (EMU)")
    HudItem.DRAW_CALLS -> tr("Draw calls the game makes per frame; in brackets the ones that took the fast path")
    HudItem.POWER -> tr("Watts going in or out of the battery")
    HudItem.BATTERY_LEVEL -> tr("Charge left, in percent")
    HudItem.BATTERY_TEMP -> tr("Read from the system, works on every device")
    HudItem.CHARGING -> tr("CHG while the device is charging")
}

fun hudAnchorName(anchor: HudAnchor): String = when (anchor) {
    HudAnchor.TOP_LEFT -> tr("Top left")
    HudAnchor.TOP_CENTER -> tr("Top center")
    HudAnchor.TOP_RIGHT -> tr("Top right")
    HudAnchor.BOTTOM_LEFT -> tr("Bottom left")
    HudAnchor.BOTTOM_CENTER -> tr("Bottom center")
    HudAnchor.BOTTOM_RIGHT -> tr("Bottom right")
}
