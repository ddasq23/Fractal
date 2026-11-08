/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.customdrivers

import android.os.Build
import dev.dfd.fractal.common.io.unzip
import dev.dfd.fractal.nativeinterface.NativeActiveSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Where an installed driver came from, saved next to it as source.json by the driver catalog. */
@Serializable
data class DriverSourceInfo(
    val owner: String,
    val repo: String,
    val tag: String,
    val asset: String,
)

const val SOURCE_FILE_NAME = "source.json"

sealed interface DriverInstallResult {
    data class Installed(val driver: Driver) : DriverInstallResult
    data class AlreadyInstalled(val driver: Driver) : DriverInstallResult
    data object Invalid : DriverInstallResult
}

private val lenientJson = Json { ignoreUnknownKeys = true }

private fun readMetadata(file: File): DriverMetadata? = readDriverMetadata(file)

fun readSourceInfo(driverDir: File): DriverSourceInfo? =
    runCatching { lenientJson.decodeFromString<DriverSourceInfo>(driverDir.resolve(SOURCE_FILE_NAME).readText()) }.getOrNull()

/**
 * Installs an adrenotools driver zip (meta.json + library). meta.json may sit at the root of the zip
 * or inside one folder. Returns the already installed driver when the same metadata exists.
 */
suspend fun installDriverZip(input: InputStream, source: DriverSourceInfo? = null): DriverInstallResult =
    withContext(Dispatchers.IO) {
        val tempDir = File(NativeActiveSettings.getUserDataPath()).resolve("driver-" + UUID.randomUUID())
        try {
            tempDir.mkdirs()
            unzip(input, tempDir.path)

            val driverRoot = if (tempDir.resolve(META_FILE_NAME).isFile) tempDir
            else tempDir.listFiles()?.singleOrNull { it.isDirectory && it.resolve(META_FILE_NAME).isFile }
                ?: return@withContext DriverInstallResult.Invalid.also { tempDir.deleteRecursively() }

            val metadata = readMetadata(driverRoot.resolve(META_FILE_NAME))
            if (metadata == null
                || metadata.minApi > Build.VERSION.SDK_INT
                || metadata.schemaVersion != SUPPORTED_SCHEMA_VERSION
                || !driverRoot.resolve(metadata.libraryName).isFile
            ) {
                tempDir.deleteRecursively()
                return@withContext DriverInstallResult.Invalid
            }

            parseInstalledDrivers().firstOrNull { it.metadata == metadata }?.let { existing ->
                tempDir.deleteRecursively()
                // remember where it came from, so the catalog shows it as installed
                if (source != null && readSourceInfo(File(existing.path)) == null)
                    File(existing.path).resolve(SOURCE_FILE_NAME).writeText(Json.encodeToString(source))
                return@withContext DriverInstallResult.AlreadyInstalled(existing)
            }

            if (source != null) driverRoot.resolve(SOURCE_FILE_NAME).writeText(Json.encodeToString(source))
            val driversDir = getCustomDriversDir().toFile().apply { mkdirs() }
            val target = driversDir.resolve(tempDir.name)
            if (!driverRoot.renameTo(target)) {
                driverRoot.copyRecursively(target, overwrite = true)
            }
            tempDir.deleteRecursively()
            DriverInstallResult.Installed(Driver(target.path, metadata))
        } catch (_: Exception) {
            tempDir.deleteRecursively()
            DriverInstallResult.Invalid
        }
    }
