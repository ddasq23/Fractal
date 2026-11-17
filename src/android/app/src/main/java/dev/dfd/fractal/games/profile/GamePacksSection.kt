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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.dfd.fractal.common.ui.fractal.PadButton
import dev.dfd.fractal.common.ui.fractal.ButtonHint
import dev.dfd.fractal.common.ui.components.FractalAlertDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.fractal.SectionTitle
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
    /** An option of a resolution / FPS limit setting, null for Off. */
    val select: (setting: PackSetting, option: PackOption?) -> Unit,
    val download: () -> Unit,
    /** Opens the browser of all packs; null hides its button. */
    val openBrowser: (() -> Unit)?,
)

// region resolution & FPS limit

private val BotwTitleIds = setOf(0x00050000101C9300L, 0x00050000101C9400L, 0x00050000101C9500L)

/** Games with a recommended packs card (it holds their resolution and FPS limit rows too). */
fun hasRecommendedPacks(titleId: Long) = titleId in BotwTitleIds

private fun List<GamePack>.fpsPlusPlus() =
    firstOrNull { it.path.endsWith("/Mods/FPS++", ignoreCase = true) } ?: firstOrNull { it.name.contains("FPS++", ignoreCase = true) }

/** Off, then the setting's options; picking one enables its pack. */
@Composable
private fun PackSettingRow(title: String, description: String, setting: PackSetting, actions: PackActions) {
    ChoiceRow(
        title = title,
        description = description,
        value = setting.selected,
        choices = listOf<PackOption?>(null) + setting.options,
        label = { it?.label ?: tr("Off (game default)") },
        onSelect = { actions.select(setting, it) },
    )
}

@Composable
private fun ResolutionRow(setting: PackSetting, actions: PackActions) = PackSettingRow(
    title = tr("Resolution"),
    description = tr("From the {0} pack. Lower resolutions are faster", setting.mainPack.name),
    setting = setting,
    actions = actions,
)

@Composable
private fun FpsLimitRow(setting: PackSetting, actions: PackActions) {
    val description = tr(
        "From the {0} pack, which changes the timing of the game itself so it keeps its normal speed. Pick a rate the device can hold: with some packs the game slows down below it.",
        setting.mainPack.name,
    )
    if (setting.options.size > 2) {
        PackSettingRow(tr("FPS limit"), description, setting, actions)
        return
    }
    // "Off" and one or two packs ("60FPS"): buttons, like the recommended card
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("FPS limit"), style = MaterialTheme.typography.titleSmall, color = FractalTheme.colors.text)
        SegmentedChoice(
            choices = listOf<PackOption?>(null) + setting.options,
            selected = setting.selected,
            label = { it?.label ?: tr("Off (game default)") },
            onSelect = { actions.select(setting, it) },
        )
        HintText(description)
    }
}

/**
 * Resolution and FPS limit near the top of the game's settings, for any game whose graphic packs have
 * them ([resolutionSetting], [fpsSetting]). Nothing when they don't; a download row while the community
 * packs aren't downloaded.
 */
@Composable
fun GamePackSettings(packs: List<GamePack>?, packsDownloaded: Boolean, downloading: Boolean, actions: PackActions) {
    if (packs == null) return
    val resolution = remember(packs) { packs.resolutionSetting() }
    val fps = remember(packs) { packs.fpsSetting() }
    if (resolution == null && fps == null) {
        if (packsDownloaded) return
        SectionTitle(tr("Resolution & FPS"))
        ActionRow(
            title = if (downloading) tr("Downloading…") else tr("Download graphic packs"),
            description = tr("The community graphic packs add resolution and FPS options for most games"),
            icon = FractalIcons.Download,
            onClick = actions.download,
            enabled = !downloading,
        )
        return
    }
    SectionTitle(tr("Resolution & FPS"))
    SettingPanel {
        resolution?.let { ResolutionRow(it, actions) }
        fps?.let { FpsLimitRow(it, actions) }
    }
}

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
        val resolution = remember(packs) { packs.resolutionSetting() }
        if (fps == null || resolution == null) {
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
            // FPS++ goes from 240 down to 20: the card offers the two that make sense on a handheld
            val limit = remember(packs) { packs.fpsSetting() }?.takeIf { it.mainPack.id == fps.id }
            val choice30 = limit?.options?.firstOrNull { it.packId == fps.id && it.label.trimStart().startsWith("30") }
            val choice60 = limit?.options?.firstOrNull { it.packId == fps.id && it.label.trimStart().startsWith("60") }
            if (limit != null && (choice30 != null || choice60 != null)) {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("FPS limit"), style = MaterialTheme.typography.titleSmall, color = FractalTheme.colors.text)
                    SegmentedChoice(
                        choices = listOfNotNull(choice30, choice60),
                        selected = limit.selected,
                        label = { if (it == choice30) tr("30 FPS") else tr("60 FPS") },
                        onSelect = { actions.select(limit, it) },
                    )
                    HintText(tr("30 FPS gives steadier frame pacing when the device can't hold 60. Picking a limit enables FPS++."))
                }
            }
        }
        if (resolution != null) ResolutionRow(resolution, actions)
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
    /** List every pack (cheats and mods too) instead of only the enabled ones. */
    startWithAll: Boolean = false,
) {
    when {
        packs == null -> HintText(tr("Loading graphic packs…"))
        packs.isEmpty() -> HintText(
            if (packsDownloaded) tr("There are no graphic packs for this game.")
            else tr("Graphic packs are not downloaded yet. The community packs add resolution, frame rate and other options for most games.")
        )

        else -> {
            var showAll by rememberSaveable { mutableStateOf(startWithAll) }
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
        actions.openBrowser?.let { FractalButton(tr("All graphic packs"), onClick = it, icon = FractalIcons.Layers) }
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
    FractalAlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        hints = listOf(ButtonHint(PadButton.A, tr("Cancel")), ButtonHint(PadButton.B, tr("Cancel"))),
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
        confirmButton = { TextButton(onClick = onCancel) { Text(tr("Cancel")) } },
    )
}
