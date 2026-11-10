/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.games.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.games.GameIcons
import dev.dfd.fractal.graphicpacks.GraphicPacksDownloadStatus

/** What the graphic pack rows can change. */
class PackActions(
    val setActive: (id: Long, active: Boolean) -> Unit,
    /** [enable] also turns the pack on, for one-tap choices. */
    val setPreset: (id: Long, category: String?, choice: String, enable: Boolean) -> Unit,
    val download: () -> Unit,
    val openBrowser: () -> Unit,
)

// region recommended packs

private val BotwTitleIds = setOf(0x00050000101C9300L, 0x00050000101C9400L, 0x00050000101C9500L)

/** Games with a recommended packs card. */
fun hasRecommendedPacks(titleId: Long) = titleId in BotwTitleIds

private fun List<GamePack>.fpsPlusPlus() =
    firstOrNull { it.path.endsWith("/Mods/FPS++", ignoreCase = true) } ?: firstOrNull { it.name.contains("FPS++", ignoreCase = true) }

/** The game's main "Graphics" pack (resolution, shadows...), "<game>/Graphics". */
private fun List<GamePack>.mainGraphicsPack() =
    firstOrNull { it.path.split('/').let { s -> s.size == 2 && s[1].equals("Graphics", ignoreCase = true) } }

private const val OFF = "\u0000off"

/**
 * Breath of the Wild: FPS++ removes the game's double-buffered vsync lock (on the RP5 11.8 -> 21 fps;
 * the lock is what holds it at 20 fps on an Odin 2 Mini), with a 30/60 FPS limit, plus the Graphics
 * pack's resolution.
 */
@Composable
fun RecommendedPacksCard(packs: List<GamePack>?, downloading: Boolean, actions: PackActions) {
    SettingPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(GameIcons.Bolt, null, tint = FractalTheme.colors.accent, modifier = Modifier.size(22.dp))
            Text(
                tr("Recommended for this game"),
                style = MaterialTheme.typography.titleMedium,
                color = FractalTheme.colors.text,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        if (packs == null) {
            HintText(tr("Loading graphic packs…"))
            return@SettingPanel
        }
        val fps = packs.fpsPlusPlus()
        val graphics = packs.mainGraphicsPack()
        if (fps == null || graphics == null) {
            HintText(
                tr("The recommended packs (FPS++ and Graphics) are not in your graphic packs yet. Download the community graphic packs to get them.")
            )
            FractalButton(
                if (downloading) tr("Downloading…") else tr("Download graphic packs"),
                onClick = actions.download,
                icon = FractalIcons.Download,
                primary = true,
                enabled = !downloading,
            )
        }
        if (fps != null) {
            ToggleRow(
                title = tr("FPS++ (recommended)"),
                description = tr("Unlocks the game's vsync lock. Without it the game waits for a full extra frame whenever a frame takes too long and gets stuck at 20 fps."),
                checked = fps.active,
                onChange = { actions.setActive(fps.id, it) },
            )
            val limit = fps.presets.firstOrNull { it.category?.contains("FPS Limit", ignoreCase = true) == true }
            val choice30 = limit?.choices?.firstOrNull { it.trimStart().startsWith("30") }
            val choice60 = limit?.choices?.firstOrNull { it.trimStart().startsWith("60") }
            if (limit != null && (choice30 != null || choice60 != null)) {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("FPS limit"), style = MaterialTheme.typography.titleSmall, color = FractalTheme.colors.text)
                    SegmentedChoice(
                        choices = listOfNotNull(choice30, choice60),
                        selected = limit.activeChoice.takeIf { fps.active },
                        label = { if (it == choice30) tr("30 FPS") else tr("60 FPS") },
                        onSelect = { actions.setPreset(fps.id, limit.category, it, true) },
                    )
                    HintText(tr("30 FPS gives steadier frame pacing when the device can't hold 60. Picking a limit enables FPS++."))
                }
            }
        }
        if (graphics != null) {
            val resolution = graphics.presets.firstOrNull { it.category.equals("Resolution", ignoreCase = true) }
            if (resolution != null) {
                ChoiceRow(
                    title = tr("Resolution"),
                    description = tr("From the Graphics pack. Lower resolutions are faster"),
                    value = if (graphics.active) resolution.activeChoice else OFF,
                    choices = listOf(OFF) + resolution.choices,
                    label = { if (it == OFF) tr("Off (game default)") else it },
                    onSelect = {
                        if (it == OFF) actions.setActive(graphics.id, false)
                        else actions.setPreset(graphics.id, resolution.category, it, true)
                    },
                )
            }
        }
    }
}

