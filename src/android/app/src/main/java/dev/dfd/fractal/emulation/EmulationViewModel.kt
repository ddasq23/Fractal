package dev.dfd.fractal.emulation

import android.view.SurfaceHolder
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dfd.fractal.common.either.Either
import dev.dfd.fractal.common.either.Error
import dev.dfd.fractal.common.either.Success
import dev.dfd.fractal.common.either.attemptWithContext
import dev.dfd.fractal.common.either.bind
import dev.dfd.fractal.common.either.mapError
import dev.dfd.fractal.common.settings.AppSettings
import dev.dfd.fractal.common.settings.AppSettingsStore
import dev.dfd.fractal.common.settings.InputOverlaySettings
import dev.dfd.fractal.common.settings.OverlayControllerType
import dev.dfd.fractal.common.settings.applyEditedRects
import dev.dfd.fractal.common.settings.migrateLegacyRects
import dev.dfd.fractal.common.settings.resetActiveLayout
import dev.dfd.fractal.emulation.inputoverlay.OverlayEditResult
import dev.dfd.fractal.nativeinterface.NativeEmulation
import dev.dfd.fractal.nativeinterface.NativeEmulation.PrepareTitleResult
import dev.dfd.fractal.nativeinterface.NativeException
import dev.dfd.fractal.nativeinterface.NativeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SideMenuState(
    val isMotionEnabled: Boolean = false,
    val isTVReplacedWithPad: Boolean = false,
    val isPadVisible: Boolean = false,
    val isInputOverlayVisible: Boolean = false,
    /** Paused from the menu or a hotkey; going to the background and back keeps it paused. */
    val isPaused: Boolean = false,
)

/** Emulator settings the in-game menu changes live; they are global and saved when the menu closes. */
data class DisplayOptions(
    val upscaleFilter: Int = NativeSettings.ScalingFilter.BILINEAR_FILTER,
    val upscaleSharpness: Int = 0,
    val downscaleFilter: Int = NativeSettings.ScalingFilter.BILINEAR_FILTER,
    val fullscreenScaling: Int = NativeSettings.FullscreenScaling.KEEP_ASPECT_RATIO,
    val vsyncMode: Int = NativeSettings.VSyncMode.DOUBLE_BUFFERING,
    val showFps: Boolean = false,
) {
    companion object {
        fun fromNative() = DisplayOptions(
            upscaleFilter = NativeSettings.getUpscalingFilter(),
            upscaleSharpness = NativeSettings.getUpscalingSharpness(),
            downscaleFilter = NativeSettings.getDownscalingFilter(),
            fullscreenScaling = NativeSettings.getFullscreenScaling(),
            vsyncMode = NativeSettings.getVsyncMode(),
            showFps = NativeSettings.isOverlayFPSEnabled(),
        )
    }
}

class ConditionFlags(
    var isMainConditionMet: Boolean = false, var isPadConditionMet: Boolean = false
) {
    fun get(isMain: Boolean): Boolean {
        return if (isMain) isMainConditionMet else isPadConditionMet
    }

    fun set(isMain: Boolean, value: Boolean) {
        if (isMain) {
            isMainConditionMet = value
        } else {
            isPadConditionMet = value
        }
    }
}

sealed interface NativeError {
    data class SurfaceCreationError(val message: String) : NativeError
    data class RendererInitializationError(val message: String) : NativeError

    object GameFilesNotFoundError : NativeError
    object NoDiscKeysError : NativeError
    object NoTitleTikError : NativeError
    data class UnknownTilePrepareError(val launchPath: String) : NativeError
    data class SystemInitializationError(val message: String) : NativeError

    object LaunchingTitleError : NativeError
}

