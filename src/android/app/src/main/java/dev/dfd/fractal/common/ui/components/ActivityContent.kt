package dev.dfd.fractal.common.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.settings.GuiSettings
import dev.dfd.fractal.common.ui.theme.BackdropHost
import dev.dfd.fractal.common.ui.theme.BackgroundKind
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.rememberGuiSettings
import dev.dfd.fractal.common.ui.wallpaper.Wallpaper

/** Art of the game in focus, shown as the background by themes that use [BackgroundKind.GAME_ART]. */
@Stable
class FocusedGameArt {
    var image by mutableStateOf<ImageBitmap?>(null)
}

val LocalFocusedGameArt = staticCompositionLocalOf { FocusedGameArt() }

/** Lets screens preview appearance changes (welcome screen) before they are saved. */
val LocalGuiSettingsOverride = staticCompositionLocalOf<GuiSettings?> { null }

@Composable
fun ActivityContent(
    showBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    val settings by rememberGuiSettings()
    val gui = settings ?: run {
        // DataStore not read yet: keep the screen blank instead of flashing the default theme
        Box(Modifier.fillMaxSize().background(Color(0xFF100F16)))
        return
    }
    ThemedContent(gui, showBackground, content)
}

/** Applies [gui]'s theme and background to [content]. */
@Composable
fun ThemedContent(
    gui: GuiSettings,
    showBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    val focusedArt = remember { FocusedGameArt() }
    FractalTheme(themeId = gui.theme) {
        val tokens = FractalTheme.tokens
        CompositionLocalProvider(LocalFocusedGameArt provides focusedArt) {
            if (!showBackground) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    content()
                }
                return@CompositionLocalProvider
            }
            BackdropHost(
                background = {
                    when (tokens.background) {
                        BackgroundKind.GAME_ART -> GameArtBackground(focusedArt.image, tokens.colors.background)
                        BackgroundKind.WALLPAPER -> Wallpaper(
                            id = gui.wallpaper ?: tokens.defaultWallpaper,
                            customImagePath = gui.customWallpaperPath,
                            economy = gui.animationEconomy,
                            fallback = tokens.colors.background,
                        )
                    }
                    tokens.colors.backgroundBrush?.let {
                        if (gui.wallpaper == null && tokens.background == BackgroundKind.WALLPAPER)
                            Box(Modifier.fillMaxSize().background(it, alpha = 0.55f))
                    }
                },
            ) {
                content()
            }
        }
    }
}

@Composable
private fun GameArtBackground(art: ImageBitmap?, base: Color) {
    Box(Modifier.fillMaxSize().background(base)) {
        Crossfade(targetState = art, animationSpec = tween(450), label = "game art") { image ->
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(48.dp),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.88f),
                    0.45f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Black.copy(alpha = 0.25f),
                )
            )
        )
    }
}
