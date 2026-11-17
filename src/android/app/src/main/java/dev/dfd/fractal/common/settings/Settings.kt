package dev.dfd.fractal.common.settings

import dev.dfd.fractal.common.android.inputdevice.listGamepads
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import dev.dfd.fractal.common.ui.theme.FractalThemeId
import dev.dfd.fractal.common.ui.theme.WallpaperId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class EmulationSettings(
    val gamePadPosition: GamePadPosition = GamePadPosition.RIGHT,
    val isPadVisible: Boolean = false,
    /** With a second display (dual screen handhelds such as the AYN Thor), the GamePad screen goes there. */
    val isPadOnSecondScreen: Boolean = true,
    /** GamePad screen image: 0 keep aspect ratio, 1 stretch. */
    val padScaling: Int = 0,
    val padSizePercent: Int = 100,
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
    /** App language code; null until the first run picks one from the device language. */
    val language: String? = null,
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
    /** Library background music (a track per theme) and its volume, 0..100. */
    val menuMusicEnabled: Boolean = true,
    val menuMusicVolume: Int = DEFAULT_MENU_VOLUME,
    /** Sound effects of the library and settings screens and their volume, 0..100. */
    val menuSfxEnabled: Boolean = true,
    val menuSfxVolume: Int = DEFAULT_MENU_VOLUME,
    /** The user's own library music (the app's copy of the file), played instead of the theme's track when on. */
    val customMusicPath: String? = null,
    val customMusicEnabled: Boolean = false,
    /** The user's own sound effects by [dev.dfd.fractal.common.sound.MenuSfx] name (the app's copies), used when on. */
    val customSfxPaths: Map<String, String> = emptyMap(),
    val customSfxEnabled: Boolean = false,
)

/** Comfortable menu volume: there, not in the way. */
const val DEFAULT_MENU_VOLUME = 25

/** Absolute pixel rectangle, as saved by versions before layout profiles. */
@Serializable
data class InputOverlayRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

const val DEFAULT_OVERLAY_ALPHA = 150
const val OVERLAY_SCALE_MIN = 0.5f
const val OVERLAY_SCALE_MAX = 2.0f

/** Whether the on-screen controls show: the user's choice, or by default only when no game controller is connected. */
fun InputOverlaySettings.isOverlayShown(): Boolean =
    isOverlayEnabled ?: listGamepads().isEmpty()

@Serializable
data class InputOverlaySettings(
    val isVibrateOnTouchEnabled: Boolean = false,
    /** null until the user picks: the overlay then shows only when no game controller is connected (phones). */
    val isOverlayEnabled: Boolean? = null,
    val controllerIndex: Int = 0,
    /** Opacity of the inputs, 0..255. */
    val alpha: Int = DEFAULT_OVERLAY_ALPHA,
    /** Size multiplier applied to every input on top of its own size. */
    val scale: Float = 1f,
    /** A finger moving from a button onto another releases the first and presses the second. */
    val slideBetweenButtons: Boolean = false,
    /** Inputs moved or resized in edit mode snap to a grid. */
    val snapToGrid: Boolean = false,
    /** Inputs moved in edit mode snap to the edges and centers of the others, the middle of the screen and mirrored spots. */
    val snapToGuides: Boolean = true,
    /** The game pauses while the on-screen controls are edited from the in-game menu. */
    val pauseWhileEditing: Boolean = true,
    val inputVisibilityMap: Map<OverlayInputConfig, Boolean> = emptyMap(),
    /** Legacy absolute pixel layout; moved into [profiles] by the overlay on first load. */
    val inputOverlayRectMap: Map<OverlayInputConfig, InputOverlayRect> = emptyMap(),
    val profiles: List<OverlayLayoutProfile> = emptyList(),
    val activeProfileIds: Map<OverlayControllerType, String> = emptyMap(),
    /** Colors of the inputs: the app theme's, or a style of their own. */
    val style: OverlayStyle = OverlayStyle.THEME,
    /** Color of [OverlayStyle.CUSTOM], ARGB. */
    val customColor: Int = DEFAULT_OVERLAY_CUSTOM_COLOR,
)

@Serializable
data class AppSettings(
    val guiSettings: GuiSettings = GuiSettings(),
    val emulationSettings: EmulationSettings = EmulationSettings(),
    val inputOverlaySettings: InputOverlaySettings = InputOverlaySettings(),
    val hotkeySettings: Map<HotkeyAction, HotkeyCombo> = emptyMap(),
    val performanceHud: PerformanceHudSettings = PerformanceHudSettings(),
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
