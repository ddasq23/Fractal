/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.welcome

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dfd.fractal.BuildConfig
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.fractal.FractalIconButton
import dev.dfd.fractal.common.ui.fractal.FractalWordmark
import dev.dfd.fractal.common.ui.fractal.SectionTitle
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.WallpaperId
import dev.dfd.fractal.common.ui.theme.fractalSurface
import dev.dfd.fractal.common.ui.theme.rememberGuiSettings
import dev.dfd.fractal.common.ui.theme.themedPanel
import dev.dfd.fractal.settings.appearance.EconomyToggle
import dev.dfd.fractal.settings.appearance.LibraryViewPicker
import dev.dfd.fractal.settings.appearance.ThemePicker
import dev.dfd.fractal.settings.appearance.WallpaperPicker
import dev.dfd.fractal.settings.appearance.updateGuiSettings
import dev.dfd.fractal.settings.gamespath.GamesPathsViewModel
import dev.dfd.fractal.settings.general.GeneralSettingsViewModel
import kotlinx.coroutines.launch

private enum class Step { LANGUAGE, APPEARANCE, GAMES, KEYS, GRAPHIC_PACKS, DRIVER, DONE }

class WelcomeNavigation(
    val finish: () -> Unit,
    val goToGraphicPacks: () -> Unit,
    val goToCustomDrivers: () -> Unit,
)

@Composable
fun WelcomeScreen(navigation: WelcomeNavigation) {
    val scope = rememberCoroutineScope()
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = Step.entries[stepIndex]

    fun finish() = scope.launch {
        updateGuiSettings { it.copy(onboardingCompleted = true) }
        navigation.finish()
    }

    fun next() {
        if (step == Step.DONE) finish() else stepIndex++
    }

    BackHandler(enabled = stepIndex > 0) { stepIndex-- }

    // handhelds are short and wide (Odin 2 Mini: ~830x420 dp) or square (RG Cube): keep it compact
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // left: brand and progress (hidden on narrow screens)
        if (wide) {
            Column(Modifier.width(190.dp).padding(top = 6.dp, start = 6.dp)) {
                FractalWordmark()
                Spacer(Modifier.height(20.dp))
                Step.entries.forEachIndexed { index, s ->
                    StepMarker(stepTitle(s), index = index, current = stepIndex)
                }
            }
        }
        // right: the step
        Column(Modifier.weight(1f).fillMaxSize().themedPanel().padding(horizontal = 20.dp, vertical = 14.dp)) {
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "welcome step",
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (wide) stepTitle(s) else "${stepIndex + 1}/${Step.entries.size}  ${stepTitle(s)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = FractalTheme.colors.text,
                    )
                    when (s) {
                        Step.LANGUAGE -> LanguageStep()
                        Step.APPEARANCE -> AppearanceStep()
                        Step.GAMES -> GamesStep()
                        Step.KEYS -> KeysStep()
                        Step.GRAPHIC_PACKS -> GraphicPacksStep(navigation.goToGraphicPacks)
                        Step.DRIVER -> DriverStep(navigation.goToCustomDrivers)
                        Step.DONE -> DoneStep()
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (stepIndex > 0) FractalIconButton(FractalIcons.Back, tr("Back"), { stepIndex-- })
                Spacer(Modifier.weight(1f))
                if (step != Step.DONE) {
                    FractalButton(tr("Skip setup"), onClick = { finish() })
                    FractalButton(tr("Skip"), onClick = { next() })
                }
                FractalButton(
                    if (step == Step.DONE) tr("Go to library") else tr("Next"),
                    onClick = { next() },
                    icon = if (step == Step.DONE) FractalIcons.Check else FractalIcons.Next,
                    primary = true,
                )
            }
        }
    }
}

private fun stepTitle(step: Step) = when (step) {
    Step.LANGUAGE -> tr("Language")
    Step.APPEARANCE -> tr("Theme and wallpaper")
    Step.GAMES -> tr("Game folder")
    Step.KEYS -> tr("Keys and files")
    Step.GRAPHIC_PACKS -> tr("Graphic packs")
    Step.DRIVER -> tr("GPU driver")
    Step.DONE -> tr("Ready")
}

@Composable
private fun StepMarker(title: String, index: Int, current: Int) {
    val colors = FractalTheme.colors
    val active = index == current
    val done = index < current
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 3.dp, height = 18.dp)
                .background(if (active) colors.accent else if (done) colors.textSecondary else colors.border)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = if (active) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            color = if (active) colors.text else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = FractalTheme.colors.textSecondary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageStep(viewModel: GeneralSettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val gui by viewModel.guiSettings.collectAsState()
    Body(tr("Choose the language of the app. You can change it later in the settings."))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        viewModel.languages.forEach { language ->
            val selected = language == gui.language
            FractalButton(
                text = viewModel.languageToDisplayNameMap[language] ?: language,
                onClick = { viewModel.setLanguage(language = language, context) },
                icon = if (selected) FractalIcons.Check else null,
                primary = selected,
            )
        }
    }
}

