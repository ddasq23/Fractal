package dev.dfd.fractal.graphicpacks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.ui.components.FractalAlertDialog
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface
import dev.dfd.fractal.nativeinterface.NativeGameTitles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens the pickers and the dialogs of a mod install; [show] goes on the screen once, [card] at the top of the list. */
class ModInstallUi(
    val installZip: () -> Unit,
    val installFolder: () -> Unit,
    val showHelp: () -> Unit,
    val dialogs: @Composable () -> Unit,
)

@Composable
fun rememberModInstallUi(onInstalled: (List<String>) -> Unit, onError: (String) -> Unit): ModInstallUi {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ModInstaller.Result.NeedsGame?>(null) }
    var help by remember { mutableStateOf(false) }

    fun handle(result: ModInstaller.Result) {
        when (result) {
            is ModInstaller.Result.Installed -> onInstalled(result.names)
            is ModInstaller.Result.NeedsGame -> pending = result
            is ModInstaller.Result.Failed -> onError(result.error)
        }
    }

    fun run(block: () -> ModInstaller.Result) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { block() }
            busy = false
            handle(result)
        }
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run { ModInstaller.installZip(context, uri) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) run { ModInstaller.installFolder(context, uri) }
    }

    return ModInstallUi(
        installZip = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
        installFolder = { folderPicker.launch(null) },
        showHelp = { help = true },
        dialogs = {
            if (busy) {
                FractalAlertDialog(
                    onDismissRequest = {},
                    title = { Text(tr("Installing the mod")) },
                    text = { Text(tr("Copying the files…")) },
                    confirmButton = {},
                    hints = emptyList(),
                )
            }
            if (help) ModsHelpDialog(onDismiss = { help = false })
            pending?.let { p ->
                ModGamePickerDialog(
                    suggestedName = p.suggestedName,
                    onPick = { name, game, titleIds ->
                        pending = null
                        run { ModInstaller.installWithRules(p, name, game, titleIds) }
                    },
                    onDismiss = {
                        ModInstaller.discard(p)
                        pending = null
                    },
                )
            }
        },
    )
}

/** Short guide at the top of the graphic packs list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModsCard(ui: ModInstallUi) {
    Column(
        Modifier
            .fillMaxWidth()
            .fractalSurface(FractalTheme.shapes.card, FractalTheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tr("Mods"), style = MaterialTheme.typography.titleMedium, color = FractalTheme.colors.text)
        Text(
            tr("Mods are installed as graphic packs. Install one from a .zip or a folder, then turn it on here or in the game's graphic packs, and start the game."),
            style = MaterialTheme.typography.bodyMedium,
            color = FractalTheme.colors.textSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FractalButton(tr("Install mod (.zip)"), onClick = ui.installZip, icon = FractalIcons.Download, primary = true)
            FractalButton(tr("Install from a folder"), onClick = ui.installFolder, icon = FractalIcons.FolderAdd)
            FractalButton(tr("How mods work"), onClick = ui.showHelp, icon = FractalIcons.Info)
        }
    }
}

@Composable
private fun ModsHelpDialog(onDismiss: () -> Unit) {
    FractalAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("How mods work")) },
        text = {
            Text(
                tr(
                    "Fractal runs Cemu mods, which are graphic packs: a folder with a rules.txt file and, for mods that replace game files, a content folder (aoc for DLC files, code for executables).\n\n" +
                        "1. Download a mod made for Cemu (GameBanana, GitHub, the mod's page). A .zip is fine, there's no need to extract it.\n" +
                        "2. Tap \"Install mod (.zip)\" or \"Install from a folder\". The mod is copied to graphicPacks/mods in the emulator data folder.\n" +
                        "3. Mods made for the console (SDCafiine) have only the content folder: Fractal asks which game they are for and writes the rules.txt.\n" +
                        "4. Open the mod in this list (it appears under the game's name), turn it on and pick its options, if any. It is also listed in the game's graphic packs.\n" +
                        "5. Start the game. Mods are read when the game starts: to turn one off, restart the game.\n\n" +
                        "Mods for other consoles (Switch versions of the same game) don't work. If a game stops starting, turn its mods off one by one."
                )
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("OK")) } },
    )
}

@Composable
private fun ModGamePickerDialog(
    suggestedName: String,
    onPick: (name: String, game: String, titleIds: List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }
    val games: List<Pair<Long, String>> = remember { installedGames() }
    FractalAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Which game is this mod for?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("This mod has the game files but no rules.txt. Pick its game and Fractal creates one."))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("Mod name")) }, singleLine = true)
                if (games.isEmpty()) Text(tr("No games in the library yet: add your game folder first."))
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(games) { entry ->
                        val (titleId, gameName) = entry
                        TextButton(onClick = { onPick(name, gameName, listOf(titleId)) }, modifier = Modifier.fillMaxWidth()) {
                            Text(gameName, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** Installed games as (base title id, name), sorted by name. */
private fun installedGames(): List<Pair<Long, String>> {
    val result = mutableListOf<Pair<Long, String>>()
    for (id in NativeGameTitles.getInstalledGamesTitleIds()) {
        val game = NativeGameTitles.getGame(id) ?: continue
        if (result.any { it.first == game.titleId }) continue
        result.add(game.titleId to (game.name ?: "%016X".format(game.titleId)))
    }
    return result.sortedBy { it.second.lowercase() }
}
