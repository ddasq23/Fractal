package dev.dfd.fractal.settings.graphics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dfd.fractal.common.ui.components.Header
import dev.dfd.fractal.common.ui.components.ScreenContent
import dev.dfd.fractal.common.ui.components.SingleSelection
import dev.dfd.fractal.common.ui.components.Slider
import dev.dfd.fractal.common.ui.components.Toggle
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeSettings
import java.util.Locale
import kotlin.math.roundToInt

private val ScalingFilterChoices = listOf(
    NativeSettings.ScalingFilter.BILINEAR_FILTER,
    NativeSettings.ScalingFilter.BICUBIC_FILTER,
    NativeSettings.ScalingFilter.BICUBIC_HERMITE_FILTER,
    NativeSettings.ScalingFilter.NEAREST_NEIGHBOR_FILTER
)

// FSR and GSR only make sense when the image gets bigger
private val UpscalingFilterChoices = ScalingFilterChoices + listOf(
    NativeSettings.ScalingFilter.FSR_FILTER,
    NativeSettings.ScalingFilter.SGSR_FILTER,
    NativeSettings.ScalingFilter.SGSR_EDGE_FILTER,
)

private val SharpenedUpscalingFilters = setOf(
    NativeSettings.ScalingFilter.FSR_FILTER,
    NativeSettings.ScalingFilter.SGSR_FILTER,
    NativeSettings.ScalingFilter.SGSR_EDGE_FILTER,
)

/** Gamma sliders work in tenths: 10..30 is 1.0..3.0. */
private const val GAMMA_SLIDER_MIN = 10
private const val GAMMA_SLIDER_MAX = 30

private fun gammaToSlider(gamma: Float) = (gamma * 10f).roundToInt().coerceIn(GAMMA_SLIDER_MIN, GAMMA_SLIDER_MAX)
private fun formatGamma(tenths: Int) = String.format(Locale.ROOT, "%.1f", tenths / 10f)

@Composable
fun GraphicsSettingsScreen(navigateBack: () -> Unit) {
    var upscalingFilter by remember { mutableIntStateOf(NativeSettings.getUpscalingFilter()) }
    var displayIsSrgb by remember { mutableStateOf(NativeSettings.getDisplayGamma() == NativeSettings.DISPLAY_GAMMA_SRGB) }

    ScreenContent(
        appBarText = tr("Graphics settings"),
        navigateBack = navigateBack,
    ) {
        Header(tr("Rendering"))
        Toggle(
            label = tr("Async shader compile"),
            description = tr("Enables async shader and pipeline compilation. Reduces stutter at the cost of objects not rendering for a short time.\nVulkan only"),
            initialCheckedState = NativeSettings::getAsyncShaderCompile,
            onCheckedChanged = NativeSettings::setAsyncShaderCompile,
        )
        Toggle(
            label = tr("Accurate barriers"),
            description = tr("Disabling the accurate barriers option will lead to flickering graphics but may improve performance. It is highly recommended to leave it turned on"),
            initialCheckedState = NativeSettings::getAccurateBarriers,
            onCheckedChanged = NativeSettings::setAccurateBarriers,
        )

        Header(tr("Presentation"))
        SingleSelection(
            label = tr("VSync"),
            initialChoice = NativeSettings::getVsyncMode,
            onChoiceChanged = NativeSettings::setVsyncMode,
            choiceToString = { vsyncModeToString(it) },
            choices = listOf(
                NativeSettings.VSyncMode.OFF,
                NativeSettings.VSyncMode.DOUBLE_BUFFERING,
                NativeSettings.VSyncMode.TRIPLE_BUFFERING
            ),
            description = tr("Android always syncs to the display. Triple buffering (mailbox) lets the game run without waiting for the screen; if the driver does not offer it, double buffering is used"),
        )
        SingleSelection(
            label = tr("Fullscreen scaling"),
            initialChoice = NativeSettings::getFullscreenScaling,
            onChoiceChanged = NativeSettings::setFullscreenScaling,
            choiceToString = { fullscreenScalingModeToString(it) },
            choices = listOf(
                NativeSettings.FullscreenScaling.KEEP_ASPECT_RATIO,
                NativeSettings.FullscreenScaling.STRETCH
            ),
        )

        Header(tr("Scaling filters"))
        SingleSelection(
            label = tr("Upscale filter"),
            initialChoice = { upscalingFilter },
            onChoiceChanged = {
                NativeSettings.setUpscalingFilter(it)
                upscalingFilter = it
            },
            choiceToString = { scalingFilterToString(it) },
            choices = UpscalingFilterChoices,
            description = tr("Used when the game image is smaller than the screen"),
        )
        if (upscalingFilter in SharpenedUpscalingFilters) {
            Slider(
                label = tr("FSR/GSR sharpness"),
                initialValue = NativeSettings::getUpscalingSharpness,
                valueFrom = 0,
                valueTo = 100,
                steps = 19,
                labelFormatter = { "$it%" },
                onValueChange = NativeSettings::setUpscalingSharpness,
            )
        }
        SingleSelection(
            label = tr("Downscale filter"),
            initialChoice = NativeSettings::getDownscalingFilter,
            onChoiceChanged = NativeSettings::setDownscalingFilter,
            choiceToString = { scalingFilterToString(it) },
            choices = ScalingFilterChoices,
            description = tr("Used when the game image is bigger than the screen"),
        )

        Header(tr("Gamma"))
        Slider(
            label = tr("Target gamma"),
            initialValue = { gammaToSlider(NativeSettings.getTargetGamma()) },
            valueFrom = GAMMA_SLIDER_MIN,
            valueTo = GAMMA_SLIDER_MAX,
            labelFormatter = ::formatGamma,
            onValueChange = { NativeSettings.setTargetGamma(it / 10f) },
        )
        Toggle(
            label = tr("Override game gamma"),
            description = tr("Ignores the gamma the game asks for and uses the target gamma alone"),
            initialCheckedState = NativeSettings::getOverrideGameGamma,
            onCheckedChanged = NativeSettings::setOverrideGameGamma,
        )
        Toggle(
            label = tr("sRGB display"),
            description = tr("Compensates for a screen that uses the piecewise sRGB curve instead of a pure gamma curve. More accurate dark scenes, but may cause banding"),
            checked = displayIsSrgb,
            onCheckedChanged = {
                displayIsSrgb = it
                NativeSettings.setDisplayGamma(if (it) NativeSettings.DISPLAY_GAMMA_SRGB else NativeSettings.DEFAULT_GAMMA)
            },
        )
        if (!displayIsSrgb) {
            Slider(
                label = tr("Display gamma"),
                initialValue = { gammaToSlider(NativeSettings.getDisplayGamma()) },
                valueFrom = GAMMA_SLIDER_MIN,
                valueTo = GAMMA_SLIDER_MAX,
                labelFormatter = ::formatGamma,
                onValueChange = { NativeSettings.setDisplayGamma(it / 10f) },
            )
        }
    }
}

