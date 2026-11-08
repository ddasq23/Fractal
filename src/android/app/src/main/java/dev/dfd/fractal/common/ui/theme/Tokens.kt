/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable

/** Themes the user can pick on the welcome screen and in the appearance settings. */
@Serializable
enum class FractalThemeId {
    SOLID,
    LIQUID_GLASS,
    BLUR,
    MATERIAL_YOU,
    SYNTHWAVE,
}

@Immutable
data class FractalColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val selected: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val textSecondary: Color,
    val border: Color,
    val focusRing: Color,
    /** Optional gradient used for accent fills (buttons, selection bars). Null means solid [accent]. */
    val accentBrush: Brush? = null,
    /** Optional gradient painted behind everything when there is no wallpaper. */
    val backgroundBrush: Brush? = null,
)

@Immutable
data class FractalShapes(
    val button: Shape,
    val card: Shape,
    val cover: Shape,
    val chip: Shape,
    val panel: Shape,
)

/** How panels, cards and bars are painted. */
enum class SurfaceKind {
    /** Opaque fill. */
    SOLID,

    /** Light translucent glass with a blurred backdrop, border and inner top highlight. */
    GLASS,

    /** Dark translucent glass, used on top of blurred game art. */
    DARK_GLASS,

    /** Material tonal surfaces. */
    TONAL,
}

@Immutable
data class SurfaceStyle(
    val kind: SurfaceKind,
    /** Fill used for panels: opaque for SOLID/TONAL, translucent for glass kinds. */
    val fill: Color,
    val borderColor: Color = Color.Transparent,
    val borderWidth: Dp = 0.dp,
    /** Backdrop blur radius for glass kinds (API 31+); 0 disables it. */
    val backdropBlur: Dp = 0.dp,
    /** Inner highlight along the top edge of glass panels. */
    val topHighlight: Color = Color.Transparent,
)

/** What sits behind the screens. */
enum class BackgroundKind {
    /** The selected wallpaper (static, animated or the user's image). */
    WALLPAPER,

    /** Art of the focused game, heavily blurred, with a dark gradient on the left. */
    GAME_ART,
}

/** How the library's top level navigation is drawn. */
enum class NavStyle {
    /** Flat bar along the top edge. */
    TOP_BAR,

    /** Floating pill-shaped bar. */
    FLOATING_PILL,

    /** Side navigation rail with a floating action button. */
    RAIL,
}

@Immutable
data class FractalTokens(
    val id: FractalThemeId,
    val isDark: Boolean,
    val colors: FractalColors,
    val shapes: FractalShapes,
    val surface: SurfaceStyle,
    val background: BackgroundKind,
    val nav: NavStyle,
    /** Wallpaper shown when the user has not picked one. */
    val defaultWallpaper: WallpaperId,
)

private val chamfered = FractalShapes(
    button = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
    card = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp),
    cover = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
    chip = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
    panel = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp),
)

private val rounded = FractalShapes(
    button = RoundedCornerShape(50),
    card = RoundedCornerShape(20.dp),
    cover = RoundedCornerShape(16.dp),
    chip = RoundedCornerShape(50),
    panel = RoundedCornerShape(28.dp),
)

private val roundedMaterial = FractalShapes(
    button = RoundedCornerShape(50),
    card = RoundedCornerShape(16.dp),
    cover = RoundedCornerShape(12.dp),
    chip = RoundedCornerShape(8.dp),
    panel = RoundedCornerShape(24.dp),
)

object FractalPalette {
    val Ink = Color(0xFF100F16)
    val Surface = Color(0xFF17151F)
    val SurfaceHigh = Color(0xFF1C1A26)
    val Selected = Color(0xFF24202E)
    val Magenta = Color(0xFFFF2D95)
    val Purple = Color(0xFF6A3CFF)
    val Cyan = Color(0xFF00C8FF)
    val Night = Color(0xFF0B0820)
    val Text = Color(0xFFF2F0F7)
    val TextSecondary = Color(0xFFB9B5C8)
}

private val solidTokens = FractalTokens(
    id = FractalThemeId.SOLID,
    isDark = true,
    colors = FractalColors(
        background = FractalPalette.Ink,
        surface = FractalPalette.Surface,
        surfaceHigh = FractalPalette.SurfaceHigh,
        selected = FractalPalette.Selected,
        accent = FractalPalette.Magenta,
        onAccent = Color.White,
        text = FractalPalette.Text,
        textSecondary = FractalPalette.TextSecondary,
        border = Color(0xFF2C2838),
        focusRing = Color.White,
    ),
    shapes = chamfered,
    surface = SurfaceStyle(kind = SurfaceKind.SOLID, fill = FractalPalette.Surface),
    background = BackgroundKind.WALLPAPER,
    nav = NavStyle.TOP_BAR,
    defaultWallpaper = WallpaperId.STATIC_INK,
)

