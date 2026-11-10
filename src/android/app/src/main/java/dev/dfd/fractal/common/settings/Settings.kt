package dev.dfd.fractal.common.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import dev.dfd.fractal.common.ui.localization.DEFAULT_LANGUAGE
import dev.dfd.fractal.common.ui.theme.FractalThemeId
import dev.dfd.fractal.common.ui.theme.WallpaperId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class EmulationSettings(
    val gamePadPosition: GamePadPosition = GamePadPosition.RIGHT,
)

@Serializable
enum class LibraryView {
    GRID,
    CAROUSEL,
    LIST,
}

/** Library order; favorites always come first. */
@Serializable
enum class LibrarySort {
    NAME,
    LAST_PLAYED,
    PLAY_TIME,
}

@Serializable
data class GuiSettings(
    val language: String = DEFAULT_LANGUAGE,
    val theme: FractalThemeId = FractalThemeId.SOLID,
    /** Null means the theme's default wallpaper. */
    val wallpaper: WallpaperId? = null,
    /** Absolute path of the user's wallpaper image, used when [wallpaper] is CUSTOM. */
    val customWallpaperPath: String? = null,
    /** Freezes animated wallpapers. */
    val animationEconomy: Boolean = false,
    val introOnLaunch: Boolean = true,
    val libraryView: LibraryView = LibraryView.GRID,
    val librarySort: LibrarySort = LibrarySort.NAME,
    val onboardingCompleted: Boolean = false,
)

@Serializable
data class InputOverlayRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

@Serializable
data class InputOverlaySettings(
    val isVibrateOnTouchEnabled: Boolean = false,
    val isOverlayEnabled: Boolean = false,
    val controllerIndex: Int = 0,
    val alpha: Int = 64,
    val inputVisibilityMap: Map<OverlayInputConfig, Boolean> = emptyMap(),
    val inputOverlayRectMap: Map<OverlayInputConfig, InputOverlayRect> = emptyMap(),
)

@Serializable
data class AppSettings(
    val guiSettings: GuiSettings = GuiSettings(),
    val emulationSettings: EmulationSettings = EmulationSettings(),
    val inputOverlaySettings: InputOverlaySettings = InputOverlaySettings(),
    val hotkeySettings: Map<HotkeyAction, HotkeyCombo> = emptyMap(),
)

object AppSettingsSerializer : Serializer<AppSettings> {
    // unknown keys are ignored so settings written by a newer build don't reset everything
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: AppSettings = AppSettings()

    override suspend fun readFrom(input: InputStream): AppSettings = try {
        json.decodeFromString<AppSettings>(input.readBytes().decodeToString())
    } catch (_: Exception) {
        defaultValue
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

object AppSettingsStore {
    private lateinit var _dataStore: DataStore<AppSettings>
    val dataStore: DataStore<AppSettings>
        get() = _dataStore

    fun init(context: Context) {
        _dataStore = MultiProcessDataStoreFactory.create(
            serializer = AppSettingsSerializer,
            corruptionHandler = null,
            produceFile = { context.dataStoreFile("appSettings.json") },
        )
    }
}
