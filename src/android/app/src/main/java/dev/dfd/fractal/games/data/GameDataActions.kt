/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.games.data

import android.app.Application
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TransferKind { SAVE, SHADERS }

/** Export/import entry points for one game; [rememberGameDataActions] wires them to the file pickers. */
class GameDataActions(
    val exportSave: (Game) -> Unit,
    val importSave: (Game) -> Unit,
    val exportShaderCache: (Game) -> Unit,
    val importShaderCache: (Game) -> Unit,
)

private data class PendingPick(val game: Game, val kind: TransferKind, val export: Boolean)

data class TransferBusy(val text: String, val done: Int = 0, val total: Int = 0)

data class ImportConfirmation(
    val game: Game,
    val kind: TransferKind,
    val source: Uri,
    val summary: ZipSummary,
)

/** Keeps the transfer going (and its dialogs) across recompositions and configuration changes. */
class GameDataViewModel(application: Application) : AndroidViewModel(application) {
    private var pending: PendingPick? = null

    var busy by mutableStateOf<TransferBusy?>(null)
        private set
    var confirmation by mutableStateOf<ImportConfirmation?>(null)
        private set

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val context get() = getApplication<Application>()

    /** Null when there's nothing to export: the message says why. */
    fun startExport(game: Game, kind: TransferKind): String? {
        val hasData = runCatching {
            if (kind == TransferKind.SAVE) GameDataTransfer.hasSave(game.titleId)
            else GameDataTransfer.shaderCacheFiles(game.titleId).isNotEmpty()
        }.getOrDefault(false)
        if (!hasData) {
            _messages.trySend(
                if (kind == TransferKind.SAVE) tr("This game has no save data yet") else tr("This game has no shader cache yet")
            )
            return null
        }
        pending = PendingPick(game, kind, export = true)
        return GameDataTransfer.suggestedFileName(game.name, kind)
    }

    fun startImport(game: Game, kind: TransferKind) {
        pending = PendingPick(game, kind, export = false)
    }

    private fun progress(text: String): (Int, Int) -> Unit = { done, total -> busy = TransferBusy(text, done, total) }

    fun onExportTarget(uri: Uri?) {
        val pick = pending?.takeIf { it.export } ?: return
        pending = null
        if (uri == null) return
        val text = if (pick.kind == TransferKind.SAVE) tr("Exporting save…") else tr("Exporting shader cache…")
        run(text) {
            val count = if (pick.kind == TransferKind.SAVE)
                GameDataTransfer.exportSave(context, pick.game.titleId, pick.game.name, uri, progress(text))
            else
                GameDataTransfer.exportShaderCache(context, pick.game.titleId, pick.game.name, uri, progress(text))
            if (pick.kind == TransferKind.SAVE) tr("Save exported ({0} files)", count)
            else tr("Shader cache exported ({0} files)", count)
        }
    }

    fun onImportSource(uri: Uri?) {
        val pick = pending?.takeIf { !it.export } ?: return
        pending = null
        if (uri == null) return
        run(tr("Checking the zip…")) {
            val summary = if (pick.kind == TransferKind.SAVE) GameDataTransfer.inspectSaveZip(context, uri)
            else GameDataTransfer.inspectShaderZip(context, uri, pick.game.titleId)
            confirmation = ImportConfirmation(pick.game, pick.kind, uri, summary)
            null
        }
    }

    fun dismissConfirmation() {
        confirmation = null
    }

    fun confirmImport() {
        val request = confirmation ?: return
        confirmation = null
        val text = if (request.kind == TransferKind.SAVE) tr("Importing save…") else tr("Importing shader cache…")
        run(text) {
            val game = request.game
            val backup = if (request.kind == TransferKind.SAVE)
                GameDataTransfer.importSave(context, game.titleId, game.name, request.source, request.summary, progress(text))
            else
                GameDataTransfer.importShaderCache(context, game.titleId, game.name, request.source, request.summary, progress(text))
            val done = if (request.kind == TransferKind.SAVE) tr("Save imported") else tr("Shader cache imported")
            if (backup != null) tr("{0}. The previous one was backed up to {1} in the Fractal folder", done, backup) else done
        }
    }

    /** Runs [block] on the IO dispatcher with the busy dialog up; its result (if any) is shown as a message. */
    private fun run(text: String, block: suspend () -> String?) {
        if (busy != null) return
        busy = TransferBusy(text)
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: TransferException) {
                e.message
            } catch (e: Exception) {
                tr("Failed: {0}", e.message ?: e.javaClass.simpleName)
            }
            busy = null
            if (message != null) _messages.send(message)
        }
    }
}

private val ZipMimeTypes = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

/**
 * File pickers, confirmation and progress dialogs for exporting/importing a game's save and shader
 * cache. Results are posted to [snackbar].
 */
@Composable
fun rememberGameDataActions(
    snackbar: SnackbarHostState,
    viewModel: GameDataViewModel = viewModel(),
): GameDataActions {
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        viewModel.onExportTarget(it)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.onImportSource(it)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    viewModel.busy?.let { TransferProgressDialog(it) }
    viewModel.confirmation?.let { request ->
        ImportConfirmationDialog(request, onConfirm = viewModel::confirmImport, onDismiss = viewModel::dismissConfirmation)
    }

    return remember(viewModel, createLauncher, openLauncher) {
        fun export(game: Game, kind: TransferKind) {
            viewModel.startExport(game, kind)?.let { name -> runCatching { createLauncher.launch(name) } }
        }

        fun import(game: Game, kind: TransferKind) {
            viewModel.startImport(game, kind)
            runCatching { openLauncher.launch(ZipMimeTypes) }
        }
        GameDataActions(
            exportSave = { export(it, TransferKind.SAVE) },
            importSave = { import(it, TransferKind.SAVE) },
            exportShaderCache = { export(it, TransferKind.SHADERS) },
            importShaderCache = { import(it, TransferKind.SHADERS) },
        )
    }
}

@Composable
private fun TransferProgressDialog(busy: TransferBusy) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(
            Modifier
                .widthIn(min = 280.dp, max = 420.dp)
                .fractalSurface(FractalTheme.shapes.panel, FractalTheme.surface, FractalTheme.colors.surface.copy(alpha = 1f))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), color = FractalTheme.colors.accent, strokeWidth = 3.dp)
                Text(busy.text, style = MaterialTheme.typography.titleMedium, color = FractalTheme.colors.text)
            }
            if (busy.total > 0) {
                LinearProgressIndicator(
                    progress = { busy.done.toFloat() / busy.total },
                    color = FractalTheme.colors.accent,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    tr("{0} of {1} files", busy.done, busy.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = FractalTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ImportConfirmationDialog(request: ImportConfirmation, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val size = remember(request) { Formatter.formatFileSize(context, request.summary.bytes) }
    val save = request.kind == TransferKind.SAVE
    val otherTitle = request.summary.sourceTitleId?.takeIf { it != request.game.titleId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (save) tr("Import save") else tr("Import shader cache")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("{0} files, {1}, for {2}.", request.summary.fileCount, size, request.game.name ?: ""))
                Text(
                    if (save) tr("The current save is replaced. A backup of it is kept in the Fractal folder (saveBackups).")
                    else tr("The current shader cache is replaced. A backup of it is kept in the Fractal folder (shaderCacheBackups).")
                )
                if (otherTitle != null) {
                    Text(
                        tr(
                            "This save was exported from another title ({0}). Saves usually only work in the same game and region.",
                            GameDataTransfer.titleIdString(otherTitle).uppercase(),
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(if (otherTitle == null) tr("Import") else tr("Import anyway")) }
        },
    )
}