private fun scalingFilterToString(scalingFilter: Int) = when (scalingFilter) {
    NativeSettings.ScalingFilter.BILINEAR_FILTER -> tr("Bilinear")
    NativeSettings.ScalingFilter.BICUBIC_FILTER -> tr("Bicubic")
    NativeSettings.ScalingFilter.BICUBIC_HERMITE_FILTER -> tr("Hermite")
    NativeSettings.ScalingFilter.NEAREST_NEIGHBOR_FILTER -> tr("Nearest neighbor")
    NativeSettings.ScalingFilter.FSR_FILTER -> tr("AMD FSR 1")
    NativeSettings.ScalingFilter.SGSR_FILTER -> tr("Snapdragon GSR")
    NativeSettings.ScalingFilter.SGSR_EDGE_FILTER -> tr("Snapdragon GSR EdgeDir")
    else -> throw IllegalArgumentException("Invalid scaling filter:  $scalingFilter")
}

private fun vsyncModeToString(vsyncMode: Int) = when (vsyncMode) {
    // Android has no immediate present mode: "off" falls back to the same FIFO queue as double buffering
    NativeSettings.VSyncMode.OFF -> tr("Off (same as double buffering on Android)")
    NativeSettings.VSyncMode.DOUBLE_BUFFERING -> tr("On, double buffering")
    NativeSettings.VSyncMode.TRIPLE_BUFFERING -> tr("On, triple buffering (mailbox)")
    else -> throw IllegalArgumentException("Invalid vsync mode: $vsyncMode")
}

private fun fullscreenScalingModeToString(fullscreenScaling: Int) = when (fullscreenScaling) {
    NativeSettings.FullscreenScaling.KEEP_ASPECT_RATIO -> tr("Keep aspect ratio")
    NativeSettings.FullscreenScaling.STRETCH -> tr("Stretch")
    else -> throw IllegalArgumentException("Invalid fullscreen scaling mode:  $fullscreenScaling")
}