class EmulationViewModel(
    private val launchPath: String,
    private val dataStore: DataStore<AppSettings> = AppSettingsStore.dataStore
) : ViewModel() {
    private val _emulationError = MutableStateFlow<NativeError?>(null)
    val emulationError = _emulationError.asStateFlow()

    private val _sideMenuState = MutableStateFlow(SideMenuState())
    val sideMenuState = _sideMenuState.asStateFlow()

    val isInputOverlayVisible =
        sideMenuState.map { it.isInputOverlayVisible }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                false,
            )

    init {
        viewModelScope.launch {
            val settings = dataStore.data.first()
            _sideMenuState.update { it.copy(isInputOverlayVisible = settings.inputOverlaySettings.isOverlayEnabled) }
        }
    }

    val inputOverlaySettings = dataStore.data.map { it.inputOverlaySettings }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        InputOverlaySettings(),
    )

    fun updateInputOverlaySettings(transform: (InputOverlaySettings) -> InputOverlaySettings) {
        viewModelScope.launch {
            dataStore.updateData {
                it.copy(inputOverlaySettings = transform(it.inputOverlaySettings))
            }
        }
    }

    fun saveInputOverlayEdits(result: OverlayEditResult) {
        if (result.isEmpty) return
        updateInputOverlaySettings {
            it.applyEditedRects(result.controllerType, result.rects, result.macroRects)
        }
    }

    /** Converts the pixel layout of older versions, measured on an overlay of this size. */
    fun migrateLegacyInputOverlayLayout(width: Int, height: Int) =
        updateInputOverlaySettings { it.migrateLegacyRects(width, height) }

    /** Resets positions and sizes of the active layout profile of [controllerType]. */
    fun resetInputOverlayLayout(controllerType: OverlayControllerType?) =
        updateInputOverlaySettings { settings ->
            val reset = controllerType?.let { settings.resetActiveLayout(it) } ?: settings
            reset.copy(inputOverlayRectMap = emptyMap())
        }

    fun updateSideMenuState(sideMenuState: SideMenuState) {
        _sideMenuState.value = sideMenuState
    }

    /** Suspends or resumes the game's threads. Does nothing before the game runs. */
    fun setPaused(paused: Boolean) {
        if (!_isEmulationInitialized.value || _emulationError.value != null) return
        if (_sideMenuState.value.isPaused == paused) return
        _sideMenuState.update { it.copy(isPaused = paused) }
        // with the main surface gone the game is paused already and resumes with it
        if (destroyedSurfaces.get(isMain = true)) return
        if (paused) NativeEmulation.pauseTitle() else NativeEmulation.resumeTitle()
    }

    private val _displayOptions = MutableStateFlow(DisplayOptions.fromNative())
    val displayOptions = _displayOptions.asStateFlow()
    private var displayOptionsChanged = false

    /** Reads the options again, e.g. when the menu opens. */
    fun refreshDisplayOptions() {
        _displayOptions.value = DisplayOptions.fromNative()
    }

    /** Applies [transform] to the live emulator settings; they are saved by [saveDisplayOptions]. */
    fun updateDisplayOptions(transform: (DisplayOptions) -> DisplayOptions) {
        val old = _displayOptions.value
        val new = transform(old)
        if (new == old) return
        if (new.upscaleFilter != old.upscaleFilter) NativeSettings.setUpscalingFilter(new.upscaleFilter)
        if (new.upscaleSharpness != old.upscaleSharpness) NativeSettings.setUpscalingSharpness(new.upscaleSharpness)
        if (new.downscaleFilter != old.downscaleFilter) NativeSettings.setDownscalingFilter(new.downscaleFilter)
        if (new.fullscreenScaling != old.fullscreenScaling) NativeSettings.setFullscreenScaling(new.fullscreenScaling)
        if (new.vsyncMode != old.vsyncMode) NativeSettings.setVsyncMode(new.vsyncMode)
        if (new.showFps != old.showFps) NativeSettings.setOverlayFPSEnabled(new.showFps)
        _displayOptions.value = new
        displayOptionsChanged = true
    }

    /** Writes settings.xml if the menu or a hotkey changed something. */
    fun saveDisplayOptions() {
        if (!displayOptionsChanged) return
        displayOptionsChanged = false
        viewModelScope.launch(Dispatchers.IO) { runCatching { NativeSettings.saveSettings() } }
    }

    val gamePadPosition = dataStore.data.map { it.emulationSettings.gamePadPosition }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null,
        )

    val destroyedSurfaces = ConditionFlags()
    var setSurfaces = ConditionFlags()

    private inner class CanvasSurfaceHolderCallback(val isMainCanvas: Boolean) :
        SurfaceHolder.Callback {

        override fun surfaceCreated(surfaceHolder: SurfaceHolder) {}

        override fun surfaceChanged(
            surfaceHolder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            try {
                NativeEmulation.setSurfaceSize(width, height, isMainCanvas)

                if (setSurfaces.get(isMainCanvas)) {
                    return
                }

                NativeEmulation.setSurface(surfaceHolder.surface, isMainCanvas)
                val mainSurfaceWasDestroyed = destroyedSurfaces.get(isMain = true)

                if (mainSurfaceWasDestroyed && isMainCanvas && !_sideMenuState.value.isPaused) {
                    NativeEmulation.resumeTitle()
                }

                setSurfaces.set(isMainCanvas, true)

                val padSurfaceWasSet = setSurfaces.get(isMain = false)
                if ((!isMainCanvas && !mainSurfaceWasDestroyed) || (isMainCanvas && padSurfaceWasSet)) {
                    NativeEmulation.initializeSurface(isMainCanvas = false)
                }

                destroyedSurfaces.set(isMainCanvas, false)
            } catch (exception: NativeException) {
                _emulationError.value = NativeError.SurfaceCreationError(exception.message!!)
            }
        }

        override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
            if (setSurfaces.get(isMain = false)) {
                NativeEmulation.clearPadSurface()
                setSurfaces.set(isMain = false, false)
                destroyedSurfaces.set(isMain = false, true)
            }

            if (isMainCanvas) {
                NativeEmulation.pauseTitle()

                setSurfaces.set(isMain = true, false)
                destroyedSurfaces.set(isMain = true, true)
            }
        }
    }

    val mainHolderCallback: SurfaceHolder.Callback = CanvasSurfaceHolderCallback(true)
    val padHolderCallback: SurfaceHolder.Callback = CanvasSurfaceHolderCallback(false)

    private suspend fun initializeSystems() = attemptWithContext(Dispatchers.IO) {
        NativeEmulation.initializeSystems()
    }.mapError { NativeError.SystemInitializationError(it) }

    private suspend fun initializeRenderer() = attemptWithContext(Dispatchers.IO) {
        NativeEmulation.initializeRenderer()
        NativeEmulation.initializeSurface(isMainCanvas = true)
    }.mapError { NativeError.RendererInitializationError(it) }

    private suspend fun prepareTitle(): Either<Unit, NativeError> =
        attemptWithContext(Dispatchers.IO) { NativeEmulation.prepareTitle(launchPath) }
            .fold(
                onSuccess = { result ->
                    when (result) {
                        PrepareTitleResult.SUCCESSFUL -> Success(Unit)
                        PrepareTitleResult.ERROR_GAME_BASE_FILES_NOT_FOUND -> Error(NativeError.GameFilesNotFoundError)
                        PrepareTitleResult.ERROR_NO_DISC_KEY -> Error(NativeError.NoDiscKeysError)
                        PrepareTitleResult.ERROR_NO_TITLE_TIK -> Error(NativeError.NoTitleTikError)
                        else -> Error(NativeError.UnknownTilePrepareError(launchPath))
                    }
                },
                onError = { Error(NativeError.UnknownTilePrepareError(launchPath)) }
            )

    private suspend fun launchTitle() =
        attemptWithContext(Dispatchers.IO) { NativeEmulation.launchTitle() }
            .mapError { NativeError.LaunchingTitleError }

    private val _isEmulationInitialized = MutableStateFlow(false)
    val isEmulationInitialized = _isEmulationInitialized.asStateFlow()
    private var emulationInitializationJob: Job? = null
    fun initializeEmulation() {
        if (_isEmulationInitialized.value || emulationInitializationJob != null) {
            return
        }

        emulationInitializationJob = viewModelScope.launch {
            prepareTitle()
                .bind { initializeSystems() }
                .bind { initializeRenderer() }
                .bind { launchTitle() }
                .onError { _emulationError.value = it }

            _isEmulationInitialized.value = true
        }
    }

    companion object {
        val LAUNCH_PATH_KEY = object : CreationExtras.Key<String> {}
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EmulationViewModel(
                    this[LAUNCH_PATH_KEY] as String
                )
            }
        }
    }
}