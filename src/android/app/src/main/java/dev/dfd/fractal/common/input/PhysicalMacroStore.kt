package dev.dfd.fractal.common.input

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private object PhysicalMacroSettingsSerializer : Serializer<PhysicalMacroSettings> {
    // unknown keys are ignored so macros written by a newer build don't reset everything
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue = PhysicalMacroSettings()

    override suspend fun readFrom(input: InputStream): PhysicalMacroSettings = try {
        json.decodeFromString<PhysicalMacroSettings>(input.readBytes().decodeToString())
    } catch (_: Exception) {
        defaultValue
    }

    override suspend fun writeTo(t: PhysicalMacroSettings, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

/**
 * Macros of the physical controllers, in their own file: the settings screen writes them and the
 * emulation (another process in release builds) reads them.
 */
object PhysicalMacroStore {
    @Volatile
    private var store: DataStore<PhysicalMacroSettings>? = null

    fun get(context: Context): DataStore<PhysicalMacroSettings> =
        store ?: synchronized(this) {
            store ?: MultiProcessDataStoreFactory.create(
                serializer = PhysicalMacroSettingsSerializer,
                corruptionHandler = null,
                produceFile = { context.applicationContext.dataStoreFile("physicalMacros.json") },
            ).also { store = it }
        }
}
