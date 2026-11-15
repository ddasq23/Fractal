package dev.dfd.fractal.games.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dfd.fractal.common.customdrivers.parseInstalledDrivers
import dev.dfd.fractal.common.customdrivers.profileDispatcher
import dev.dfd.fractal.games.data.GameDataTransfer
import dev.dfd.fractal.graphicpacks.GraphicPacksDownloadStatus
import dev.dfd.fractal.graphicpacks.GraphicPacksDownloader
import dev.dfd.fractal.graphicpacks.Preset
import dev.dfd.fractal.nativeinterface.NativeGameTitles
import dev.dfd.fractal.nativeinterface.NativeGameTitles.DriverSettingMode
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game
import dev.dfd.fractal.nativeinterface.NativeGraphicPacks
import dev.dfd.fractal.nativeinterface.NativeInput
import dev.dfd.fractal.nativeinterface.NativeSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CustomDriverData(
    val customPath: String,
    val name: String,
)

data class DriverSettingChoice(
    val mode: Int,
    val customDriverData: CustomDriverData? = null,
)

/** The per-game profile values the screen edits; null until read from the native profile. */
data class ProfileValues(
    val loadSharedLibraries: Boolean,
    val shaderMulAccuracy: Boolean,
    val fullSyncAtGX2DrawDone: Boolean,
    val turnipSysmem: Boolean,
    val cpuMode: Int,
    val threadQuantum: Int,
)

/** A graphic pack made for this game, with its presets. */
data class GamePack(
    val id: Long,
    val path: String,
    val name: String,
    /** Folders between the game and the pack, like "Mods" or "Enhancements". */
    val group: String,
    val description: String,
    val active: Boolean,
    val presets: List<Preset>,
)

/** Controller profile each slot loads for this game (null: the input settings) and the saved profiles. */
data class GameControllerProfiles(val perSlot: List<String?>, val available: List<String>)

/** Saves & cache section: what there is to export. */
data class GameDataInfo(val hasSave: Boolean, val shaderCacheBytes: Long)

class GameProfileEditViewModel(val game: Game) : ViewModel() {
    private val titleId = game.titleId

    // region profile

    private val _values = MutableStateFlow<ProfileValues?>(null)
    val values = _values.asStateFlow()

    private fun loadValues() = ProfileValues(
        loadSharedLibraries = NativeGameTitles.isLoadingSharedLibrariesForTitleEnabled(titleId),
        shaderMulAccuracy = NativeGameTitles.isShaderMultiplicationAccuracyForTitleEnabled(titleId),
        fullSyncAtGX2DrawDone = NativeGameTitles.isFullSyncAtGX2DrawDoneForTitleEnabled(titleId),
        turnipSysmem = NativeGameTitles.isTurnipSysmemForTitleEnabled(titleId),
        cpuMode = NativeGameTitles.getCpuModeForTitle(titleId),
        threadQuantum = NativeGameTitles.getThreadQuantumForTitle(titleId),
    )

    /** Updates the screen right away and writes the profile on the profile thread. */
    private fun edit(transform: (ProfileValues) -> ProfileValues, write: () -> Unit) {
        _values.update { it?.let(transform) }
        viewModelScope.launch(profileDispatcher) { runCatching(write) }
    }

    fun setLoadSharedLibraries(on: Boolean) =
        edit({ it.copy(loadSharedLibraries = on) }) { NativeGameTitles.setLoadingSharedLibrariesForTitleEnabled(titleId, on) }

    fun setShaderMulAccuracy(on: Boolean) =
        edit({ it.copy(shaderMulAccuracy = on) }) { NativeGameTitles.setShaderMultiplicationAccuracyForTitleEnabled(titleId, on) }

    fun setFullSync(on: Boolean) =
        edit({ it.copy(fullSyncAtGX2DrawDone = on) }) { NativeGameTitles.setFullSyncAtGX2DrawDoneForTitleEnabled(titleId, on) }

    fun setTurnipSysmem(on: Boolean) =
        edit({ it.copy(turnipSysmem = on) }) { NativeGameTitles.setTurnipSysmemForTitleEnabled(titleId, on) }

    fun setCpuMode(mode: Int) =
        edit({ it.copy(cpuMode = mode) }) { NativeGameTitles.setCpuModeForTitle(titleId, mode) }

    fun setThreadQuantum(quantum: Int) =
        edit({ it.copy(threadQuantum = quantum) }) { NativeGameTitles.setThreadQuantumForTitle(titleId, quantum) }

    // endregion

    // region driver

    private val _selectedDriverSetting =
        MutableStateFlow(DriverSettingChoice(DriverSettingMode.GLOBAL))
    val selectedDriverSetting = _selectedDriverSetting.asStateFlow()

