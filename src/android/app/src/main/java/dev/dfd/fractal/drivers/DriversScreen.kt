/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.drivers

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dfd.fractal.common.gpu.Gpu
import dev.dfd.fractal.common.gpu.GpuVendor
import dev.dfd.fractal.common.ui.fractal.ButtonHint
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.fractal.PadButton
import dev.dfd.fractal.common.ui.fractal.FractalIconButton
import dev.dfd.fractal.common.ui.fractal.FractalScreen
import dev.dfd.fractal.common.ui.fractal.SectionTitle
import dev.dfd.fractal.common.ui.fractal.StatusChip
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface

/** Releases shown per repository: the most recent ones, and up to [RELEASES_PER_SOURCE] when expanded. */
private const val RECENT_RELEASES = 2
private const val RELEASES_PER_SOURCE = 8

@Composable
fun DriversScreen(navigateBack: () -> Unit, viewModel: DriversViewModel = viewModel()) {
    val gpu by viewModel.gpu.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val global by viewModel.globalDriver.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFromFile)
    }

    FractalScreen(
        title = tr("Drivers"),
        onBack = navigateBack,
        extraHints = buildList {
            if (viewModel.supportsCustomDrivers) add(ButtonHint(PadButton.X, tr("Import")) {
                importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            })
            if (viewModel.visibleSources(gpu).isNotEmpty()) add(ButtonHint(PadButton.Y, tr("Refresh")) { viewModel.refresh() })
        },
    ) {
        GpuCard(gpu, viewModel.supportsCustomDrivers)
        message?.let { Banner(it) }

        SectionTitle(tr("Installed"))
        DriverRow(
            title = tr("System driver"),
            detail = tr("The driver that came with the device"),
            inUse = global == null,
            onUse = { viewModel.select(null) },
        )
        installed.forEach { item ->
            val meta = item.driver.metadata
            DriverRow(
                title = meta.name,
                detail = listOfNotNull(
                    meta.driverVersion.takeIf { it.isNotBlank() },
                    item.source?.let { "${it.owner}/${it.repo} · ${it.tag}" } ?: tr("Imported from file"),
                ).joinToString("  ·  "),
                inUse = global == item.driver.path,
                onUse = { viewModel.select(item.driver.path) },
                onDelete = { viewModel.delete(item) },
            )
        }
        FractalButton(
            tr("Import from file"),
            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            icon = FractalIcons.FolderAdd,
            enabled = viewModel.supportsCustomDrivers,
        )

        val visible = viewModel.visibleSources(gpu)
        if (visible.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle(tr("Available")) }
                FractalButton(tr("Refresh"), onClick = { viewModel.refresh() }, icon = FractalIcons.Refresh)
            }
            visible.forEach { source ->
                SourceSection(
                    source = source,
                    state = sources[source.key] ?: SourceState(loading = true),
                    gpu = gpu!!,
                    installed = installed,
                    downloads = downloads,
                    onInstall = viewModel::install,
                    onUse = { viewModel.select(it) },
                    globalDriver = global,
                )
            }
        }
    }
}

@Composable
private fun GpuCard(gpu: Gpu?, supportsCustomDrivers: Boolean) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FractalIcons.Chip, null, tint = FractalTheme.colors.accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(gpu?.displayName ?: tr("Detecting GPU…"), style = MaterialTheme.typography.titleMedium, color = FractalTheme.colors.text)
                Text(
                    when {
                        gpu == null -> ""
                        supportsCustomDrivers && gpu.vendor == GpuVendor.ADRENO ->
                            tr("Turnip (Mesa) drivers for this GPU are listed below")
                        supportsCustomDrivers && gpu.vendor == GpuVendor.MALI ->
                            tr("Experimental: PanVK and malisx2 drivers for this GPU are listed below. If a driver fails, Fractal goes back to the system driver.")
                        else -> tr("Custom drivers are not supported on this GPU; the system driver is used")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = FractalTheme.colors.textSecondary,
                )
            }
            if (supportsCustomDrivers && gpu?.vendor == GpuVendor.MALI) {
                StatusChip(tr("Experimental"))
                Spacer(Modifier.width(8.dp))
            }
            gpu?.renderer?.takeIf { it.isNotBlank() }?.let { StatusChip(it) }
        }
    }
}

