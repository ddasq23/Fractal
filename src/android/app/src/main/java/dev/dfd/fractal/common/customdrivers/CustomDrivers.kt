package dev.dfd.fractal.common.customdrivers

import kotlinx.serialization.json.Json
import dev.dfd.fractal.nativeinterface.NativeActiveSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

const val META_FILE_NAME = "meta.json"
const val SUPPORTED_SCHEMA_VERSION = 1
const val CUSTOM_DRIVERS_DIR_NAME = "customDrivers"

fun getCustomDriversDir(): Path =
    Path(NativeActiveSettings.getUserDataPath()).resolve(CUSTOM_DRIVERS_DIR_NAME)

@Serializable
data class DriverMetadata(
    val schemaVersion: Int,
    val name: String,
    val description: String,
    val author: String,
    val packageVersion: String,
    val vendor: String,
    val driverVersion: String,
    val minApi: Int,
    val libraryName: String,
)

// lenient: some packages add their own fields to meta.json
private val metadataJson = Json { ignoreUnknownKeys = true }

fun readDriverMetadata(file: File): DriverMetadata? =
    runCatching { metadataJson.decodeFromString<DriverMetadata>(file.readText()) }.getOrNull()

data class Driver(
    val path: String,
    val metadata: DriverMetadata,
)

suspend fun parseInstalledDrivers(): List<Driver> {
    return withContext(Dispatchers.IO) {
        val customDriversDir = getCustomDriversDir()

        if (!customDriversDir.isDirectory())
            return@withContext emptyList()

        val driverDirs: Array<File> =
            customDriversDir.toFile().listFiles() ?: return@withContext emptyList()

        val drivers = mutableListOf<Driver>()

        for (driverDir in driverDirs) {
            if (!driverDir.isDirectory)
                continue
            val metadata = readDriverMetadata(driverDir.resolve(META_FILE_NAME)) ?: continue
            val driver = Driver(
                path = driverDir.path,
                metadata = metadata,
            )
            drivers.add(driver)
        }

        drivers.sortBy { it.metadata.name }

        return@withContext drivers
    }
}