private val liquidGlassTokens = FractalTokens(
    id = FractalThemeId.LIQUID_GLASS,
    isDark = true,
    colors = FractalColors(
        background = Color(0xFF14121C),
        surface = Color.White.copy(alpha = 0.11f),
        surfaceHigh = Color.White.copy(alpha = 0.16f),
        selected = Color.White.copy(alpha = 0.22f),
        accent = FractalPalette.Magenta,
        onAccent = Color.White,
        text = Color.White,
        textSecondary = Color.White.copy(alpha = 0.72f),
        border = Color.White.copy(alpha = 0.24f),
        focusRing = Color.White,
    ),
    shapes = rounded,
    surface = SurfaceStyle(
        kind = SurfaceKind.GLASS,
        fill = Color.White.copy(alpha = 0.11f),
        borderColor = Color.White.copy(alpha = 0.24f),
        borderWidth = 1.dp,
        backdropBlur = 28.dp,
        topHighlight = Color.White.copy(alpha = 0.35f),
    ),
    background = BackgroundKind.WALLPAPER,
    nav = NavStyle.FLOATING_PILL,
    defaultWallpaper = WallpaperId.ANIMATED_DRIFT,
)

private val blurTokens = FractalTokens(
    id = FractalThemeId.BLUR,
    isDark = true,
    colors = FractalColors(
        background = Color(0xFF09080D),
        surface = Color.Black.copy(alpha = 0.38f),
        surfaceHigh = Color.Black.copy(alpha = 0.5f),
        selected = Color.White.copy(alpha = 0.16f),
        accent = FractalPalette.Magenta,
        onAccent = Color.White,
        text = Color.White,
        textSecondary = Color.White.copy(alpha = 0.75f),
        border = Color.White.copy(alpha = 0.14f),
        focusRing = Color.White,
    ),
    shapes = rounded,
    surface = SurfaceStyle(
        kind = SurfaceKind.DARK_GLASS,
        fill = Color.Black.copy(alpha = 0.38f),
        borderColor = Color.White.copy(alpha = 0.14f),
        borderWidth = 1.dp,
        backdropBlur = 20.dp,
    ),
    background = BackgroundKind.GAME_ART,
    nav = NavStyle.TOP_BAR,
    defaultWallpaper = WallpaperId.STATIC_INK,
)

private val synthwaveTokens = FractalTokens(
    id = FractalThemeId.SYNTHWAVE,
    isDark = true,
    colors = FractalColors(
        background = FractalPalette.Night,
        surface = Color(0xFF15102E),
        surfaceHigh = Color(0xFF1D1640),
        selected = Color(0xFF2A1E55),
        accent = FractalPalette.Magenta,
        onAccent = Color.White,
        text = FractalPalette.Text,
        textSecondary = Color(0xFFC3B9E6),
        border = Color(0xFF3A2C70),
        focusRing = FractalPalette.Cyan,
        accentBrush = Brush.linearGradient(listOf(FractalPalette.Magenta, FractalPalette.Purple, FractalPalette.Cyan)),
        backgroundBrush = Brush.verticalGradient(listOf(FractalPalette.Night, Color(0xFF1A0B3A), Color(0xFF3A0B4A))),
    ),
    shapes = chamfered,
    surface = SurfaceStyle(
        kind = SurfaceKind.SOLID,
        fill = Color(0xFF15102E),
        borderColor = Color(0xFF3A2C70),
        borderWidth = 1.dp,
    ),
    background = BackgroundKind.WALLPAPER,
    nav = NavStyle.TOP_BAR,
    defaultWallpaper = WallpaperId.STATIC_SYNTHWAVE,
)

internal fun materialYouTokens(scheme: ColorScheme, isDark: Boolean) = FractalTokens(
    id = FractalThemeId.MATERIAL_YOU,
    isDark = isDark,
    colors = FractalColors(
        background = scheme.background,
        surface = scheme.surfaceContainer,
        surfaceHigh = scheme.surfaceContainerHigh,
        selected = scheme.secondaryContainer,
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        text = scheme.onSurface,
        textSecondary = scheme.onSurfaceVariant,
        border = scheme.outlineVariant,
        focusRing = scheme.primary,
    ),
    shapes = roundedMaterial,
    surface = SurfaceStyle(kind = SurfaceKind.TONAL, fill = scheme.surfaceContainer),
    background = BackgroundKind.WALLPAPER,
    nav = NavStyle.RAIL,
    defaultWallpaper = WallpaperId.NONE,
)

internal fun staticTokensFor(id: FractalThemeId): FractalTokens = when (id) {
    FractalThemeId.SOLID -> solidTokens
    FractalThemeId.LIQUID_GLASS -> liquidGlassTokens
    FractalThemeId.BLUR -> blurTokens
    FractalThemeId.SYNTHWAVE -> synthwaveTokens
    FractalThemeId.MATERIAL_YOU -> error("Material You tokens depend on the system color scheme")
}
