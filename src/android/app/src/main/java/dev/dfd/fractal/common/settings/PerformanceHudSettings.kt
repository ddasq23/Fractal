/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * What the performance HUD can show. [bit] is the item's flag in [PerformanceHudSettings.items]; the values are the
 * ones of Winlator-Ludashi's HUD, where it comes from.
 */
enum class HudItem(val bit: Int) {
    FPS(1),
    GPU_NAME(1 shl 7),
    GPU_USAGE(1 shl 1),
    CPU_USAGE(1 shl 2),
    CPU_TEMP(1 shl 8),
    RAM(1 shl 6),
    POWER(1 shl 3),
    BATTERY_LEVEL(1 shl 11),
    BATTERY_TEMP(1 shl 9),
    CHARGING(1 shl 10),
    ;

    companion object {
        val ALL_MASK: Int = entries.fold(0) { mask, item -> mask or item.bit }

        /** Everything but the battery level, which the system bar already shows. */
        val DEFAULT_MASK: Int = ALL_MASK and BATTERY_LEVEL.bit.inv()
    }
}

/** Places the settings offer for the HUD; dragging it over the game puts it anywhere else. */
enum class HudAnchor(val x: Float, val y: Float) {
    TOP_LEFT(0f, 0f),
    TOP_CENTER(0.5f, 0f),
    TOP_RIGHT(1f, 0f),
    BOTTOM_LEFT(0f, 1f),
    BOTTOM_CENTER(0.5f, 1f),
    BOTTOM_RIGHT(1f, 1f),
}

/**
 * The performance HUD drawn over the game (CPU, GPU, RAM, temperatures, power, FPS). Global: the settings screen and
 * the in-game menu change the same values.
 */
@Serializable
data class PerformanceHudSettings(
    val enabled: Boolean = false,
    /** [HudItem] bits of the items shown. */
    val items: Int = HudItem.DEFAULT_MASK,
    val sizePercent: Int = 100,
    /** Opacity of the HUD's background; the values stay readable at any setting. */
    val opacityPercent: Int = 85,
    /** One item per line instead of a single row. */
    val vertical: Boolean = false,
    /**
     * Where the HUD sits, as a fraction of the room the screen leaves around it: 0 is the left (top) edge and 1 the
     * right (bottom) one. Kept as fractions so a HUD put in a corner stays there on any screen, size and orientation.
     */
    val x: Float = DEFAULT_ANCHOR.x,
    val y: Float = DEFAULT_ANCHOR.y,
) {
    fun shows(item: HudItem): Boolean = items and item.bit != 0

    fun withItem(item: HudItem, shown: Boolean): PerformanceHudSettings =
        copy(items = if (shown) items or item.bit else items and item.bit.inv())

    /** The settings' place the HUD is at, or null after it was dragged somewhere else. */
    val anchor: HudAnchor? get() = HudAnchor.entries.firstOrNull { it.x == x && it.y == y }

    fun at(anchor: HudAnchor): PerformanceHudSettings = copy(x = anchor.x, y = anchor.y)

    /** Values from an older or newer build, or a hand edited file, brought back in range. */
    fun sanitized(): PerformanceHudSettings {
        val clean = copy(
            items = items and HudItem.ALL_MASK,
            sizePercent = sizePercent.coerceIn(SIZE_MIN, SIZE_MAX),
            opacityPercent = opacityPercent.coerceIn(OPACITY_MIN, OPACITY_MAX),
            x = if (x.isNaN()) DEFAULT_ANCHOR.x else x.coerceIn(0f, 1f),
            y = if (y.isNaN()) DEFAULT_ANCHOR.y else y.coerceIn(0f, 1f),
        )
        return if (clean == this) this else clean
    }

    companion object {
        const val SIZE_MIN = 50
        const val SIZE_MAX = 200
        const val OPACITY_MIN = 0
        const val OPACITY_MAX = 100

        /** Top center: on-screen control layouts keep their shoulder buttons in the top corners. */
        val DEFAULT_ANCHOR = HudAnchor.TOP_CENTER
    }
}

/** The HUD settings as they change, from the app settings. */
fun performanceHudSettings(): Flow<PerformanceHudSettings> =
    AppSettingsStore.dataStore.data.map { it.performanceHud.sanitized() }.distinctUntilChanged()

/** Changes the HUD settings and saves them. */
suspend fun updatePerformanceHud(transform: (PerformanceHudSettings) -> PerformanceHudSettings) {
    AppSettingsStore.dataStore.updateData { settings ->
        val updated = transform(settings.performanceHud).sanitized()
        if (updated == settings.performanceHud) settings else settings.copy(performanceHud = updated)
    }
}
