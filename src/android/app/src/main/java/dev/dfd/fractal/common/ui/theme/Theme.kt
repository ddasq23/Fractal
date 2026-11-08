/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.settings.AppSettingsStore
import dev.dfd.fractal.common.settings.GuiSettings
import kotlinx.coroutines.flow.map

val LocalFractalTokens = staticCompositionLocalOf<FractalTokens> {
    error("FractalTheme is not set")
}

object FractalTheme {
    val tokens: FractalTokens
        @Composable @ReadOnlyComposable get() = LocalFractalTokens.current
    val colors: FractalColors
        @Composable @ReadOnlyComposable get() = LocalFractalTokens.current.colors
    val shapes: FractalShapes
        @Composable @ReadOnlyComposable get() = LocalFractalTokens.current.shapes
    val surface: SurfaceStyle
        @Composable @ReadOnlyComposable get() = LocalFractalTokens.current.surface
}

/** The GUI settings as state, null until the DataStore has been read. */
@Composable
fun rememberGuiSettings(): State<GuiSettings?> {
    val flow = remember { AppSettingsStore.dataStore.data.map { it.guiSettings } }
    return flow.collectAsState(initial = null)
}

@Composable
fun fractalTokens(themeId: FractalThemeId): FractalTokens {
    if (themeId != FractalThemeId.MATERIAL_YOU)
        return staticTokensFor(themeId)
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    return materialYouTokens(scheme, dark)
}

private fun FractalTokens.toMaterialColorScheme(): ColorScheme {
    val c = colors
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    // glass themes use translucent surfaces for panels; Material components (dialogs, menus)
    // need opaque ones, so those get the theme background tinted instead
    val opaqueSurface = if (surface.kind == SurfaceKind.GLASS || surface.kind == SurfaceKind.DARK_GLASS)
        c.background else c.surface
    val opaqueSurfaceHigh = if (surface.kind == SurfaceKind.GLASS || surface.kind == SurfaceKind.DARK_GLASS)
        c.background else c.surfaceHigh
    return base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.selected,
        onPrimaryContainer = c.text,
        secondary = c.accent,
        onSecondary = c.onAccent,
        secondaryContainer = c.selected,
        onSecondaryContainer = c.text,
        tertiary = c.accent,
        onTertiary = c.onAccent,
        background = c.background,
        onBackground = c.text,
        surface = c.background,
        onSurface = c.text,
        surfaceVariant = opaqueSurfaceHigh,
        onSurfaceVariant = c.textSecondary,
        surfaceContainerLowest = c.background,
        surfaceContainerLow = opaqueSurface,
        surfaceContainer = opaqueSurface,
        surfaceContainerHigh = opaqueSurfaceHigh,
        surfaceContainerHighest = opaqueSurfaceHigh,
        surfaceBright = opaqueSurfaceHigh,
        surfaceDim = c.background,
        inverseSurface = c.text,
        inverseOnSurface = c.background,
        outline = c.border,
        outlineVariant = c.border,
    )
}

private fun FractalTokens.toMaterialShapes(): Shapes = when (shapes.card) {
    is CutCornerShape -> Shapes(
        extraSmall = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
        small = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        medium = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
        large = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
        extraLarge = CutCornerShape(topStart = 22.dp, bottomEnd = 22.dp),
    )

    else -> Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(30.dp),
    )
}

@Composable
fun FractalTheme(
    themeId: FractalThemeId,
    content: @Composable () -> Unit,
) {
    val tokens = fractalTokens(themeId)
    val colorScheme = remember(tokens) { tokens.toMaterialColorScheme() }
    val shapes = remember(tokens) { tokens.toMaterialShapes() }
    val typography = remember { fractalTypography() }
    CompositionLocalProvider(LocalFractalTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            typography = typography,
            content = content,
        )
    }
}
