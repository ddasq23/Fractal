/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.games.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import dev.dfd.fractal.common.android.context.internalFolder
import dev.dfd.fractal.common.customdrivers.profileDispatcher
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeGameTitles
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game
import dev.dfd.fractal.nativeinterface.NativeGraphicPacks
import kotlinx.coroutines.withContext
import java.io.File

/** What the library shows about a game beyond its name: driver, graphic packs, own profile. */
data class GameStatus(
    val driver: String,
    val activeGraphicPacks: Int,
    val hasCustomProfile: Boolean,
)


// the native side loads the profile into one global (g_currentGameProfile), so reads are serialized

private fun loadStatus(game: Game, userFolder: File): GameStatus {
    val driverSetting = NativeGameTitles.getDriverSettingForTitle(game.titleId)
    val driver = when (driverSetting.mode) {
        NativeGameTitles.DriverSettingMode.SYSTEM -> tr("System driver")
        NativeGameTitles.DriverSettingMode.CUSTOM ->
            driverSetting.customPath?.let { File(it).nameWithoutExtension } ?: tr("Custom driver")

        else -> tr("Global driver")
    }
    val packs = try {
        NativeGraphicPacks.getGraphicPackBasicInfos().count { it.enabled && game.titleId in it.titleIds }
    } catch (_: Exception) {
        0
    }
    // Cemu ships default profiles for many games (data/gameProfiles/default); only a profile the user
    // saved (config/gameProfiles/<title id>.ini) or a per-game driver counts as an own configuration
    val custom = File(userFolder, "gameProfiles/%016x.ini".format(game.titleId)).exists() ||
            driverSetting.mode != NativeGameTitles.DriverSettingMode.GLOBAL
    return GameStatus(driver, packs, custom)
}

@Composable
fun rememberGameStatus(game: Game?): State<GameStatus?> {
    val userFolder = LocalContext.current.internalFolder()
    return produceState<GameStatus?>(null, game?.titleId) {
        value = game?.let { g -> withContext(profileDispatcher) { runCatching { loadStatus(g, userFolder) }.getOrNull() } }
    }
}

fun regionName(region: Int): String = when (region) {
    NativeGameTitles.ConsoleRegion.JPN -> "JPN"
    NativeGameTitles.ConsoleRegion.USA -> "USA"
    NativeGameTitles.ConsoleRegion.EUR -> "EUR"
    NativeGameTitles.ConsoleRegion.CHN -> "CHN"
    NativeGameTitles.ConsoleRegion.KOR -> "KOR"
    NativeGameTitles.ConsoleRegion.TWN -> "TWN"
    else -> if (region == 0) "?" else tr("Multi-region")
}
