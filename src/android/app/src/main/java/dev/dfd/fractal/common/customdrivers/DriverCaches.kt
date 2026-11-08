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
 * The cache that depends on the GPU driver, per title: the pipeline cache under shaderCache/driver
 * (Vulkan: shaderCache/driver/vk/<title id>.bin). shaderCache/precompiled holds SPIR-V and
 * shaderCache/transferable holds the guest shaders; both are driver independent and never touched.
 */
object DriverCaches {
    private fun root(): File = File(NativeActiveSettings.getUserDataPath()).resolve("shaderCache/driver")

    private fun titleIdOf(file: File): Long? = file.name.take(16).toLongOrNull(16)

    private fun files(): List<File> = root().walkTopDown().filter(File::isFile).toList()

    /** Driver library dir a title actually uses: null = system driver. */
    fun effectiveDriver(setting: NativeGameTitles.DriverSetting, globalDriver: String?): String? =
        when (setting.mode) {
            DriverSettingMode.SYSTEM -> null
            DriverSettingMode.CUSTOM -> setting.customPath
            else -> globalDriver
        }

    /** After a game's own driver changed. Returns the number of files deleted. */
    suspend fun clearForTitle(titleId: Long): Int = withContext(profileDispatcher) {
        files().filter { titleIdOf(it) == titleId }.count { it.delete() }
    }

    /** After the global driver changed: clears the titles that follow the global driver. */
    suspend fun clearForGlobalChange(): Int = withContext(profileDispatcher) {
        files().count { file ->
            val titleId = titleIdOf(file)
            val followsGlobal = titleId == null ||
                runCatching { NativeGameTitles.getDriverSettingForTitle(titleId).mode == DriverSettingMode.GLOBAL }
                    .getOrDefault(true)
            followsGlobal && file.delete()
        }
    }

    /** Sets the global driver (null = system), saves the settings and clears caches if it changed. */
    suspend fun setGlobalDriver(path: String?): Int {
        val old = NativeSettings.getCustomDriverPath()
        if (old == path) return 0
        NativeSettings.setCustomDriverPath(path)
        NativeSettings.saveSettings()
        return clearForGlobalChange()
    }
}