@Composable
private fun AppearanceStep() {
    val settings by rememberGuiSettings()
    val gui = settings ?: return
    val scope = rememberCoroutineScope()
    val tokens = FractalTheme.tokens
    Body(tr("Changes apply right away, so this screen is the preview."))
    SectionTitle(tr("Theme"))
    ThemePicker(gui.theme) { id -> scope.launch { updateGuiSettings { it.copy(theme = id) } } }
    SectionTitle(tr("Wallpaper"))
    WallpaperPicker(
        current = gui.wallpaper ?: tokens.defaultWallpaper,
        customImagePath = gui.customWallpaperPath,
        economy = gui.animationEconomy,
        onSelect = { id -> scope.launch { updateGuiSettings { it.copy(wallpaper = id) } } },
        onCustomImage = { path ->
            scope.launch { updateGuiSettings { it.copy(wallpaper = WallpaperId.CUSTOM, customWallpaperPath = path) } }
        },
    )
    EconomyToggle(gui.animationEconomy) { on -> scope.launch { updateGuiSettings { it.copy(animationEconomy = on) } } }
    SectionTitle(tr("Library view"))
    LibraryViewPicker(gui.libraryView) { view -> scope.launch { updateGuiSettings { it.copy(libraryView = view) } } }
}

@Composable
private fun GamesStep(viewModel: GamesPathsViewModel = viewModel()) {
    val context = LocalContext.current
    val paths by viewModel.gamesPaths.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val path = DocumentFile.fromTreeUri(context, uri)?.uri?.toString() ?: return@rememberLauncherForActivityResult
        if (path !in paths) viewModel.addGamesPath(path)
    }
    Body(
        tr("Pick the folder where your Wii U games are (.wua, .wux, .wud, .iso, .rpx). Fractal also looks in the folders inside it.")
    )
    paths.forEach { path ->
        Row(
            Modifier
                .fillMaxWidth()
                .fractalSurface(FractalTheme.shapes.card, FractalTheme.surface, FractalTheme.colors.surfaceHigh)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(FractalIcons.Folder, null, tint = FractalTheme.colors.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                readablePath(path),
                style = MaterialTheme.typography.bodyMedium,
                color = FractalTheme.colors.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FractalIconButton(FractalIcons.Trash, tr("Remove"), { viewModel.removeGamesPath(path) })
        }
    }
    FractalButton(tr("Add game folder"), onClick = { launcher.launch(null) }, icon = FractalIcons.FolderAdd, primary = paths.isEmpty())
}

/** "content://…/tree/primary%3AWII%20U" -> "WII U" */
private fun readablePath(path: String): String =
    android.net.Uri.decode(path).substringAfterLast(':').ifBlank { path }

@Composable
private fun KeysStep() {
    val dataFolder = "Android/data/${BuildConfig.APPLICATION_ID}/files"
    Body(
        tr("Fractal does not download keys, firmware or games. Decrypted games (.wua, or folders with code/content/meta) work without keys.")
    )
    InfoPanel {
        InfoLine(tr("Encrypted games (.wud, .wux)"), tr("put keys.txt in the Fractal folder"))
        InfoLine(tr("Online features"), tr("put otp.bin and seeprom.bin from your console in the Fractal folder"))
        InfoLine(tr("Fractal folder"), dataFolder)
    }
    Body(tr("You can open the Fractal folder from the library options (Start)."))
}

@Composable
private fun GraphicPacksStep(goToGraphicPacks: () -> Unit) {
    Body(
        tr("Graphic packs change resolution, fix issues and add enhancements per game. They are optional; you can download the community collection from the graphic packs screen.")
    )
    FractalButton(tr("Open graphic packs"), onClick = goToGraphicPacks, icon = FractalIcons.Layers)
}

@Composable
private fun DriverStep(goToCustomDrivers: () -> Unit) {
    Body(
        tr("The system driver works on every device. A recommended driver per GPU will be suggested here once testing is done.")
    )
    InfoPanel {
        InfoLine(tr("Current"), tr("System driver"))
        InfoLine(tr("Recommendation"), tr("coming after device tests"))
    }
    FractalButton(tr("Custom drivers"), onClick = goToCustomDrivers, icon = FractalIcons.Chip)
}

@Composable
private fun DoneStep() {
    Body(tr("All set. You can reopen this setup any time from the library options or the settings."))
    Body(
        tr("Gamepad: A plays, Y opens the game's settings, X opens the settings, Start opens the options, L1/R1 change the library view.")
    )
}

@Composable
private fun InfoPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(max = 720.dp)
            .clip(FractalTheme.shapes.card)
            .fractalSurface(FractalTheme.shapes.card, FractalTheme.surface, FractalTheme.colors.surfaceHigh)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = FractalTheme.colors.textSecondary, modifier = Modifier.width(200.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = FractalTheme.colors.text)
    }
}
