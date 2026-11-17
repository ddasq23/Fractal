/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.settings.appearance

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.settings.GuiSettings
import dev.dfd.fractal.common.sound.MenuSfx
import dev.dfd.fractal.common.sound.MenuSounds
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Longest sound effect accepted: effects play on every focus move, and SoundPool keeps them decoded in memory. */
private const val MAX_SFX_MS = 5_000L

private const val SOUNDS_DIR = "sounds"
private const val MUSIC_KEY = "music"

private fun MenuSfx.label(): String = when (this) {
    MenuSfx.NAVIGATE -> tr("Moving through menus")
    MenuSfx.COVER -> tr("Moving through game covers")
    MenuSfx.SWAP -> tr("Switching theme, wallpaper or view")
    MenuSfx.CONFIRM -> tr("Opening a game, a screen or a button")
}

/**
 * The user's own library music and menu sound effects, next to the volumes. The picked files are copied into the app
 * (like a custom wallpaper), so they keep working if the original is moved or deleted. Each part has a switch to go
 * back to the Fractal sounds without losing the choice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomSoundSettings(gui: GuiSettings, update: ((GuiSettings) -> GuiSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var pickingFor by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = pickingFor
        pickingFor = null
        if (uri == null || key == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = copySound(context, uri, key, maxMs = if (key == MUSIC_KEY) null else MAX_SFX_MS)) {
                is CopyResult.Failed -> error = result.message
                is CopyResult.Copied -> {
                    error = null
                    if (key == MUSIC_KEY) update { it.copy(customMusicPath = result.path, customMusicEnabled = true) }
                    else update { it.copy(customSfxPaths = it.customSfxPaths + (key to result.path), customSfxEnabled = true) }
                }
            }
        }
    }

    fun pick(key: String) {
        pickingFor = key
        picker.launch(arrayOf("audio/*", "application/ogg"))
    }

    SoundCard {
        Text(tr("Your sounds"), style = MaterialTheme.typography.titleSmall, color = FractalTheme.colors.text)
        Text(
            tr("Use your own music and sound effects instead of the Fractal ones. MP3, OGG, M4A, WAV or FLAC; effects up to 5 seconds. The files are copied into the app."),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
        )
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FractalTheme.colors.accent) }
    }

    // music
    SettingToggle(
        tr("Use my music"),
        gui.customMusicPath?.let { tr("Plays in the library instead of the theme's track: {0}", displayName(it)) }
            ?: tr("No music chosen yet"),
        gui.customMusicEnabled && gui.customMusicPath != null,
    ) { on -> if (gui.customMusicPath == null) pick(MUSIC_KEY) else update { it.copy(customMusicEnabled = on) } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FractalButton(tr("Choose music"), onClick = { pick(MUSIC_KEY) }, icon = FractalIcons.FolderAdd)
        if (gui.customMusicPath != null) {
            FractalButton(tr("Remove"), onClick = {
                deleteSound(gui.customMusicPath)
                update { it.copy(customMusicPath = null, customMusicEnabled = false) }
            }, icon = FractalIcons.Trash)
        }
    }

    // effects
    SettingToggle(
        tr("Use my sound effects"),
        if (gui.customSfxPaths.isEmpty()) tr("Pick a sound for any of the effects below; the others keep the Fractal sound")
        else tr("Effects without a sound of yours keep the Fractal one"),
        gui.customSfxEnabled && gui.customSfxPaths.isNotEmpty(),
    ) { on -> update { it.copy(customSfxEnabled = on) } }
    for (sfx in MenuSfx.entries) {
        val path = gui.customSfxPaths[sfx.name]
        SoundCard {
            Text(sfx.label(), style = MaterialTheme.typography.titleSmall, color = FractalTheme.colors.text)
            Text(
                path?.let { displayName(it) } ?: tr("Fractal sound"),
                style = MaterialTheme.typography.bodySmall,
                color = FractalTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FractalButton(tr("Choose"), onClick = { pick(sfx.name) }, icon = FractalIcons.FolderAdd)
                FractalButton(tr("Listen"), onClick = { MenuSounds.previewCustom(sfx) }, icon = FractalIcons.Play)
                if (path != null) {
                    FractalButton(tr("Remove"), onClick = {
                        deleteSound(path)
                        update { it.copy(customSfxPaths = it.customSfxPaths - sfx.name) }
                    }, icon = FractalIcons.Trash)
                }
            }
        }
    }
}

@Composable
private fun SoundCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .fractalSurface(FractalTheme.shapes.card, FractalTheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

private sealed interface CopyResult {
    data class Copied(val path: String) : CopyResult
    data class Failed(val message: String) : CopyResult
}

/** The original file name, kept after the "<key>-<time>-" prefix of the copy. */
private fun displayName(path: String): String = File(path).name.split('-', limit = 3).getOrElse(2) { File(path).name }

private fun deleteSound(path: String?) {
    path?.let { File(it).delete() }
}

/**
 * Copies the picked file to files/sounds/<key>-<time>-<original name>, after checking that Android can play it and,
 * for effects, that it is short. The previous copy for [key] is replaced.
 */
private suspend fun copySound(context: Context, uri: Uri, key: String, maxMs: Long?): CopyResult = withContext(Dispatchers.IO) {
    val dir = File(context.filesDir, SOUNDS_DIR).apply { mkdirs() }
    val original = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "sound"
    val safeName = original.replace(Regex("[/\\\\:*?\"<>|]"), "_")
    val target = File(dir, "$key-${System.currentTimeMillis()}-$safeName")
    try {
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: return@withContext CopyResult.Failed(tr("Couldn't open the file."))
        val durationMs = MediaMetadataRetriever().run {
            try {
                setDataSource(target.absolutePath)
                if (extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == null) null
                else extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
                null
            } finally {
                release()
            }
        }
        if (durationMs == null) {
            target.delete()
            return@withContext CopyResult.Failed(tr("{0} is not an audio file Android can play.", original))
        }
        if (maxMs != null && durationMs > maxMs) {
            target.delete()
            return@withContext CopyResult.Failed(tr("{0} is {1} seconds long: sound effects can be up to 5 seconds.", original, durationMs / 1000))
        }
        dir.listFiles()?.filter { it.name.startsWith("$key-") && it != target }?.forEach { it.delete() }
        CopyResult.Copied(target.absolutePath)
    } catch (e: Exception) {
        target.delete()
        CopyResult.Failed(e.message ?: e.javaClass.simpleName)
    }
}
