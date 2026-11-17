package dev.dfd.fractal.settings.datafolder

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.window.DialogProperties
import dev.dfd.fractal.common.android.context.DataFolder
import dev.dfd.fractal.common.android.context.internalFolder
import dev.dfd.fractal.common.ui.components.FractalAlertDialog
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.fractal.FractalScreen
import dev.dfd.fractal.common.ui.fractal.SettingsCard
import dev.dfd.fractal.common.ui.fractal.SettingsRowText
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.emulation.GameRestartActivity
import dev.dfd.fractal.nativeinterface.NativeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the screen is asking or doing after a folder was picked. */
private sealed interface Step {
    /** Move the current data into [target] (which has no Fractal data yet), [size] bytes. */
    data class ConfirmMove(val target: File, val size: Long) : Step

    /** [target] already holds Fractal data: use it as it is, or move the current data over it. */
    data class ExistingData(val target: File, val size: Long) : Step

    /** Back to the default folder, optionally moving the data there. */
    data class ConfirmDefault(val target: File, val size: Long) : Step
    data class Moving(val done: Long, val total: Long) : Step
    data class Failed(val error: String) : Step
}

@Composable
fun DataFolderScreen(navigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { context.internalFolder() }
    val customPath = remember { DataFolder.customPath(context) }
    val defaultFolder = remember { DataFolder.defaultFolder(context) }
    var step by remember { mutableStateOf<Step?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun bytes(n: Long) = Formatter.formatShortFileSize(context, n)

    fun applyAndRestart(target: File?, moveData: Boolean) {
        scope.launch {
            NativeSettings.saveSettings()
            if (moveData && target != null) {
                step = Step.Moving(0, 0)
                val error = withContext(Dispatchers.IO) {
                    DataFolder.move(current, target) { done, total -> step = Step.Moving(done, total) }
                }
                if (error != null) {
                    step = Step.Failed(error)
                    return@launch
                }
            }
            val isDefault = target == null || target.canonicalPath == defaultFolder.canonicalPath
            DataFolder.setCustomPath(context, if (isDefault) null else target!!.absolutePath)
            DataFolder.switching = true
            GameRestartActivity.restartApp(context)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = DataFolder.pathFromTreeUri(context, uri)
        if (picked == null) {
            message = tr("Pick a folder in internal storage or on an SD card.")
            return@rememberLauncherForActivityResult
        }
        val target = DataFolder.targetForPickedFolder(picked)
        if (target.canonicalPath == current.canonicalPath) {
            message = tr("Fractal already uses this folder.")
            return@rememberLauncherForActivityResult
        }
        if (target.canonicalPath.startsWith(current.canonicalPath + File.separator)) {
            message = tr("The new folder can't be inside the current data folder.")
            return@rememberLauncherForActivityResult
        }
        if (!target.isDirectory && !target.mkdirs()) {
            message = tr("Fractal can't write to this folder. Pick another one.")
            return@rememberLauncherForActivityResult
        }
        message = null
        scope.launch {
            val size = withContext(Dispatchers.IO) { DataFolder.sizeOf(current) }
            step = if (DataFolder.holdsFractalData(target)) Step.ExistingData(target, size) else Step.ConfirmMove(target, size)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (DataFolder.hasAllFilesAccess()) folderPicker.launch(null)
        else message = tr("Without \"All files access\" Fractal can only use its default folder.")
    }
    var askPermission by remember { mutableStateOf(false) }

    FractalScreen(title = tr("Emulator data folder"), onBack = navigateBack) {
        Text(
            tr("Saves, game updates and DLC installed in the emulator, shader caches, graphic packs, drivers and settings are kept in this folder. The default folder is inside Android/data, which newer Android versions hide from file managers and which is deleted with the app. A folder of your own, in internal storage or on an SD card, is easy to open, back up and keep across reinstalls."),
            style = MaterialTheme.typography.bodyMedium,
            color = FractalTheme.colors.textSecondary,
        )
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                SettingsRowText(tr("Current folder"), current.absolutePath)
                SettingsRowText(tr("Free space"), bytes(DataFolder.freeBytes(current)))
                if (customPath != null && current.absolutePath != customPath) {
                    Text(
                        tr("The chosen folder {0} can't be used now (SD card removed or \"All files access\" turned off), so the default folder is in use.", customPath),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FractalTheme.colors.accent,
                    )
                }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FractalTheme.colors.accent) }
        FractalButton(
            tr("Choose another folder"),
            onClick = { if (DataFolder.hasAllFilesAccess()) folderPicker.launch(null) else askPermission = true },
            icon = FractalIcons.Folder,
            primary = true,
        )
        if (customPath != null) {
            FractalButton(
                tr("Use the default folder"),
                onClick = {
                    scope.launch {
                        val size = withContext(Dispatchers.IO) { DataFolder.sizeOf(current) }
                        step = Step.ConfirmDefault(defaultFolder, size)
                    }
                },
                icon = FractalIcons.Refresh,
            )
        }
    }

    if (askPermission) {
        FractalAlertDialog(
            onDismissRequest = { askPermission = false },
            title = { Text(tr("All files access")) },
            text = { Text(tr("The emulator reads and writes its data folder directly, so a folder outside Android/data needs \"All files access\". Turn it on for Fractal on the next screen and come back.")) },
            confirmButton = {
                TextButton(onClick = {
                    askPermission = false
                    permissionLauncher.launch(DataFolder.allFilesAccessIntent(context))
                }) { Text(tr("Open settings")) }
            },
            dismissButton = { TextButton(onClick = { askPermission = false }) { Text(tr("Cancel")) } },
        )
    }

    when (val s = step) {
        null -> {}
        is Step.ConfirmMove -> {
            val free = DataFolder.freeBytes(s.target)
            // the Fractal subfolder was created when the folder was picked: don't leave it behind empty
            val cancel = {
                s.target.delete()
                step = null
            }
            FractalAlertDialog(
                onDismissRequest = cancel,
                title = { Text(tr("Move the data?")) },
                text = {
                    Text(
                        if (free < s.size) tr("{0} of data doesn't fit in {1} ({2} free).", bytes(s.size), s.target.absolutePath, bytes(free))
                        else tr("Move {0} of data to {1}? Fractal restarts when it is done.", bytes(s.size), s.target.absolutePath)
                    )
                },
                confirmButton = {
                    if (free >= s.size) TextButton(onClick = { applyAndRestart(s.target, moveData = true) }) { Text(tr("Move")) }
                },
                dismissButton = { TextButton(onClick = cancel) { Text(tr("Cancel")) } },
            )
        }
        is Step.ExistingData -> FractalAlertDialog(
            onDismissRequest = { step = null },
            title = { Text(tr("This folder already has Fractal data")) },
            text = { Text(tr("{0} already holds a data folder. Use it as it is (the current data stays where it is), or move the current data ({1}) there, replacing files with the same name?", s.target.absolutePath, bytes(s.size))) },
            confirmButton = { TextButton(onClick = { applyAndRestart(s.target, moveData = false) }) { Text(tr("Use it as it is")) } },
            neutralButton = { TextButton(onClick = { applyAndRestart(s.target, moveData = true) }) { Text(tr("Move the current data")) } },
            dismissButton = { TextButton(onClick = { step = null }) { Text(tr("Cancel")) } },
        )
        is Step.ConfirmDefault -> FractalAlertDialog(
            onDismissRequest = { step = null },
            title = { Text(tr("Use the default folder")) },
            text = { Text(tr("Move the data ({0}) back to {1}? Without moving, the default folder is used as it is and the data stays in the chosen folder.", bytes(s.size), s.target.absolutePath)) },
            confirmButton = { TextButton(onClick = { applyAndRestart(s.target, moveData = true) }) { Text(tr("Move")) } },
            neutralButton = { TextButton(onClick = { applyAndRestart(null, moveData = false) }) { Text(tr("Don't move")) } },
            dismissButton = { TextButton(onClick = { step = null }) { Text(tr("Cancel")) } },
        )
        is Step.Moving -> FractalAlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(tr("Moving the data")) },
            text = {
                Column {
                    Text(if (s.total > 0) tr("{0} of {1}", bytes(s.done), bytes(s.total)) else tr("Preparing…"))
                    LinearProgressIndicator(
                        progress = { if (s.total > 0) s.done.toFloat() / s.total else 0f },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {},
            hints = emptyList(),
        )
        is Step.Failed -> FractalAlertDialog(
            onDismissRequest = { step = null },
            title = { Text(tr("Couldn't move the data")) },
            text = { Text(tr("Nothing was changed: Fractal keeps using the current folder. Error: {0}", s.error)) },
            confirmButton = { TextButton(onClick = { step = null }) { Text(tr("OK")) } },
        )
    }
}