    private val _driverSettingChoices = MutableStateFlow<List<DriverSettingChoice>>(listOf())
    val driverSettingChoices = _driverSettingChoices.asStateFlow()

    fun setSelectedDriverSetting(driverSetting: DriverSettingChoice) {
        if (driverSetting.mode != DriverSettingMode.GLOBAL
            && driverSetting.mode != DriverSettingMode.SYSTEM
            && !driverSettingChoices.value.any { it.customDriverData != null && it.customDriverData.customPath == driverSetting.customDriverData?.customPath }
        ) {
            return
        }

        val nativeDriverSetting = NativeGameTitles.DriverSetting(
            driverSetting.mode,
            driverSetting.customDriverData?.customPath
        )

        _selectedDriverSetting.value = driverSetting
        // each driver keeps its own pipeline cache, so switching needs no clearing
        viewModelScope.launch(profileDispatcher) {
            runCatching { NativeGameTitles.setDriverSettingForTitle(titleId, nativeDriverSetting) }
        }
    }

    private suspend fun getDriverSettingsChoices(): List<DriverSettingChoice> {
        val globalDriverSetting = DriverSettingChoice(DriverSettingMode.GLOBAL)

        val systemDriverSetting = DriverSettingChoice(DriverSettingMode.SYSTEM)

        val installedDrivers = parseInstalledDrivers().map {
            val data = CustomDriverData(it.path, it.metadata.name)
            DriverSettingChoice(
                DriverSettingMode.CUSTOM,
                data,
            )
        }

        val titleDriverSetting = withContext(profileDispatcher) { NativeGameTitles.getDriverSettingForTitle(titleId) }
        val selectedDriverName =
            if (titleDriverSetting.mode == DriverSettingMode.CUSTOM && titleDriverSetting.customPath != null) {
                installedDrivers.firstOrNull { it.customDriverData?.customPath == titleDriverSetting.customPath }
                    ?.customDriverData?.name
            } else {
                null
            }

        if (titleDriverSetting.mode == DriverSettingMode.CUSTOM && selectedDriverName != null) {
            _selectedDriverSetting.value = DriverSettingChoice(
                mode = DriverSettingMode.CUSTOM,
                customDriverData = CustomDriverData(
                    customPath = titleDriverSetting.customPath!!,
                    name = selectedDriverName,
                )
            )
        } else {
            val mode = if (titleDriverSetting.mode == DriverSettingMode.CUSTOM)
                DriverSettingMode.GLOBAL
            else
                titleDriverSetting.mode
            _selectedDriverSetting.value = DriverSettingChoice(mode = mode)
        }

        return listOf(globalDriverSetting) + listOf(systemDriverSetting) + installedDrivers
    }

    // endregion

    // region graphic packs

    private val _packs = MutableStateFlow<List<GamePack>?>(null)
    val packs = _packs.asStateFlow()

    private fun readPack(info: NativeGraphicPacks.GraphicPackBasicInfo): GamePack? {
        val pack = NativeGraphicPacks.getGraphicPack(info.id) ?: return null
        val segments = info.virtualPath.split('/')
        return GamePack(
            id = info.id,
            path = info.virtualPath,
            name = pack.name.ifBlank { segments.last() },
            group = if (segments.size > 2) segments.subList(1, segments.size - 1).joinToString(" / ") else "",
            description = pack.description,
            active = pack.isActive(),
            presets = Preset.fromNativeGraphicPack(pack),
        )
    }

    private fun loadPacks(): List<GamePack> = NativeGraphicPacks.getGraphicPackBasicInfos()
        .filter { titleId in it.titleIds }
        .mapNotNull { runCatching { readPack(it) }.getOrNull() }
        .sortedWith(compareBy<GamePack>({ it.group.isNotEmpty() }, { it.group.lowercase() }, { it.name.lowercase() }))

    fun refreshPacks() {
        viewModelScope.launch(profileDispatcher) {
            _packs.value = runCatching { loadPacks() }.getOrDefault(emptyList())
        }
    }

    private fun updatePack(id: Long, transform: (GamePack) -> GamePack) =
        _packs.update { list -> list?.map { if (it.id == id) transform(it) else it } }

    fun setPackActive(id: Long, active: Boolean) {
        updatePack(id) { it.copy(active = active) }
        viewModelScope.launch(profileDispatcher) {
            runCatching { NativeGraphicPacks.setGraphicPackActive(id, active) }
        }
    }