// endregion

// region packs for this game

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamePacksSection(
    packs: List<GamePack>?,
    packsDownloaded: Boolean,
    downloading: Boolean,
    actions: PackActions,
) {
    when {
        packs == null -> HintText(tr("Loading graphic packs…"))
        packs.isEmpty() -> HintText(
            if (packsDownloaded) tr("There are no graphic packs for this game.")
            else tr("Graphic packs are not downloaded yet. The community packs add resolution, frame rate and other options for most games.")
        )

        else -> {
            var showAll by rememberSaveable { mutableStateOf(false) }
            val enabled = packs.filter { it.active }
            val shown = if (showAll) packs else enabled
            if (enabled.isEmpty() && !showAll) HintText(tr("No graphic pack is enabled for this game."))
            shown.groupBy { it.group }.forEach { (group, groupPacks) ->
                if (group.isNotEmpty() && showAll) {
                    Text(
                        group,
                        style = MaterialTheme.typography.labelMedium,
                        color = FractalTheme.colors.textSecondary,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
                groupPacks.forEach { PackItem(it, actions, showGroup = !showAll) }
            }
            if (packs.size > enabled.size || showAll) {
                FractalButton(
                    if (showAll) tr("Show only enabled packs") else tr("Show all {0} packs", packs.size),
                    onClick = { showAll = !showAll },
                    icon = FractalIcons.Layers,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FractalButton(
            when {
                downloading -> tr("Downloading…")
                packsDownloaded -> tr("Update graphic packs")
                else -> tr("Download graphic packs")
            },
            onClick = actions.download,
            icon = FractalIcons.Download,
            primary = !packsDownloaded,
            enabled = !downloading,
        )
        FractalButton(tr("All graphic packs"), onClick = actions.openBrowser, icon = FractalIcons.Layers)
    }
}

@Composable
private fun PackItem(pack: GamePack, actions: PackActions, showGroup: Boolean) {
    val summary = pack.description.replace("\\n", " ").replace('\n', ' ').trim().let {
        if (it.length > 160) it.take(157).trimEnd() + "…" else it
    }
    val title = if (showGroup && pack.group.isNotEmpty()) "${pack.name}  ·  ${pack.group}" else pack.name
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ToggleRow(title = title, description = summary, checked = pack.active, onChange = { actions.setActive(pack.id, it) })
        if (pack.active) {
            pack.presets.forEach { preset ->
                Row(Modifier.padding(start = 20.dp)) {
                    ChoiceRow(
                        title = preset.category ?: tr("Preset"),
                        description = null,
                        value = preset.activeChoice,
                        choices = preset.choices,
                        label = { it },
                        onSelect = { actions.setPreset(pack.id, preset.category, it, false) },
                    )
                }
            }
        }
    }
}

// endregion

/** Progress while the community graphic packs download; [onCancel] stops it. */
@Composable
fun PacksDownloadDialog(status: GraphicPacksDownloadStatus, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(tr("Graphic packs download")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when (status) {
                        GraphicPacksDownloadStatus.CHECKING_VERSION -> tr("Checking version...")
                        GraphicPacksDownloadStatus.EXTRACTING -> tr("Extracting...")
                        else -> tr("Downloading graphic packs...")
                    }
                )
                LinearProgressIndicator(color = FractalTheme.colors.accent, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(tr("Cancel")) } },
    )
}
