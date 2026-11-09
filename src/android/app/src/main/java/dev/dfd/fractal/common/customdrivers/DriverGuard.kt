/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.customdrivers

import android.content.Context
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeGameTitles
import dev.dfd.fractal.nativeinterface.NativeGameTitles.DriverSettingMode
import dev.dfd.fractal.nativeinterface.NativeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Goes back to the system driver when a custom driver failed. The native side (VulkanAPI.cpp) writes
 * two markers in the app data dir, each holding the driver dir, "game" or "global", and the title id:
 * - custom_driver_pending: written before loading the driver, removed after the first presented frame.
 *   Still there in a new process means starting a game with that driver crashed or never showed a frame.
 * - custom_driver_failed: the driver could not be loaded and the game ran on the system driver.
 */
object DriverGuard {
    private const val PENDING = "custom_driver_pending"
    private const val FAILED = "custom_driver_failed"

    /**
     * The pending marker only means a crash when no game is running. Release builds run the game in its
     * own process (":EmulationProcess"), debug builds in the app process, where the library can't be
     * resumed while a game runs.
     */
    private fun gameRunning(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
        return am.runningAppProcesses.orEmpty().any { it.processName.endsWith(":EmulationProcess") }
    }

    fun loadFailed(context: Context): Boolean = File(context.dataDir, FAILED).isFile

    /**
     * Reads the markers, switches the setting that chose the failed driver (the game's profile or the
     * global driver) back to the system driver and returns a message for the user, or null.
     */
    suspend fun recover(context: Context): String? = withContext(Dispatchers.IO) {
        val failed = File(context.dataDir, FAILED)
        val pending = File(context.dataDir, PENDING)
        // the library can come back a moment before the system drops the crashed game process
        var checkPending = false
        if (pending.isFile) for (attempt in 0 until 4) {
            if (!gameRunning(context)) { checkPending = true; break }
            delay(750)
        }
        val (marker, crashed) = when {
            failed.isFile -> failed to false
            checkPending -> pending to true
            else -> return@withContext null
        }
        val lines = runCatching { marker.readLines() }.getOrDefault(emptyList())
        failed.delete()
        pending.delete()

        val driverPath = lines.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@withContext null
        val fromGame = lines.getOrNull(1) == "game"
        val titleId = lines.getOrNull(2)?.toULongOrNull(16)?.toLong()
        val reverted = when {
            fromGame && titleId != null -> {
                val current = withContext(profileDispatcher) { NativeGameTitles.getDriverSettingForTitle(titleId) }
                if (current.mode == DriverSettingMode.CUSTOM && current.customPath == driverPath) {
                    withContext(profileDispatcher) {
                        NativeGameTitles.setDriverSettingForTitle(
                            titleId, NativeGameTitles.DriverSetting(DriverSettingMode.SYSTEM),
                        )
                    }
                    DriverCaches.clearForTitle(titleId)
                    true
                } else false
            }
            NativeSettings.getCustomDriverPath() == driverPath -> {
                DriverCaches.setGlobalDriver(null)
                DriverCaches.clearForGlobalChange()
                true
            }
            else -> false // the user already picked another driver
        }
        if (!reverted) return@withContext null

        val name = readDriverMetadata(File(driverPath, META_FILE_NAME))?.name ?: File(driverPath).name
        val what = if (crashed) tr("The game closed or showed no image while starting with the driver {0}.", name)
        else tr("The driver {0} could not be loaded, so the game used the system driver.", name)
        val where = if (fromGame) tr("That game's profile is back on the system driver.")
        else tr("Fractal is back on the system driver.")
        "$what $where"
    }
}
