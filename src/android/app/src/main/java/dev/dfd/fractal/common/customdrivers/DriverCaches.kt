/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.customdrivers

import dev.dfd.fractal.nativeinterface.NativeActiveSettings
import dev.dfd.fractal.nativeinterface.NativeGameTitles
import dev.dfd.fractal.nativeinterface.NativeGameTitles.DriverSettingMode
import dev.dfd.fractal.nativeinterface.NativeSettings
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The cache that depends on the GPU driver, per title: the pipeline cache under shaderCache/driver.
 * Vulkan keeps one folder per driver (shaderCache/driver/vk/<pipeline cache UUID>_<driver version>/<title id>.bin),
 * so switching drivers keeps every driver's cache and needs no clearing. It is only cleared after a driver
 * failed to start a game, in case the cache was the cause. shaderCache/precompiled holds SPIR-V and
 * shaderCache/transferable holds the guest shaders; both are driver independent and never touched.
 */
object DriverCaches {
    private fun root(): File = File(NativeActiveSettings.getUserDataPath()).resolve("shaderCache/driver")

    private fun titleIdOf(file: File): Long? = file.name.take(16).toLongOrNull(16)

    private fun files(): List<File> = root().walkTopDown().filter(File::isFile).toList()

    /** After a driver failed to start this game. Returns the number of files deleted. */
    suspend fun clearForTitle(titleId: Long): Int = withContext(profileDispatcher) {
        files().filter { titleIdOf(it) == titleId }.count { it.delete() }
    }

    /** After the global driver failed to start a game: clears the titles that follow the global driver. */
    suspend fun clearForGlobalChange(): Int = withContext(profileDispatcher) {
        files().count { file ->
            val titleId = titleIdOf(file)
            val followsGlobal = titleId == null ||
                runCatching { NativeGameTitles.getDriverSettingForTitle(titleId).mode == DriverSettingMode.GLOBAL }
                    .getOrDefault(true)
            followsGlobal && file.delete()
        }
    }

    /** Sets the global driver (null = system) and saves the settings. Caches are per driver, so nothing is cleared. */
    suspend fun setGlobalDriver(path: String?) = withContext(profileDispatcher) {
        if (NativeSettings.getCustomDriverPath() != path) {
            NativeSettings.setCustomDriverPath(path)
            NativeSettings.saveSettings()
        }
    }
}
