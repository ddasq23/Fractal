package dev.dfd.fractal.games.profile

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import dev.dfd.fractal.common.ui.components.FractalAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import dev.dfd.fractal.games.data.GameDataActions
import androidx.compose.foundation.layout.ColumnScope
import android.content.Context
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dfd.fractal.common.android.context.internalFolder
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.fractal.FractalScreen
import dev.dfd.fractal.common.ui.fractal.SectionTitle
import dev.dfd.fractal.common.ui.fractal.StatusChip
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.controllerTypeToString
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.settings.input.controller.GameControllerType
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.themedPanel
import dev.dfd.fractal.games.GameIcons
import dev.dfd.fractal.games.data.GameDataViewModel
import dev.dfd.fractal.games.data.rememberGameDataActions
import dev.dfd.fractal.games.library.GameArt
import dev.dfd.fractal.games.library.formatLastPlayed
import dev.dfd.fractal.games.library.formatPlaytime
import dev.dfd.fractal.games.library.lastPlayedDate
import dev.dfd.fractal.games.library.regionName
import dev.dfd.fractal.graphicpacks.GraphicPacksDownloadStatus
import dev.dfd.fractal.nativeinterface.NativeEmulation
import dev.dfd.fractal.nativeinterface.NativeGameTitles
import dev.dfd.fractal.nativeinterface.NativeGraphicPacks
import dev.dfd.fractal.nativeinterface.NativeGameTitles.DriverSettingMode
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game