    /** Picks a preset; presets of other categories can depend on it, so they're read back afterwards. */
    fun setPackPreset(id: Long, category: String?, choice: String, enable: Boolean = false) {
        updatePack(id) { pack ->
            pack.copy(
                active = pack.active || enable,
                presets = pack.presets.map { if (it.category == category) it.copy(activeChoice = choice) else it },
            )
        }
        viewModelScope.launch(profileDispatcher) {
            runCatching {
                NativeGraphicPacks.setGraphicPackActivePreset(id, category, choice)
                if (enable) NativeGraphicPacks.setGraphicPackActive(id, true)
                val presets = NativeGraphicPacks.getGraphicPackPresets(id).mapIndexed { index, preset ->
                    Preset(index, preset.category, preset.activePreset, preset.presets.toList())
                }
                updatePack(id) { it.copy(presets = presets) }
            }
        }
    }

    /**
     * Picks [option] of a pack-backed setting (resolution, FPS limit): the other packs of the setting are
     * turned off first, they would fight over the same thing; null turns them all off.
     */
    fun selectPackOption(setting: PackSetting, option: PackOption?) {
        val active = _packs.value.orEmpty().filter { it.active }.mapTo(HashSet()) { it.id }
        setting.packIds.filter { it != option?.packId && it in active }.forEach { setPackActive(it, false) }
        when {
            option == null -> Unit
            option.choice != null -> setPackPreset(option.packId, option.category, option.choice, enable = true)
            else -> setPackActive(option.packId, true)
        }
    }

    private val _downloadStatus = MutableStateFlow<GraphicPacksDownloadStatus?>(null)
    val downloadStatus = _downloadStatus.asStateFlow()
    private var downloadJob: Job? = null

    /** [userFolder] is the Fractal folder; graphic packs go in its graphicPacks/downloadedGraphicPacks. */
    fun downloadPacks(userFolder: File) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            try {
                GraphicPacksDownloader().download(userFolder).collect { status ->
                    _downloadStatus.value = status
                    if (status == GraphicPacksDownloadStatus.FINISHED_DOWNLOADING) refreshPacks()
                }
            } catch (e: CancellationException) {
                _downloadStatus.value = null
                throw e
            } catch (_: Exception) {
                _downloadStatus.value = GraphicPacksDownloadStatus.ERROR
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadStatus.value = null
    }

    fun clearDownloadStatus() {
        _downloadStatus.value = null
    }

    // endregion

    // region controllers

    private val _controllerProfiles = MutableStateFlow<GameControllerProfiles?>(null)
    val controllerProfiles = _controllerProfiles.asStateFlow()

    private fun loadControllerProfiles() = GameControllerProfiles(
        perSlot = (0..<NativeInput.MAX_CONTROLLERS).map { NativeGameTitles.getControllerProfileForTitle(titleId, it) },
        available = NativeInput.getControllerProfiles().sortedWith(String.CASE_INSENSITIVE_ORDER),
    )

    fun setControllerProfile(index: Int, profile: String?) {
        _controllerProfiles.update { it?.copy(perSlot = it.perSlot.toMutableList().apply { set(index, profile) }) }
        viewModelScope.launch(profileDispatcher) {
            runCatching { NativeGameTitles.setControllerProfileForTitle(titleId, index, profile) }
        }
    }

    // endregion

    // region saves & cache

    private val _dataInfo = MutableStateFlow<GameDataInfo?>(null)
    val dataInfo = _dataInfo.asStateFlow()

    fun refreshDataInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            _dataInfo.value = runCatching {
                GameDataTransfer.run { GameDataInfo(hasSave(titleId), shaderCacheFiles(titleId).sumOf { it.length() }) }
            }.getOrNull()
        }
    }

    fun removeShaderCaches() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { NativeGameTitles.removeShaderCacheFilesForTitle(titleId) }
            refreshDataInfo()
        }
    }

    // endregion

    init {
        viewModelScope.launch(profileDispatcher) {
            _values.value = runCatching { loadValues() }.getOrNull()
            _controllerProfiles.value = runCatching { loadControllerProfiles() }.getOrNull()
        }
        viewModelScope.launch {
            _driverSettingChoices.value = getDriverSettingsChoices()
        }
        refreshPacks()
        refreshDataInfo()
    }

    override fun onCleared() {
        // graphic pack choices live in settings.xml: write them once the pending changes are applied
        // (the profile dispatcher is serial) rather than waiting for the activity to pause
        CoroutineScope(profileDispatcher).launch { runCatching { NativeSettings.saveSettings() } }
    }

    companion object {
        val GAME_KEY = object : CreationExtras.Key<Game> {}
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GameProfileEditViewModel(
                    this[GAME_KEY] as Game
                )
            }
        }
    }
}
