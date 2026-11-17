/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.emulation

import android.content.Context
import android.util.Log
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.games.profile.PackOption
import dev.dfd.fractal.games.profile.PackSetting
import dev.dfd.fractal.games.profile.loadGamePacks
import dev.dfd.fractal.games.profile.resolutionSetting
import dev.dfd.fractal.nativeinterface.NativeEmulation
import dev.dfd.fractal.nativeinterface.NativeGraphicPacks
import dev.dfd.fractal.nativeinterface.NativeSettings
import kotlinx.coroutines.delay

/**
 * Points out, once per game, a lower resolution for a game that runs well below full speed. On lighter GPUs the
 * resolution is what limits most games: on an Adreno 610, 640x360 took Kirby and the Rainbow Curse from 12 to 27 fps,
 * New Super Mario Bros. U from 20 to 34 and Captain Toad from 16 to 30. Measured by the game's frame rate, not by the
 * GPU model, so it fits any slow device, and offered only when the game's graphic packs have a resolution option and
 * the player hasn't picked one.
 */
object ResolutionHint {
    private const val TAG = "ResolutionHint"
    private const val PREFS = "resolution_hint"
    private const val KEY_SHOWN = "shown_titles"

    /** Below this the game is clearly short of speed: 30 fps games run at 30, and 60 fps ones rarely drop this low. */
    private const val SLOW_FPS = 22.0

    /** When to measure, after the game started: menus and the first loading come before gameplay. */
    private val MEASURE_FROM_MS = listOf(60_000L, 150_000L, 300_000L)
    private const val SAMPLES = 6
    private const val SAMPLE_EVERY_MS = 5_000L

    private val Size = Regex("""(\d{3,5})\s*[x×]\s*(\d{3,5})""")

    data class Suggestion(val setting: PackSetting, val option: PackOption, val resolution: String, val fps: Double)

    fun wasShown(context: Context, titleId: Long): Boolean =
        prefs(context).getStringSet(KEY_SHOWN, emptySet())!!.contains("%016x".format(titleId))

    fun markShown(context: Context, titleId: Long) {
        val set = prefs(context).getStringSet(KEY_SHOWN, emptySet())!!.toMutableSet()
        if (set.add("%016x".format(titleId))) prefs(context).edit().putStringSet(KEY_SHOWN, set).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun size(label: String): Pair<Int, Int>? =
        Size.find(label)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

    /** 640x360 when the packs have it, else the smallest option from 360p up that is still below 720p. */
    private fun lowerOption(setting: PackSetting): Pair<PackOption, String>? {
        val sized = setting.options.mapNotNull { option -> size(option.label)?.let { option to it } }
            .filter { (_, s) -> s.second in 360..<720 }
        val (option, s) = sized.firstOrNull { (_, s) -> s == 640 to 360 } ?: sized.minByOrNull { (_, s) -> s.first * s.second }
            ?: return null
        return option to "${s.first}x${s.second}"
    }

    /**
     * Measures the game's frame rate for a while (pauses don't count) and returns what to suggest, or null when the
     * game runs fine, has no resolution option or the player already chose one. [isPaused] tells when to wait.
     */
    suspend fun watch(titleId: Long, isPaused: () -> Boolean): Suggestion? {
        var waited = 0L
        for (from in MEASURE_FROM_MS) {
            delay(from - waited)
            waited = from
            val samples = mutableListOf<Double>()
            while (samples.size < SAMPLES) {
                delay(SAMPLE_EVERY_MS)
                waited += SAMPLE_EVERY_MS
                if (isPaused()) continue
                // 0 while the game shows nothing new (loading): not a frame rate
                NativeEmulation.getFps().takeIf { it > 1.0 }?.let { samples += it }
                if (samples.isEmpty() && waited - from > SAMPLES * SAMPLE_EVERY_MS * 2) break
            }
            if (samples.size < SAMPLES) continue
            val fps = samples.sorted()[samples.size / 2]
            Log.i(TAG, "%016x: median %.1f fps over %d s".format(titleId, fps, SAMPLES * SAMPLE_EVERY_MS / 1000))
            if (fps >= SLOW_FPS) continue
            val setting = runCatching { loadGamePacks(titleId).resolutionSetting() }.getOrNull() ?: return null
            if (setting.selected != null) return null
            val (option, resolution) = lowerOption(setting) ?: return null
            return Suggestion(setting, option, resolution, fps)
        }
        return null
    }

    fun message(suggestion: Suggestion) = tr(
        "This game runs at {0} fps here. At {1} it usually runs close to twice as fast on lighter GPUs, with a softer picture.",
        suggestion.fps.toInt(), suggestion.resolution,
    )

    fun action(suggestion: Suggestion) = tr("Use {0}", suggestion.resolution)

    /**
     * Turns on the suggested resolution for this game, like the game settings do (the other resolution packs off),
     * and saves it. The packs apply when a game starts: it takes effect at the next start.
     */
    fun apply(suggestion: Suggestion): Boolean = try {
        val option = suggestion.option
        suggestion.setting.packIds.filter { it != option.packId }.forEach { id ->
            if (NativeGraphicPacks.getGraphicPack(id)?.isActive() == true) NativeGraphicPacks.setGraphicPackActive(id, false)
        }
        if (option.choice != null) NativeGraphicPacks.setGraphicPackActivePreset(option.packId, option.category, option.choice)
        NativeGraphicPacks.setGraphicPackActive(option.packId, true)
        NativeSettings.saveSettings()
        true
    } catch (e: Exception) {
        Log.w(TAG, "can't set ${suggestion.resolution}", e)
        false
    }
}