@Composable
fun GameProfileEditScreen(
    game: Game,
    navigateBack: () -> Unit,
    goToGraphicPacks: () -> Unit = {},
    onPlay: ((Game) -> Unit)? = null,
    /** Set when the screen is opened over the running game, from the in-game menu. */
    inGame: InGameProfile? = null,
    /** Only the graphic packs and cheats, with every pack listed (the in-game menu's shortcut). */
    packsOnly: Boolean = false,
    viewModel: GameProfileEditViewModel = viewModel(
        factory = GameProfileEditViewModel.Factory,
        extras = MutableCreationExtras().apply {
            set(GameProfileEditViewModel.GAME_KEY, game)
        },
    ),
) {
    val context = LocalContext.current
    val supportsLoadingCustomDrivers = remember { NativeEmulation.supportsLoadingCustomDriver() }
    val snackbar = remember { SnackbarHostState() }
    val dataViewModel: GameDataViewModel = viewModel()
    val dataActions = rememberGameDataActions(snackbar, dataViewModel)

    val values by viewModel.values.collectAsState()
    val driverSettingChoices by viewModel.driverSettingChoices.collectAsState()
    val selectedDriverSetting by viewModel.selectedDriverSetting.collectAsState()
    val packs by viewModel.packs.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val dataInfo by viewModel.dataInfo.collectAsState()
    val controllerProfiles by viewModel.controllerProfiles.collectAsState()
    var confirmRemoveShaders by remember { mutableStateOf(false) }

    val userFolder = remember { context.internalFolder() }
    val packsDownloaded = remember(packs) { userFolder.resolve("graphicPacks/downloadedGraphicPacks").isDirectory }
    val downloading = downloadStatus == GraphicPacksDownloadStatus.CHECKING_VERSION ||
            downloadStatus == GraphicPacksDownloadStatus.DOWNLOADING ||
            downloadStatus == GraphicPacksDownloadStatus.EXTRACTING
    val packActions = remember(viewModel) {
        PackActions(
            setActive = viewModel::setPackActive,
            setPreset = viewModel::setPackPreset,
            select = viewModel::selectPackOption,
            download = { viewModel.downloadPacks(userFolder) },
            // the pack browser is a library screen; over the game there is only this game's list
            openBrowser = goToGraphicPacks.takeIf { inGame == null },
        )
    }

    if (inGame != null) {
        LaunchedEffect(viewModel) {
            viewModel.packApplied.collect { result ->
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(
                    if (result == NativeGraphicPacks.APPLY_DONE) tr("Applied to the running game")
                    else tr("This pack changes textures: restart the game to apply it")
                )
            }
        }
    }

    // the save / cache sizes change after an import or export finishes
    LaunchedEffect(dataViewModel.busy == null) { if (dataViewModel.busy == null) viewModel.refreshDataInfo() }
    LaunchedEffect(downloadStatus) {
        val message = when (downloadStatus) {
            GraphicPacksDownloadStatus.FINISHED_DOWNLOADING -> tr("Downloaded latest graphic packs")
            GraphicPacksDownloadStatus.NO_UPDATES_AVAILABLE -> tr("No updates available.")
            GraphicPacksDownloadStatus.ERROR -> tr("Failed to download graphic packs")
            else -> null
        } ?: return@LaunchedEffect
        viewModel.clearDownloadStatus()
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(message)
    }

    Box(Modifier.fillMaxSize()) {
        FractalScreen(title = if (packsOnly) tr("Graphic packs and cheats") else tr("Game settings"), onBack = navigateBack) {
            GameHeader(game, onPlay)
            if (inGame != null) {
                SettingPanel {
                    HintText(tr("Changes are saved to this game's profile right away. Graphic packs and cheats apply to the running game when they can; resolution packs and the other settings apply the next time the game starts."))
                    FractalButton(tr("Restart game"), onClick = inGame.onRestart, icon = FractalIcons.Refresh, primary = true)
                }
            }

            if (hasRecommendedPacks(game.titleId)) {
                SectionTitle(tr("Recommended"))
                RecommendedPacksCard(packs, downloading, packActions)
            } else {
                GamePackSettings(packs, packsDownloaded, downloading, packActions)
            }

            if (!packsOnly) {
                val v = values
                SectionTitle(tr("CPU"))
                if (v == null) {
                    HintText(tr("Loading…"))
                } else {
                    ChoiceRow(
                        title = tr("CPU mode"),
                        description = tr("Multi-core recompiler is the fastest for most games"),
                        value = v.cpuMode,
                        choices = listOf(
                            NativeGameTitles.CPUMode.AUTO,
                            NativeGameTitles.CPUMode.MULTICORERECOMPILER,
                            NativeGameTitles.CPUMode.SINGLECORERECOMPILER,
                            NativeGameTitles.CPUMode.SINGLECOREINTERPRETER,
                        ),
                        label = ::cpuModeToString,
                        onSelect = viewModel::setCpuMode,
                    )
                    ChoiceRow(
                        title = tr("Thread quantum"),
                        description = tr("Emulated CPU cycles a thread runs before the next one gets a turn"),
                        value = v.threadQuantum,
                        choices = (NativeGameTitles.THREAD_QUANTUM_VALUES + v.threadQuantum).distinct().sorted(),
                        label = { it.toString() },
                        onSelect = viewModel::setThreadQuantum,
                    )
                    ToggleRow(
                        title = tr("Load shared libraries"),
                        description = tr("Load libraries from the cafeLibs directory"),
                        checked = v.loadSharedLibraries,
                        onChange = viewModel::setLoadSharedLibraries,
                    )

                    SectionTitle(tr("Graphics"))
                    ToggleRow(
                        title = tr("Shader multiplication accuracy"),
                        description = tr("Controls the accuracy of floating point multiplication in shaders"),
                        checked = v.shaderMulAccuracy,
                        onChange = viewModel::setShaderMulAccuracy,
                    )
                    ToggleRow(
                        title = tr("Full sync at GX2DrawDone()"),
                        description = tr("If synchronization is requested by the game, the emulated CPU will wait for the GPU to finish all operations.\nThis is more accurate behavior, but may cause lower performance"),
                        checked = v.fullSyncAtGX2DrawDone,
                        onChange = viewModel::setFullSync,
                    )
                }

                SectionTitle(tr("Driver"))
                if (supportsLoadingCustomDrivers) {
                    ChoiceRow(
                        title = tr("Custom driver"),
                        description = tr("Vulkan driver used for this game"),
                        value = selectedDriverSetting,
                        choices = driverSettingChoices.ifEmpty { listOf(selectedDriverSetting) },
                        label = ::customDriverSettingChoiceToString,
                        onSelect = viewModel::setSelectedDriverSetting,
                    )
                }
                if (v != null) {
                    ToggleRow(
                        title = tr("Turnip: render in system memory"),
                        description = tr("Turnip only: render in system memory instead of tiles. Faster on Adreno 6xx and avoids GPU hangs in some games. Other drivers ignore it."),
                        checked = v.turnipSysmem,
                        onChange = viewModel::setTurnipSysmem,
                    )
                }

                controllerProfiles?.let { profiles ->
                    SectionTitle(tr("Controllers"))
                    ChoiceRow(
                        title = tr("Player 1 controller"),
                        description = tr("Some games only read the Wii U GamePad, others only a Pro Controller or Wii Remote. Your gamepad and the touch controls become this controller in this game only."),
                        value = profiles.player1Type,
                        choices = listOf<Int?>(null) + GameControllerType.CHOICES,
                        label = { it?.let(::controllerTypeToString) ?: tr("Same as the input settings") },
                        onSelect = viewModel::setPlayer1Type,
                    )
                    HintText(
                        if (profiles.available.isEmpty()) tr("Save a controller profile in Settings > Controllers to use it here, e.g. a Wii U GamePad setup for a game that needs one.")
                        else tr("The game loads these controller profiles when it starts; the input settings stay as they are.")
                    )
                    // four players cover most games; a slot further down shows when it has a profile
                    profiles.perSlot.forEachIndexed { index, slotProfile ->
                        // a type choice above is no profile of the user's
                        val profile = slotProfile?.takeUnless { GameControllerType.isTypeProfile(it) }
                        if (index >= 4 && profile == null) return@forEachIndexed
                        ChoiceRow(
                            title = tr("Controller {0}", index + 1),
                            description = null,
                            value = profile,
                            choices = listOf<String?>(null) + (profiles.available + listOfNotNull(profile)).distinct(),
                            label = { it ?: tr("Same as the input settings") },
                            onSelect = { viewModel.setControllerProfile(index, it) },
                            enabled = profiles.available.isNotEmpty() || profile != null,
                        )
                    }
                }
            }

            SectionTitle(tr("Graphic packs"))
            GamePacksSection(packs, packsDownloaded, downloading, packActions, startWithAll = packsOnly)

            // exporting or replacing a save or the shader cache of the game that is running could lose data
            if (inGame == null && !packsOnly) SavesAndCacheSection(game, dataInfo, dataActions, context) { confirmRemoveShaders = true }
            if (inGame == null && !packsOnly) CommunityShadersSection(game, snackbar) { viewModel.refreshDataInfo() }
            Spacer(Modifier.height(24.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (downloading) {
        PacksDownloadDialog(downloadStatus!!, onCancel = viewModel::cancelDownload)
    }

    if (confirmRemoveShaders) {
        FractalAlertDialog(
            onDismissRequest = { confirmRemoveShaders = false },
            title = { Text(tr("Remove shader caches")) },
            text = { Text(tr("Remove the shader caches for {0}?", game.name ?: "")) },
            dismissButton = { TextButton(onClick = { confirmRemoveShaders = false }) { Text(tr("No")) } },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveShaders = false
                    viewModel.removeShaderCaches()
                }) { Text(tr("Yes")) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameHeader(game: Game, onPlay: ((Game) -> Unit)?) {
    val colors = FractalTheme.colors
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // portrait phones: the Play button goes under the title, beside it the title had ~120 dp
        val stacked = maxWidth < 520.dp
        Column(
            Modifier
                .fillMaxWidth()
                .themedPanel()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameArt(game, Modifier.size(if (stacked) 72.dp else 88.dp).clip(FractalTheme.shapes.cover))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        game.name ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(regionName(game.region), icon = FractalIcons.Globe)
                        StatusChip("v${game.version}" + if (game.dlc > 0) " + DLC" else "")
                        // "Never played" once, not inside "{0} played" and "Last played: {0}"
                        val lastPlayed = game.lastPlayedDate
                        if (game.minutesPlayed > 0)
                            StatusChip(tr("{0} played", formatPlaytime(game.minutesPlayed)), icon = GameIcons.Clock)
                        if (lastPlayed != null)
                            StatusChip(tr("Last played: {0}", formatLastPlayed(lastPlayed)))
                        if (game.minutesPlayed <= 0 && lastPlayed == null)
                            StatusChip(tr("Never played"), icon = GameIcons.Clock)
                    }
                    Text(
                        "%016X".format(game.titleId),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
                if (onPlay != null && !stacked) {
                    Spacer(Modifier.width(12.dp))
                    FractalButton(tr("Play"), onClick = { onPlay(game) }, icon = FractalIcons.Play, primary = true)
                }
            }
            if (onPlay != null && stacked) {
                FractalButton(
                    tr("Play"),
                    onClick = { onPlay(game) },
                    icon = FractalIcons.Play,
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun customDriverSettingChoiceToString(driverSettingChoice: DriverSettingChoice): String =
    when (driverSettingChoice.mode) {
        DriverSettingMode.GLOBAL -> tr("Global")
        DriverSettingMode.SYSTEM -> tr("System driver")
        else -> driverSettingChoice.customDriverData?.name ?: tr("Custom driver")
    }

private fun cpuModeToString(cpuMode: Int): String = when (cpuMode) {
    NativeGameTitles.CPUMode.SINGLECOREINTERPRETER -> tr("Single-core interpreter")
    NativeGameTitles.CPUMode.SINGLECORERECOMPILER -> tr("Single-core recompiler")
    NativeGameTitles.CPUMode.MULTICORERECOMPILER -> tr("Multi-core recompiler")
    else -> tr("Auto (recommended)")
}

/** Options for the running game, opened from the in-game menu over [GameProfileEditScreen]. */
class InGameProfile(val onRestart: () -> Unit)

@Composable
private fun ColumnScope.SavesAndCacheSection(
    game: Game,
    info: GameDataInfo?,
    dataActions: GameDataActions,
    context: Context,
    onRemoveShaders: () -> Unit,
) {
    SectionTitle(tr("Saves & cache"))

    ActionRow(
        title = tr("Export save"),
        description = when {
            info == null -> null
            info.hasSave -> tr("Zip of this game's save, to keep or move to another device")
            else -> tr("This game has no save data yet")
        },
        icon = GameIcons.Export,
        onClick = { dataActions.exportSave(game) },
        enabled = info?.hasSave != false,
    )
    ActionRow(
        title = tr("Import save"),
        description = tr("Replaces the save with one from a zip; the current save is backed up first"),
        icon = GameIcons.Import,
        onClick = { dataActions.importSave(game) },
    )
    val cacheSize = info?.shaderCacheBytes?.takeIf { it > 0 }?.let { Formatter.formatFileSize(context, it) }
    ActionRow(
        title = tr("Export shader cache"),
        description = when {
            info == null -> null
            cacheSize != null -> tr("Transferable cache ({0}): shaders compiled while playing", cacheSize)
            else -> tr("This game has no shader cache yet")
        },
        icon = GameIcons.Export,
        onClick = { dataActions.exportShaderCache(game) },
        enabled = cacheSize != null || info == null,
    )
    ActionRow(
        title = tr("Import shader cache"),
        description = tr("Use a cache made for this same game to skip shader compile stutters"),
        icon = GameIcons.Import,
        onClick = { dataActions.importShaderCache(game) },
    )
    ActionRow(
        title = tr("Remove shader caches"),
        description = tr("Deletes the shader and pipeline caches of this game; they are rebuilt while playing"),
        icon = FractalIcons.Trash,
        onClick = onRemoveShaders,
    )
}
