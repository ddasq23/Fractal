/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.settings.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dev.dfd.fractal.common.settings.GuiSettings
import dev.dfd.fractal.common.ui.fractal.FractalScreen
import dev.dfd.fractal.common.ui.fractal.SectionTitle
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.WallpaperId
import dev.dfd.fractal.common.ui.theme.rememberGuiSettings
import kotlinx.coroutines.launch

@Composable
fun AppearanceScreen(navigateBack: () -> Unit) {
    val settings by rememberGuiSettings()
    val gui = settings ?: return
    val scope = rememberCoroutineScope()
    val tokens = FractalTheme.tokens
    fun update(block: (GuiSettings) -> GuiSettings) =
        scope.launch { updateGuiSettings(block) }

    FractalScreen(title = tr("Appearance"), onBack = navigateBack) {
        SectionTitle(tr("Theme"))
        ThemePicker(gui.theme) { id ->
            // a wallpaper picked for another theme is kept; "theme default" follows the new theme
            update { it.copy(theme = id) }
        }
        SectionTitle(tr("Library view"))
        LibraryViewPicker(gui.libraryView) { view -> update { it.copy(libraryView = view) } }
        SectionTitle(tr("Wallpaper"))
        WallpaperPicker(
            current = gui.wallpaper ?: tokens.defaultWallpaper,
            customImagePath = gui.customWallpaperPath,
            economy = gui.animationEconomy,
            onSelect = { id -> update { it.copy(wallpaper = id) } },
            onCustomImage = { path -> update { it.copy(wallpaper = WallpaperId.CUSTOM, customWallpaperPath = path) } },
        )
        EconomyToggle(gui.animationEconomy) { on -> update { it.copy(animationEconomy = on) } }
    }
}