@Composable
private fun Banner(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .fractalSurface(FractalTheme.shapes.card, FractalTheme.surface, FractalTheme.colors.accent.copy(alpha = 0.18f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(FractalIcons.Info, null, tint = FractalTheme.colors.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = FractalTheme.colors.text)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .fractalSurface(FractalTheme.shapes.card, FractalTheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun DriverRow(
    title: String,
    detail: String,
    inUse: Boolean,
    onUse: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val colors = FractalTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(FractalTheme.shapes.card)
            .fractalSurface(FractalTheme.shapes.card, FractalTheme.surface, if (inUse) colors.selected else null),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (inUse) colors.accent else colors.border.copy(alpha = 0f)))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (inUse) StatusChip(tr("In use"), icon = FractalIcons.Check, highlight = true)
        else FractalButton(tr("Use"), onClick = onUse)
        if (onDelete != null) {
            Spacer(Modifier.width(6.dp))
            FractalIconButton(FractalIcons.Trash, tr("Delete"), onDelete)
        }
        Spacer(Modifier.width(10.dp))
    }
}

@Composable
private fun SourceSection(
    source: DriverSource,
    state: SourceState,
    gpu: Gpu,
    installed: List<InstalledDriver>,
    downloads: Map<String, Float>,
    globalDriver: String?,
    onInstall: (DriverSource, GhRelease, GhAsset) -> Unit,
    onUse: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var showOlder by rememberSaveable(source.key) { mutableStateOf(false) }
    val compatible = remember(state.releases, gpu) {
        state.releases
            .mapNotNull { r -> r.assets.filter { source.accepts(it.name, gpu) }.takeIf { it.isNotEmpty() }?.let { r to it } }
            .take(RELEASES_PER_SOURCE)
    }
    val entries = if (showOlder) compatible else compatible.take(RECENT_RELEASES)
    Panel {
        // credit: repository name and link
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(source.key, style = MaterialTheme.typography.titleMedium, color = FractalTheme.colors.text)
                    if (source.experimental) {
                        Spacer(Modifier.width(8.dp))
                        StatusChip(tr("Experimental"))
                    }
                }
                Text(
                    "${tr(source.description)} · ${source.url}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FractalTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                source.note?.let {
                    Text(tr(it), style = MaterialTheme.typography.bodySmall, color = FractalTheme.colors.accent)
                }
            }
            FractalIconButton(FractalIcons.Globe, tr("Open repository"), { uriHandler.openUri(source.url) })
        }
        val status = when {
            state.loading && entries.isEmpty() -> tr("Loading releases…")
            state.error != null && entries.isEmpty() -> tr("Could not load releases: {0}", state.error)
            state.error != null -> tr("Offline, showing the saved list ({0})", state.error)
            entries.isEmpty() -> tr("No compatible releases")
            else -> state.fetchedAt?.let {
                if (System.currentTimeMillis() - it < DateUtils.MINUTE_IN_MILLIS) tr("Updated just now")
                else tr("Updated {0}", DateUtils.getRelativeTimeSpanString(it).toString())
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = FractalTheme.colors.textSecondary) }
        entries.forEach { (release, assets) ->
            assets.forEach { asset ->
                val match = installed.firstOrNull {
                    it.source?.owner == source.owner && it.source.repo == source.repo &&
                        it.source.tag == release.tag && it.source.asset == asset.name
                }
                AssetRow(
                    release = release,
                    asset = asset,
                    progress = downloads[asset.downloadUrl],
                    installedPath = match?.driver?.path,
                    inUse = match != null && match.driver.path == globalDriver,
                    onInstall = { onInstall(source, release, asset) },
                    onUse = onUse,
                )
            }
        }
        if (compatible.size > RECENT_RELEASES) {
            FractalButton(
                if (showOlder) tr("Show fewer versions") else tr("Older versions ({0})", compatible.size - RECENT_RELEASES),
                onClick = { showOlder = !showOlder },
            )
        }
    }
}

@Composable
private fun AssetRow(
    release: GhRelease,
    asset: GhAsset,
    progress: Float?,
    installedPath: String?,
    inUse: Boolean,
    onInstall: () -> Unit,
    onUse: (String) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    asset.name.removeSuffix(".zip"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FractalTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        release.tag,
                        release.publishedAt?.take(10),
                        if (release.prerelease) tr("pre-release") else null,
                        "%.1f MB".format(asset.size / 1_048_576f),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = FractalTheme.colors.textSecondary,
                )
            }
            when {
                progress != null -> Text(
                    if (progress >= 0f) "${(progress * 100).toInt()}%" else tr("Downloading…"),
                    style = MaterialTheme.typography.labelLarge,
                    color = FractalTheme.colors.accent,
                )
                inUse -> StatusChip(tr("In use"), icon = FractalIcons.Check, highlight = true)
                installedPath != null -> FractalButton(tr("Use"), onClick = { onUse(installedPath) })
                else -> FractalButton(tr("Install"), onClick = onInstall, icon = FractalIcons.Download)
            }
        }
        if (progress != null) {
            if (progress >= 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

